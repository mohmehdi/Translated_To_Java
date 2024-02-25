package okhttp3.internal.connection;

import java.io.IOException;
import java.net.Socket;
import okhttp3.Address;
import okhttp3.EventListener;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Route;
import okhttp3.internal.canReuseConnectionFor;
import okhttp3.internal.closeQuietly;
import okhttp3.internal.concurrent.TaskRunner;
import okhttp3.internal.http.ExchangeCodec;
import okhttp3.internal.http.RealInterceptorChain;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.StreamResetException;

public class ExchangeFinder {
  private TaskRunner taskRunner;
  private RealConnectionPool connectionPool;
  private Address address;
  private RealCall call;
  private EventListener eventListener;
  private RouteSelector.Selection routeSelection;
  private RouteSelector routeSelector;
  private int refusedStreamCount = 0;
  private int connectionShutdownCount = 0;
  private int otherFailureCount = 0;
  private Route nextRouteToTry;

  public ExchangeFinder(
    TaskRunner taskRunner,
    RealConnectionPool connectionPool,
    Address address,
    RealCall call,
    EventListener eventListener
  ) {
    this.taskRunner = taskRunner;
    this.connectionPool = connectionPool;
    this.address = address;
    this.call = call;
    this.eventListener = eventListener;
  }

  public ExchangeCodec find(OkHttpClient client, RealInterceptorChain chain) {
    try {
      RealConnection resultConnection = findHealthyConnection(
        chain.connectTimeoutMillis(),
        chain.readTimeoutMillis(),
        chain.writeTimeoutMillis(),
        client.pingIntervalMillis(),
        client.retryOnConnectionFailure(),
        chain.request().method() != "GET"
      );
      return resultConnection.newCodec(client, chain);
    } catch (RouteException e) {
      trackFailure(e.lastConnectException());
      throw e;
    } catch (IOException e) {
      trackFailure(e);
      throw new RouteException(e);
    }
  }

  private RealConnection findHealthyConnection(
    int connectTimeout,
    int readTimeout,
    int writeTimeout,
    int pingIntervalMillis,
    boolean connectionRetryEnabled,
    boolean doExtensiveHealthChecks
  ) throws IOException {
    while (true) {
      RealConnection candidate = findConnection(
        connectTimeout,
        readTimeout,
        writeTimeout,
        pingIntervalMillis,
        connectionRetryEnabled
      );

      if (candidate.isHealthy(doExtensiveHealthChecks)) {
        return candidate;
      }

      candidate.noNewExchanges();

      if (nextRouteToTry != null) continue;

      boolean routesLeft = routeSelection != null ? routeSelection.hasNext() : true;
      if (routesLeft) continue;

      boolean routesSelectionLeft = routeSelector != null ? routeSelector.hasNext() : true;
      if (routesSelectionLeft) continue;

      throw new IOException("exhausted all routes");
    }
  }

  public RealConnection findConnection(
    int connectTimeout,
    int readTimeout,
    int writeTimeout,
    int pingIntervalMillis,
    boolean connectionRetryEnabled
  ) throws IOException {
    if (call.isCanceled()) throw new IOException("Canceled");

    RealConnection callConnection = tryReuseCallConnection();
    if (callConnection != null) return callConnection;

    refusedStreamCount = 0;
    connectionShutdownCount = 0;
    otherFailureCount = 0;

    RealConnection pooled1 = findPooledConnection();
    if (pooled1 != null) return pooled1;

    Route newConnectionRoute;
    List<Route> routes = null;
    Pair<RealConnection, List<Route>> newConnectionAndRoutes = routeNewConnection();
    RealConnection newConnection = newConnectionAndRoutes.first;
    routes = newConnectionAndRoutes.second;

    RealConnection pooled2 = findPooledConnection(newConnection, routes);
    if (pooled2 != null) return pooled2;

    call.connectionToCancel = newConnection;
    try {
      newConnection.connect(
        connectTimeout,
        readTimeout,
        writeTimeout,
        pingIntervalMillis,
        connectionRetryEnabled,
        call,
        eventListener
      );
    } finally {
      call.connectionToCancel = null;
    }
    call.client.routeDatabase().connected(newConnection.route());

    RealConnection pooled3 = findPooledConnection(newConnection, routes);
    if (pooled3 != null) return pooled3;

    synchronized (newConnection) {
      connectionPool.put(newConnection);
      call.acquireConnectionNoEvents(newConnection);
    }

    eventListener.connectionAcquired(call, newConnection);
    return newConnection;
  }

  private RealConnection tryReuseCallConnection() {
    RealConnection candidate = call.connection();
    Socket toClose = null;
    synchronized (candidate) {
      if (candidate.noNewExchanges() || !sameHostAndPort(candidate.route().address().url())) {
        toClose = call.releaseConnectionNoEvents();
      }
    }

    if (call.connection() != null) {
      check(toClose == null);
      return candidate;
    }

    toClose.closeQuietly();
    eventListener.connectionReleased(call, candidate);

    return null;
  }

  private Pair<RealConnection, List<Route>> routeNewConnection() {
    List<Route> routes = null;

    Route localNextRouteToTry = nextRouteToTry;
    if (localNextRouteToTry != null) {
      nextRouteToTry = null;
      return new Pair<>(new RealConnection(taskRunner, connectionPool, localNextRouteToTry), routes);
    }

    RouteSelector.Selection existingRouteSelection = routeSelection;
    if (existingRouteSelection != null && existingRouteSelection.hasNext()) {
      return new Pair<>(new RealConnection(taskRunner, connectionPool, existingRouteSelection.next()), routes);
    }

    RouteSelector newRouteSelector = routeSelector;
    if (newRouteSelector == null) {
      newRouteSelector = new RouteSelector(address, call.client().routeDatabase(), call, eventListener);
      routeSelector = newRouteSelector;
    }

    RouteSelector.Selection newRouteSelection = newRouteSelector.next();
    routeSelection = newRouteSelection;

    if (call.isCanceled()) throw new IOException("Canceled");

    return new Pair<>(new RealConnection(taskRunner, connectionPool, newRouteSelection.next()), newRouteSelection.routes());
  }

  private RealConnection findPooledConnection(RealConnection connectionToReplace, List<Route> routes) {
    boolean requireMultiplexed = connectionToReplace != null && !connectionToReplace.isNew();
    if (!connectionPool.callAcquirePooledConnection(address, call, routes, requireMultiplexed)) {
      return null;
    }

    RealConnection result = call.connection();

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
    nextRouteToTry = null;
    if (e instanceof StreamResetException && ((StreamResetException) e).errorCode() == ErrorCode.REFUSED_STREAM) {
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

    if (routeSelection != null && routeSelection.hasNext()) return true;

    RouteSelector localRouteSelector = routeSelector;
    if (localRouteSelector == null) return true;

    return localRouteSelector.hasNext();
  }

  private Route retryRoute() {
    if (refusedStreamCount > 1 || connectionShutdownCount > 1 || otherFailureCount > 0) {
      return null;
    }

    RealConnection connection = call.connection();
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