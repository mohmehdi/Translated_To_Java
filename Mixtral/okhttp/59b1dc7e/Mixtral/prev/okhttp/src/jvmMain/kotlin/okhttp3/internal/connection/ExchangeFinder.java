
package okhttp3.internal.connection;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.Executor;
import okhttp3.Address;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Route;
import okhttp3.RouteException;
import okhttp3.internal.Internal;
import okhttp3.internal.RouteDatabase;
import okhttp3.internal.connection.RealConnectionPool.ConnectionAttempt;
import okhttp3.internal.connection.RouteSelector.Selection;
import okhttp3.internal.concurrent.TaskRunner;
import okhttp3.internal.http.ExchangeCodec;
import okhttp3.internal.http.RealInterceptorChain;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.StreamResetException;

public class ExchangeFinder {
  private final TaskRunner taskRunner;
  private final RealConnectionPool connectionPool;
  private final Address address;
  private final Call call;
  private final EventListener eventListener;
  private Selection routeSelection;
  private RouteSelector routeSelector;
  private int refusedStreamCount = 0;
  private int connectionShutdownCount = 0;
  private int otherFailureCount = 0;
  private Route nextRouteToTry;

  public ExchangeFinder(
      TaskRunner taskRunner, RealConnectionPool connectionPool, Address address, Call call,
      EventListener eventListener) {
    this.taskRunner = taskRunner;
    this.connectionPool = connectionPool;
    this.address = address;
    this.call = call;
    this.eventListener = eventListener;
  }

  public ExchangeCodec find(OkHttpClient client, RealInterceptorChain chain)
      throws IOException {
    try {
      RealConnection resultConnection = findHealthyConnection(
          chain.connectTimeoutMillis(), chain.readTimeoutMillis(),
          chain.writeTimeoutMillis(), client.pingIntervalMillis(),
          client.retryOnConnectionFailure(),
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

  private RealConnection findHealthyConnection(int connectTimeout, int readTimeout,
      int writeTimeout, int pingIntervalMillis, boolean connectionRetryEnabled,
      boolean doExtensiveHealthChecks)
      throws IOException {
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

  private RealConnection findConnection(int connectTimeout, int readTimeout, int writeTimeout,
      int pingIntervalMillis, boolean connectionRetryEnabled)
      throws IOException {
    if (call.isCanceled()) throw new IOException("Canceled");

    RealConnection callConnection = call.connection();
    if (callConnection != null) {
      Socket toClose = null;
      synchronized (callConnection) {
        if (callConnection.noNewExchanges()
            || !sameHostAndPort(callConnection.route().address().url())) {
          toClose = call.releaseConnectionNoEvents();
        }
      }

      if (call.connection() != null) {
        if (toClose != null) {
          throw new IllegalStateException("call.connection != null but toClose != null");
        }
        return callConnection;
      }

      toClose.closeQuietly();
      eventListener.connectionReleased(call, callConnection);
    }

    refusedStreamCount = 0;
    connectionShutdownCount = 0;
    otherFailureCount = 0;

    if (connectionPool.callAcquirePooledConnection(address, call, null, false)) {
      RealConnection result = call.connection();
      eventListener.connectionAcquired(call, result);
      return result;
    }

    List<Route> routes = null;
    Route route = null;
    if (nextRouteToTry != null) {
      routes = null;
      route = nextRouteToTry;
      nextRouteToTry = null;
    } else if (routeSelection != null && routeSelection.hasNext()) {
      routes = null;
      route = routeSelection.next();
    } else {
      RouteSelector localRouteSelector =
          routeSelector != null ? routeSelector : new RouteSelector(address, call.client().routeDatabase(), call, eventListener);
      Selection localRouteSelection = localRouteSelector.next();
      routeSelection = localRouteSelection;
      routes = localRouteSelection.routes();

      if (call.isCanceled()) throw new IOException("Canceled");

      if (connectionPool.callAcquirePooledConnection(address, call, routes, false)) {
        RealConnection result = call.connection();
        eventListener.connectionAcquired(call, result);
        return result;
      }

      route = localRouteSelection.next();
    }

    RealConnection newConnection =
        new RealConnection(taskRunner, connectionPool, route);
    call.connectionToCancel = newConnection;
    try {
      newConnection.connect(connectTimeout, readTimeout, writeTimeout, pingIntervalMillis,
          connectionRetryEnabled, call, eventListener);
    } finally {
      call.connectionToCancel = null;
    }
    call.client().routeDatabase().connected(newConnection.route());

    if (connectionPool.callAcquirePooledConnection(address, call, routes, true)) {
      RealConnection result = call.connection();
      nextRouteToTry = route;
      newConnection.socket().closeQuietly();
      eventListener.connectionAcquired(call, result);
      return result;
    }

    synchronized (newConnection) {
      connectionPool.put(newConnection);
      call.acquireConnectionNoEvents(newConnection);
    }

    eventListener.connectionAcquired(call, newConnection);
    return newConnection;
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

    if (routeSelection != null && routeSelection.hasNext()) {
      return true;
    }

    if (routeSelector != null) {
      return routeSelector.hasNext();
    }

    return true;
  }

  private Route retryRoute() {
    if (refusedStreamCount > 1 || connectionShutdownCount > 1 || otherFailureCount > 0) {
      return null;
    }

    RealConnection connection = call.connection();
    if (connection == null) {
      return null;
    }

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