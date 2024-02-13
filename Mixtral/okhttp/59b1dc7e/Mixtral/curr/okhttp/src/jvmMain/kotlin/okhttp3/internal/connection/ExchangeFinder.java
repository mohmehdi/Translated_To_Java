
package okhttp3.internal.connection;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Address;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.Internal;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Route;
import okhttp3.RouteException;
import okhttp3.internal.RealCall;
import okhttp3.internal.RealConnection;
import okhttp3.internal.RouteSelector;
import okhttp3.internal.RouteSelector.Selection;
import okhttp3.internal.concurrent.TaskRunner;
import okhttp3.internal.http.ExchangeCodec;
import okhttp3.internal.http.RealInterceptorChain;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.StreamResetException;
import okhttp3.internal.platform.Platform;
import okhttp3.internal.route.RouteDatabase;
import okhttp3.internal.util.CanceledException;
import okhttp3.internal.util.Deadline;
import okhttp3.internal.util.ExtensibleList;
import okhttp3.internal.util.HostAddress;
import okhttp3.internal.util.LeakDetector;
import okhttp3.internal.util.PlatformDebug;
import okhttp3.internal.util.Predicate;

public class ExchangeFinder {
  private final TaskRunner taskRunner;
  private final RealConnectionPool connectionPool;
  private final Address address;
  private final RealCall call;
  private final EventListener eventListener;
  private Selection routeSelection;
  private RouteSelector routeSelector;
  private int refusedStreamCount = 0;
  private int connectionShutdownCount = 0;
  private int otherFailureCount = 0;
  private Route nextRouteToTry;

  public ExchangeFinder(
      TaskRunner taskRunner, RealConnectionPool connectionPool, Address address, RealCall call,
      EventListener eventListener) {
    this.taskRunner = taskRunner;
    this.connectionPool = connectionPool;
    this.address = address;
    this.call = call;
    this.eventListener = eventListener;
  }

  public ExchangeCodec find(OkHttpClient client, RealInterceptorChain chain) throws IOException {
    try {
      RealConnection resultConnection = findHealthyConnection(
          chain.connectTimeoutMillis(), chain.readTimeoutMillis(), chain.writeTimeoutMillis(),
          client.pingIntervalMillis(), client.retryOnConnectionFailure(),
          chain.request().method() != "GET");
      return resultConnection.newCodec(client, chain);
    } catch (RouteException e) {
      trackFailure(e.lastConnectException());
      throw e;
    } catch (IOException e) {
      trackFailure(e);
      throw new RouteException(e);
    }
  }

  @Internal
  private RealConnection findHealthyConnection(int connectTimeout, int readTimeout,
      int writeTimeout, int pingIntervalMillis, boolean connectionRetryEnabled,
      boolean doExtensiveHealthChecks) throws IOException {
    while (true) {
      RealConnection candidate = findConnection(connectTimeout, readTimeout, writeTimeout,
          pingIntervalMillis, connectionRetryEnabled);

      if (candidate.isHealthy(doExtensiveHealthChecks)) {
        return candidate;
      }

      candidate.noNewExchanges();

      if (nextRouteToTry != null) continue;

      boolean routesLeft = routeSelection != null && routeSelection.hasNext();
      if (routesLeft) continue;

      boolean routesSelectionLeft = routeSelector != null && routeSelector.hasNext();
      if (routesSelectionLeft) continue;

      throw new IOException("exhausted all routes");
    }
  }

  @Internal
  private RealConnection findConnection(int connectTimeout, int readTimeout, int writeTimeout,
      int pingIntervalMillis, boolean connectionRetryEnabled) throws IOException {
    if (call.isCanceled()) throw new CanceledException();

    RealConnection callConnection = tryReuseCallConnection();
    if (callConnection != null) return callConnection;

    refusedStreamCount = 0;
    connectionShutdownCount = 0;
    otherFailureCount = 0;

    RealConnection pooled1 = findPooledConnection();
    if (pooled1 != null) return pooled1;

    ExtensibleList.Builder<Route> routesBuilder = new ExtensibleList.Builder<>();
    RealConnection newConnection = routeNewConnection(routesBuilder);

    RealConnection pooled2 = findPooledConnection(newConnection, routesBuilder.build());
    if (pooled2 != null) return pooled2;

    call.connectionToCancel = newConnection;
    try {
      newConnection.connect(connectTimeout, readTimeout, writeTimeout, pingIntervalMillis,
          connectionRetryEnabled, call, eventListener);
    } finally {
      call.connectionToCancel = null;
    }
    call.client.routeDatabase().connected(newConnection.route());

    pooled2 = findPooledConnection(newConnection, routesBuilder.build());
    if (pooled2 != null) return pooled2;

    synchronized (newConnection) {
      connectionPool.put(newConnection);
      call.acquireConnectionNoEvents(newConnection);
    }

    eventListener.connectionAcquired(call, newConnection);
    return newConnection;
  }

  private RealConnection tryReuseCallConnection() {
    RealConnection candidate = call.connection;
    if (candidate == null) return null;

    Socket toClose = null;
    synchronized (candidate) {
      if (candidate.noNewExchanges()
          || !HostAddress.sameHostAndPort(candidate.route().address().url(), address.url())) {
        toClose = call.releaseConnectionNoEvents();
      }
    }

    if (call.connection != null) {
      if (toClose != null) {
        toClose.closeQuietly();
      }
      return candidate;
    }

    if (toClose != null) {
      toClose.closeQuietly();
    }
    eventListener.connectionReleased(call, candidate);

    return null;
  }

   private Pair<RealConnection, List<Route>> routeNewConnection() throws IOException {
    List<Route> routes = null;

    Route localNextRouteToTry = nextRouteToTry();
    if (localNextRouteToTry != null) {
      nextRouteToTry = null;
      return new Pair<>(new RealConnection(taskRunner, connectionPool, localNextRouteToTry), routes);
    }

    RouteSelection existingRouteSelection = this.routeSelection;
    if (existingRouteSelection != null && existingRouteSelection.hasNext()) {
      return new Pair<>(new RealConnection(taskRunner, connectionPool, existingRouteSelection.next()), routes);
    }

    RouteSelector newRouteSelector = routeSelector != null ? routeSelector : new RouteSelector(address, call.client.routeDatabase, call, eventListener);
    if (newRouteSelector != routeSelector) {
      routeSelector = newRouteSelector;
    }

    RouteSelection newRouteSelection = newRouteSelector.next();
    routeSelection = newRouteSelection;

    if (call.isCanceled()) {
      throw new IOException("Canceled");
    }

    return new Pair<>(new RealConnection(taskRunner, connectionPool, newRouteSelection.next()), newRouteSelection.routes);
  }

  private RealConnection findPooledConnection(RealConnection connectionToReplace,
      List<Route> routes) {
    boolean requireMultiplexed = connectionToReplace != null && !connectionToReplace.isNew();
    if (!connectionPool.callAcquirePooledConnection(address, call, routes, requireMultiplexed)) {
      return null;
    }

    RealConnection result = call.connection;

    if (connectionToReplace != null) {
      nextRouteToTry = connectionToReplace.route();
      if (!connectionToReplace.isNew()) {
        connectionToReplace.socket().closeQuietly();
      }
    }

    eventListener.connectionAcquired(call, result);
    return result;
  }

  public void trackFailure(IOException e) {
    this.nextRouteToTry = null;
    if (e instanceof StreamResetException && e.errorCode() == ErrorCode.REFUSED_STREAM) {
      refusedStreamCount++;
    } else if (e instanceof ConnectionShutdownException) {
      connectionShutdownCount++;
    } else {
      otherFailureCount++;
    }
  }

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
    return localRouteSelector != null && localRouteSelector.hasNext();
  }

  private Route retryRoute() {
    if (refusedStreamCount > 1 || connectionShutdownCount > 1 || otherFailureCount > 0) {
      return null;
    }

    RealConnection connection = call.connection;
    if (connection == null) return null;

    synchronized (connection) {
      if (connection.routeFailureCount() != 0) return null;
      if (!connection.route().address().url().canReuseConnectionFor(address.url())) return null;
      return connection.route();
    }
  }

  public boolean sameHostAndPort(HttpUrl url) {
    HttpUrl routeUrl = address.url();
    return url.port() == routeUrl.port() && url.host().equals(routeUrl.host());
  }
}