package okhttp3.internal.connection;

import java.io.IOException;
import java.net.Socket;
import okhttp3.Address;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Route;
import okhttp3.internal.canReuseConnectionFor;
import okhttp3.internal.closeQuietly;
import okhttp3.internal.connection.RoutePlanner.Plan;
import okhttp3.internal.http.RealInterceptorChain;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.StreamResetException;

internal class RealRoutePlanner implements RoutePlanner {
  private OkHttpClient client;
  private Address address;
  private RealCall call;
  private RealInterceptorChain chain;
  private ConnectionPool connectionPool;
  private EventListener eventListener;
  private boolean doExtensiveHealthChecks;
  private RouteSelector.Selection routeSelection;
  private RouteSelector routeSelector;
  private int refusedStreamCount;
  private int connectionShutdownCount;
  private int otherFailureCount;
  private Route nextRouteToTry;

  public RealRoutePlanner(OkHttpClient client, Address address, RealCall call, RealInterceptorChain chain) {
    this.client = client;
    this.address = address;
    this.call = call;
    this.chain = chain;
    this.connectionPool = client.connectionPool.delegate;
    this.eventListener = call.eventListener;
    this.doExtensiveHealthChecks = !chain.request.method.equals("GET");
  }

  public boolean isCanceled() {
    return call.isCanceled();
  }

  public Plan plan() throws IOException {
    ReusePlan reuseCallConnection = planReuseCallConnection();
    if (reuseCallConnection != null) return reuseCallConnection;

    refusedStreamCount = 0;
    connectionShutdownCount = 0;
    otherFailureCount = 0;

    ReusePlan pooled1 = planReusePooledConnection();
    if (pooled1 != null) return pooled1;

    RealConnectPlan connect = planConnect();

    ReusePlan pooled2 = planReusePooledConnection(connect.connection, connect.routes);
    if (pooled2 != null) return pooled2;

    return connect;
  }

  private ReusePlan planReuseCallConnection() {
    RealConnection candidate = call.connection;
    if (candidate == null) return null;

    boolean healthy = candidate.isHealthy(doExtensiveHealthChecks);
    Socket toClose = null;
    synchronized (candidate) {
      if (!healthy) {
        candidate.noNewExchanges = true;
        call.releaseConnectionNoEvents();
      } else if (candidate.noNewExchanges || !sameHostAndPort(candidate.route().address.url)) {
        call.releaseConnectionNoEvents();
      } else {
        return new ReusePlan(candidate);
      }
    }

    if (call.connection != null) {
      check(toClose == null);
      return new ReusePlan(candidate);
    }

    closeQuietly(toClose);
    eventListener.connectionReleased(call, candidate);
    return null;
  }

  private RealConnectPlan planConnect() throws IOException {
    Route localNextRouteToTry = nextRouteToTry;
    if (localNextRouteToTry != null) {
      nextRouteToTry = null;
      return new RealConnectPlan(localNextRouteToTry);
    }

    RouteSelector.Selection existingRouteSelection = routeSelection;
    if (existingRouteSelection != null && existingRouteSelection.hasNext()) {
      return new RealConnectPlan(existingRouteSelection.next());
    }

    RouteSelector newRouteSelector = routeSelector;
    if (newRouteSelector == null) {
      newRouteSelector = new RouteSelector(
        address,
        call.client.routeDatabase,
        call,
        client.fastFallback,
        eventListener
      );
      routeSelector = newRouteSelector;
    }

    if (!newRouteSelector.hasNext()) throw new IOException("exhausted all routes");
    RouteSelector.Selection newRouteSelection = newRouteSelector.next();
    routeSelection = newRouteSelection;

    if (call.isCanceled()) throw new IOException("Canceled");

    return new RealConnectPlan(newRouteSelection.next(), newRouteSelection.routes);
  }

  private ReusePlan planReusePooledConnection(RealConnection connectionToReplace, List<Route> routes) {
    RealConnection result = connectionPool.callAcquirePooledConnection(
      doExtensiveHealthChecks,
      address,
      call,
      routes,
      connectionToReplace != null && !connectionToReplace.isNew
    );
    if (result == null) return null;

    if (connectionToReplace != null) {
      nextRouteToTry = connectionToReplace.route();
      if (!connectionToReplace.isNew) {
        connectionToReplace.socket().closeQuietly();
      }
    }

    eventListener.connectionAcquired(call, result);
    return new ReusePlan(result);
  }

  internal class ReusePlan implements Plan {
    private RealConnection connection;
    boolean isConnected = true;

    public ReusePlan(RealConnection connection) {
      this.connection = connection;
    }

    public void connect() {
      throw new IllegalStateException("already connected");
    }

    public RealConnection handleSuccess() {
      return connection;
    }

    public void cancel() {
      throw new IllegalStateException("unexpected cancel of reused connection");
    }
  }

  internal class RealConnectPlan implements Plan {
    private Route route;
    private List<Route> routes;

    public RealConnectPlan(Route route, List<Route> routes) {
      this.route = route;
      this.routes = routes;
    }

    public boolean isConnected() {
      return !connection.isNew;
    }

    public void connect() throws IOException {
      call.connectionsToCancel.add(connection);
      try {
        connection.connect(
          chain.connectTimeoutMillis,
          chain.readTimeoutMillis,
          chain.writeTimeoutMillis,
          client.pingIntervalMillis,
          client.retryOnConnectionFailure,
          call,
          eventListener
        );
      } finally {
        call.connectionsToCancel.remove(connection);
      }
    }

    public RealConnection handleSuccess() {
      call.client.routeDatabase.connected(connection.route());

      ReusePlan pooled3 = planReusePooledConnection(connection, routes);
      if (pooled3 != null) return pooled3.connection;

      synchronized (connection) {
        connectionPool.put(connection);
        call.acquireConnectionNoEvents(connection);
      }

      eventListener.connectionAcquired(call, connection);
      return connection;
    }

    public void cancel() {
      connection.cancel();
    }
  }

  public void trackFailure(IOException e) {
    if (e instanceof StreamResetException && ((StreamResetException) e).errorCode == ErrorCode.REFUSED_STREAM) {
      refusedStreamCount++;
    } else if (e instanceof ConnectionShutdownException) {
      connectionShutdownCount++;
    } else {
      otherFailureCount++;
    }
  }

  public boolean hasFailure() {
    return refusedStreamCount > 0 || connectionShutdownCount > 0 || otherFailureCount > 0;
  }

  public boolean hasMoreRoutes() {
    if (nextRouteToTry != null) {
      return true;
    }

    Route retryRoute = retryRoute();
    if (retryRoute != null) {
      nextRouteToTry = retryRoute;
      return true;
    }

    if (routeSelection != null && routeSelection.hasNext()) return true;

    RouteSelector localRouteSelector = routeSelector;
    if (localRouteSelector != null) return localRouteSelector.hasNext();

    return true;
  }

  private Route retryRoute() {
    if (refusedStreamCount > 1 || connectionShutdownCount > 1 || otherFailureCount > 0) {
      return null;
    }

    RealConnection connection = call.connection;
    if (connection == null) return null;

    synchronized (connection) {
      if (connection.routeFailureCount != 0) return null;
      if (!connection.noNewExchanges) return null;
      if (!connection.route().address.url.canReuseConnectionFor(address.url)) return null;
      return connection.route();
    }
  }

  public boolean sameHostAndPort(HttpUrl url) {
    HttpUrl routeUrl = address.url;
    return url.port == routeUrl.port && url.host.equals(routeUrl.host);
  }
}