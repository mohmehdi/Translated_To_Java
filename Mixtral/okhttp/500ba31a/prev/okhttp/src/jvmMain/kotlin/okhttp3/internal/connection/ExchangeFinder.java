package okhttp3.internal.connection;

import java.io.IOException;
import java.net.Socket;
import java.util.List;

import okhttp3.Address;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Route;
import okhttp3.internal.canReuseConnectionFor;
import okhttp3.internal.closeQuietly;
import okhttp3.internal.http.ExchangeCodec;
import okhttp3.internal.http.RealInterceptorChain;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.StreamResetException;
import okhttp3.Request;

public class ExchangeFinder {
  private final OkHttpClient client;
  private final Address address;
  private final RealCall call;
  private final RealInterceptorChain chain;
  private RouteSelector.Selection routeSelection;
  private RouteSelector routeSelector;
  private int refusedStreamCount = 0;
  private int connectionShutdownCount = 0;
  private int otherFailureCount = 0;
  private Route nextRouteToTry;

  private final ConnectionPool connectionPool;
  private final Callback eventListener;
  private final boolean doExtensiveHealthChecks;

  public ExchangeFinder(OkHttpClient client, Address address, RealCall call, RealInterceptorChain chain) {
    this.client = client;
    this.address = address;
    this.call = call;
    this.chain = chain;
    this.connectionPool = client.connectionPool().getDelegate();
    this.eventListener = call.eventListener();
    this.doExtensiveHealthChecks = chain.request().method().equals("GET");
  }

  public ExchangeCodec find() throws IOException {
    IOException firstException = null;
    while (true) {
      if (call.isCanceled()) throw new IOException("Canceled");

      try {
        Plan plan = plan();
        if (plan instanceof ReusePlan) {
          ReusePlan reusePlan = (ReusePlan) plan;
          return reusePlan.connection.newCodec(client, chain);
        } else if (plan instanceof ConnectPlan) {
          ConnectPlan connectPlan = (ConnectPlan) plan;
          connectPlan.connect();

          IOException failure = connectPlan.failure;
          if (failure != null) throw failure;

          return connectPlan.handleSuccess();
        }
      } catch (IOException e) {
        trackFailure(e);

        if (firstException == null) {
          firstException = e;
        } else {
          e.addSuppressed(firstException);
        }
        if (!retryAfterFailure()) {
          throw firstException;
        }
      }
    }
  }

  private Plan plan() {
    Plan reuseCallConnection = planReuseCallConnection();
    if (reuseCallConnection != null) return reuseCallConnection;

    refusedStreamCount = 0;
    connectionShutdownCount = 0;
    otherFailureCount = 0;

    Plan pooled1 = planReusePooledConnection();
    if (pooled1 != null) return pooled1;

    Plan connect = planConnect();

    Plan pooled2 = planReusePooledConnection(connect.connection, connect.routes);
    if (pooled2 != null) return pooled2;

    return connect;
  }

  private Plan planReuseCallConnection() {
    ConnectionCandidate candidate = call.connection();
    if (candidate == null) return null;

    boolean healthy = candidate.isHealthy(doExtensiveHealthChecks);
    Socket toClose = null;
    synchronized(candidate) {
      if (healthy) {
        if (candidate.noNewExchanges() || !sameHostAndPort(candidate.route().address().url())) {
          call.releaseConnectionNoEvents();
        }
      } else {
        candidate.noNewExchanges = true;
        call.releaseConnectionNoEvents();
      }
      toClose = candidate.socket();
    }

    if (call.connection != null) {
      assert(toClose == null);
      return new ReusePlan(candidate);
    }

    closeQuietly(toClose);
    eventListener.connectionReleased(call, candidate);
    return null;
  }

  private Plan planConnect() throws IOException {
    Route localNextRouteToTry = nextRouteToTry;
    if (localNextRouteToTry != null) {
      nextRouteToTry = null;
      return new ConnectPlan(localNextRouteToTry);
    }

    RouteSelector.Selection existingRouteSelection = routeSelection;
    if (existingRouteSelection != null && existingRouteSelection.hasNext()) {
      return new ConnectPlan(existingRouteSelection.next());
    }

    RouteSelector newRouteSelector = routeSelector;
    if (newRouteSelector == null) {
      newRouteSelector = new RouteSelector(address, call.client().routeDatabase(), call, eventListener);
      routeSelector = newRouteSelector;
    }

    if (!newRouteSelector.hasNext()) throw new IOException("exhausted all routes");
    RouteSelection newRouteSelection = newRouteSelector.next();
    routeSelection = newRouteSelection;

    if (call.isCanceled()) throw new IOException("Canceled");

    return new ConnectPlan(newRouteSelection.next(), newRouteSelection.routes);
  }

  private Plan planReusePooledConnection(ConnectionCandidate connectionToReplace, List routes) {
    ConnectionPool.CallAcquireResult result = connectionPool.callAcquirePooledConnection(
      doExtensiveHealthChecks,
      address,
      call,
      routes,
      connectionToReplace != null && !connectionToReplace.isNew()
    );
    if (result == null) return null;

    if (connectionToReplace != null) {
      nextRouteToTry = connectionToReplace.route();
      if (!connectionToReplace.isNew()) {
        connectionToReplace.socket().closeQuietly();
      }
    }

    eventListener.connectionAcquired(call, result.connection);
    return new ReusePlan(result.connection);
  }

public abstract class Plan {
    // You can add methods and properties here if needed
}
  public static class ReusePlan extends Plan {
    private final ConnectionCandidate connection;

    public ReusePlan(ConnectionCandidate connection) {
      super();
      this.connection = connection;
    }
  }

  public static class ConnectPlan extends Plan {
    private final Route route;
    private final List routes;
    private RealConnection connection;
    private boolean complete;
    private IOException failure;

    public ConnectPlan(Route route) {
      this(route, null);
    }

    public ConnectPlan(Route route, List routes) {
      this.route = route;
      this.routes = routes;
    }

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
          eventListener
        );
      } catch (IOException e) {
        failure = e;
      } finally {
        call.connectionsToCancel.remove(connection);
        complete = true;
      }
    }

    public RealConnection handleSuccess() throws IOException {
      client.routeDatabase().connected(connection.route());

      ConnectionPool.CallAcquireResult result = planReusePooledConnection(connection, routes);
      if (result != null) return result.connection;

      synchronized(connection) {
        connectionPool.put(connection);
        call.acquireConnectionNoEvents(connection);
      }

      eventListener.connectionAcquired(call, connection);
      return connection;
    }
  }

  private void trackFailure(IOException e) {
    if (e instanceof StreamResetException && e.errorCode() == ErrorCode.REFUSED_STREAM) {
      refusedStreamCount++;
    } else if (e instanceof ConnectionShutdownException) {
      connectionShutdownCount++;
    } else {
      otherFailureCount++;
    }
  }

  private boolean retryAfterFailure() {
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

    if (routeSelection != null && routeSelection.hasNext()) return true;

    RouteSelector localRouteSelector = routeSelector;
    return localRouteSelector != null && localRouteSelector.hasNext();
  }

  private Route retryRoute() {
    if (refusedStreamCount > 1 || connectionShutdownCount > 1 || otherFailureCount > 0) {
      return null;
    }

    ConnectionCandidate connection = call.connection();
    if (connection == null) return null;

    synchronized(connection) {
      if (connection.routeFailureCount() != 0) return null;
      if (!connection.noNewExchanges()) return null; // This route is still in use.
      if (!connection.route().address().url().canReuseConnectionFor(address.url())) return null;
      return connection.route();
    }
  }

  private boolean sameHostAndPort(HttpUrl url) {
    HttpUrl routeUrl = address.url();
    return url.port() == routeUrl.port() && url.host().equals(routeUrl.host());
  }


}