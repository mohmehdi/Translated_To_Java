

package okhttp3.internal.connection;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.Address;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Route;
import okhttp3.internal.CanReuseConnectionFor;
import okhttp3.internal.CloseQuietly;
import okhttp3.internal.connection.RoutePlanner.Plan;
import okhttp3.internal.http.RealInterceptorChain;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.StreamResetException;

public class RealRoutePlanner implements RoutePlanner {
  private final OkHttpClient client;
  private final Address address;
  private final RealCall call;
  private final RealInterceptorChain chain;
  private final ConnectionPool connectionPool;
  private final EventListener eventListener;
  private final boolean doExtensiveHealthChecks;

  private RouteSelector.Selection routeSelection;
  private RouteSelector routeSelector;
  private int refusedStreamCount = 0;
  private int connectionShutdownCount = 0;
  private int otherFailureCount = 0;
  private Route nextRouteToTry;

  public RealRoutePlanner(OkHttpClient client, Address address, RealCall call, RealInterceptorChain chain) {
    this.client = client;
    this.address = address;
    this.call = call;
    this.chain = chain;
    this.connectionPool = client.connectionPool().delegate();
    this.eventListener = call.eventListener();
    this.doExtensiveHealthChecks = chain.request().method().equals("GET");
  }

  private RealConnectPlan planConnect() throws IOException {
        RealRoute localNextRouteToTry = nextRouteToTry;
        if (localNextRouteToTry != null) {
            nextRouteToTry = null;
            return new RealConnectPlan(localNextRouteToTry);
        }

        RouteSelection existingRouteSelection = routeSelection;
        if (existingRouteSelection != null && existingRouteSelection.hasNext()) {
            return new RealConnectPlan(existingRouteSelection.next());
        }

        RouteSelector newRouteSelector = routeSelector;
        if (newRouteSelector == null) {
            newRouteSelector = new RouteSelector(address, call.client.routeDatabase, call, eventListener);
            routeSelector = newRouteSelector;
        }

        if (!newRouteSelector.hasNext()) {
            throw new IOException("exhausted all routes");
        }

        RouteSelection newRouteSelection = newRouteSelector.next();
        routeSelection = newRouteSelection;

        if (call.isCanceled()) {
            throw new IOException("Canceled");
        }

        RealRoute nextRoute = newRouteSelection.next();
        List<RealRoute> routes = newRouteSelection.routes;
        return new RealConnectPlan(nextRoute, routes);
    }

  @Override
  public boolean isCanceled() {
    return call.isCanceled();
  }

  @Override
  public Plan plan() throws IOException {
    ReusePlan reuseCallConnection = planReuseCallConnection();
    if (reuseCallConnection != null) return reuseCallConnection;

    refusedStreamCount = 0;
    connectionShutdownCount = 0;
    otherFailureCount = 0;

    Plan pooled1 = planReusePooledConnection();
    if (pooled1 != null) return pooled1;

    Plan connect = planConnect();

    Plan pooled2 = planReusePooledConnection(connect.connection(), connect.routes());
    if (pooled2 != null) return pooled2;

    return connect;
  }

  private ReusePlan planReuseCallConnection() {
    Connection candidate = call.connection();
    if (candidate == null) return null;

    boolean healthy = candidate.isHealthy(doExtensiveHealthChecks);
    Closeable toClose = null;
    synchronized (candidate) {
      if (!healthy) {
        candidate.noNewExchanges = true;
        call.releaseConnectionNoEvents();
      } else if (candidate.noNewExchanges() || !sameHostAndPort(candidate.route().address().url())) {
        call.releaseConnectionNoEvents();
      }
      if (!healthy || candidate.noNewExchanges() || !sameHostAndPort(candidate.route().address().url())) {
        toClose = candidate;
      }
    }

    if (call.connection() != null) {
      if (toClose != null) throw new IllegalStateException("candidate should be null");
      return new ReusePlan(candidate);
    }

    CloseQuietly.closeQuietly(toClose);
    eventListener.connectionReleased(call, candidate);
    return null;
  }



private ReusePlan planReusePooledConnection(RealConnection connectionToReplace, List<Route> routes) {
    RealConnection result = connectionPool.callAcquirePooledConnection(
            doExtensiveHealthChecks,
            address,
            call,
            routes,
            connectionToReplace != null && !connectionToReplace.isNew()
    );

    if (result == null) {
        return null;
    }

    if (connectionToReplace != null) {
        nextRouteToTry = connectionToReplace.route();
        if (!connectionToReplace.isNew()) {
            ConnectionSocketToClose socket = connectionToReplace.socket();
            if (socket != null) {
                socket.closeQuietly();
            }
        }
    }

    eventListener.connectionAcquired(call, result);
    return new ReusePlan(result);
}

  private static class ReusePlan implements Plan {
    private final RealConnection connection;

    ReusePlan(RealConnection connection) {
      this.connection = connection;
    }

 

    @Override
    public void connect() {
      throw new IllegalStateException("already connected");
    }

    @Override
    public RealConnection handleSuccess() {
      return connection;
    }
  }

  private class RealConnectPlan implements Plan {
    private final Route route;
    private final List<Route> routes;
    private final RealConnection connection;

    RealConnectPlan(Route route) {
      this(route, null);
    }

    RealConnectPlan(Route route, List<Route> routes) {
      this.route = route;
      this.routes = routes;
      this.connection = new RealConnection(client.taskRunner(), connectionPool, route);
    }



    @Override
    public void connect() throws IOException {
      call.connectionsToCancel.add(connection);
      try {
        connection.connect(
            chain.connectTimeoutMillis(),
            chain.readTimeoutMillis(),
            chain.writeTimeoutMillis(),
            client.pingIntervalMillis(),
            client.retryOnConnectionFailure(),
            call,
            eventListener);
      } finally {
        call.connectionsToCancel.remove(connection);
      }
    }

    @Override
    public RealConnection handleSuccess() {
      client.routeDatabase().connected(connection.route());

      Plan pooled3 = planReusePooledConnection(connection, routes);
      if (pooled3 != null) return pooled3.connection();

      synchronized (connection) {
        connectionPool.put(connection);
        call.acquireConnectionNoEvents(connection);
      }

      eventListener.connectionAcquired(call, connection);
      return connection;
    }
  }

  @Override
  public void trackFailure(IOException e) {
    if (e instanceof StreamResetException
        && ((StreamResetException) e).errorCode() == ErrorCode.REFUSED_STREAM) {
      refusedStreamCount++;
    } else if (e instanceof ConnectionShutdownException) {
      connectionShutdownCount++;
    } else {
      otherFailureCount++;
    }
  }

  @Override
  public boolean retryAfterFailure() {
    if (refusedStreamCount == 0 && connectionShutdownCount == 0 && otherFailureCount == 0) {
      return false;
    }

    if (nextRouteToTry != null) {
      return true;
    }

    Route retryRoute = retryRoute();
    if (retryRoute != null) {
      nextRouteToTry = retryRoute;
      return true;
    }

    if (routeSelection != null && routeSelection.hasNext()) {
      return true;
    }

    RouteSelector localRouteSelector = routeSelector;
    if (localRouteSelector != null) {
      return localRouteSelector.hasNext();
    }

    return true;
  }

  private Route retryRoute() {
    if (refusedStreamCount > 1 || connectionShutdownCount > 1 || otherFailureCount > 0) {
      return null;
    }

    Connection connection = call.connection();
    if (connection == null) {
      return null;
    }

    synchronized (connection) {
      if (connection.routeFailureCount() != 0) return null;
      if (!connection.noNewExchanges()) return null;
      if (!connection.route().address().url().canReuseConnectionFor(address.url())) return null;
      return connection.route();
    }
  }

  @Override
  public boolean sameHostAndPort(HttpUrl url) {
    HttpUrl routeUrl = address.url();
    return url.port() == routeUrl.port() && url.host().equals(routeUrl.host());
  }
}