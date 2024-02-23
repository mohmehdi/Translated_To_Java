

package okhttp3.internal.connection;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.UnknownServiceException;
import java.util.List;

import okhttp3.Address;
import okhttp3.ConnectionSpec;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.internal.EMPTY_RESPONSE;
import okhttp3.internal.CanReuseConnection;
import okhttp3.internal.CloseQuietly;
import okhttp3.internal.connection.RoutePlanner.ConnectResult;
import okhttp3.internal.connection.RoutePlanner.Plan;
import okhttp3.internal.http.RealInterceptorChain;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.StreamResetException;
import okhttp3.internal.platform.Platform;
import okhttp3.internal.toHostHeader;
import okhttp3.internal.userAgent;

class RealRoutePlanner implements RoutePlanner {
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

  RealRoutePlanner(OkHttpClient client, Address address, RealCall call, RealInterceptorChain chain) {
    this.client = client;
    this.address = address;
    this.call = call;
    this.chain = chain;
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

    ConnectPlan connect = planConnect();

    Plan pooled2 = planReusePooledConnection(connect, connect.routes);
    if (pooled2 != null) return pooled2;

    return connect;
  }

  private ReusePlan planReuseCallConnection() {
    Connection candidate = call.connection;
    if (candidate == null) return null;

    boolean healthy = candidate.isHealthy(doExtensiveHealthChecks);
    Socket toClose = null;
    synchronized (candidate) {
      if (!healthy) {
        candidate.noNewExchanges = true;
        call.releaseConnectionNoEvents();
      } else if (candidate.noNewExchanges || !CanReuseConnection.canReuseConnectionFor(candidate.route().address.url, address.url)) {
        call.releaseConnectionNoEvents();
      } else {
        return new ReusePlan(candidate);
      }

      toClose = candidate.socket();
    }

    toClose.closeQuietly();
    call.eventListener.connectionReleased(call, candidate);
    return null;
  }

  @Override
  public Plan planConnect() throws IOException {
    Route localNextRouteToTry = nextRouteToTry;
    if (localNextRouteToTry != null) {
      nextRouteToTry = null;
      return planConnectToRoute(localNextRouteToTry);
    }

    RouteSelector.Selection existingRouteSelection = routeSelection;
    if (existingRouteSelection != null && existingRouteSelection.hasNext()) {
      return planConnectToRoute(existingRouteSelection.next());
    }

    RouteSelector newRouteSelector = routeSelector;
    if (newRouteSelector == null) {
      newRouteSelector = new RouteSelector(
        address,
        call.client.routeDatabase,
        call,
        client.fastFallback,
        call.eventListener
      );
      routeSelector = newRouteSelector;
    }

    if (!newRouteSelector.hasNext()) throw new IOException("exhausted all routes");
    RouteSelector.Selection newRouteSelection = newRouteSelector.next();
    routeSelection = newRouteSelection;

    if (call.isCanceled()) throw new IOException("Canceled");

    return planConnectToRoute(newRouteSelection.next(), newRouteSelection.routes);
  }

  private Plan planReusePooledConnection(ConnectPlan planToReplace, List<Route> routes) {
    RealConnection result = client.connectionPool.delegate.callAcquirePooledConnection(
      doExtensiveHealthChecks,
      address,
      call,
      routes,
      planToReplace != null && planToReplace.isConnected
    );

    if (result == null) return null;

    if (planToReplace != null) {
      nextRouteToTry = planToReplace.route;
      planToReplace.closeQuietly();
    }

    call.eventListener.connectionAcquired(call, result);
    return new ReusePlan(result);
  }

  static class ReusePlan implements Plan {
    private final RealConnection connection;

    ReusePlan(RealConnection connection) {
      this.connection = connection;
    }

    @Override
    public boolean isConnected() {
      return true;
    }

    @Override
    public ConnectResult connect() {
      throw new IllegalStateException("already connected");
    }

    @Override
    public RealConnection handleSuccess() {
      return connection;
    }

    @Override
    public void cancel() {
      throw new IllegalStateException("unexpected cancel of reused connection");
    }
  }

  private ConnectPlan planConnectToRoute(Route route, List<Route> routes) throws IOException {
    if (route.address.sslSocketFactory == null) {
      if (!CanReuseConnection.canReuseConnectionFor(route.address.url, address.url)) {
        throw new UnknownServiceException("CLEARTEXT communication not enabled for client");
      }

      String host = route.address.url.host();
      if (!Platform.get().isCleartextTrafficPermitted(host)) {
        throw new UnknownServiceException(
          "CLEARTEXT communication to " + host + " not permitted by network security policy"
        );
      }
    } else {
      if (CanReuseConnection.canReuseConnectionFor(Protocol.H2_PRIOR_KNOWLEDGE, route.address.protocols)) {
        throw new UnknownServiceException("H2_PRIOR_KNOWLEDGE cannot be used with HTTPS");
      }
    }

    Request tunnelRequest = null;
    if (route.requiresTunnel()) {
      tunnelRequest = createTunnelRequest(route);
    }

    return new ConnectPlan(
      client,
      call,
      this,
      route,
      routes,
      0,
      tunnelRequest,
      -1,
      false
    );
  }

  private Request createTunnelRequest(Route route) throws IOException {
    Request.Builder proxyConnectRequestBuilder = new Request.Builder()
      .url(route.address.url())
      .method("CONNECT", null)
      .header("Host", route.address.url().toHostHeader(true))
      .header("Proxy-Connection", "Keep-Alive") 
      .header("User-Agent", userAgent)
      .build();

    Response fakeAuthChallengeResponse = new Response.Builder()
      .request(proxyConnectRequestBuilder)
      .protocol(Protocol.HTTP_1_1)
      .code(HttpURLConnection.HTTP_PROXY_AUTH)
      .message("Preemptive Authenticate")
      .body(EMPTY_RESPONSE)
      .sentRequestAtMillis(-1L)
      .receivedResponseAtMillis(-1L)
      .header("Proxy-Authenticate", "OkHttp-Preemptive")
      .build();

    Request authenticatedRequest = route.address.proxyAuthenticator().authenticate(route, fakeAuthChallengeResponse);

    return authenticatedRequest != null ? authenticatedRequest : proxyConnectRequestBuilder;
  }

  @Override
  public void trackFailure(IOException e) {
    if (e instanceof StreamResetException && ((StreamResetException) e).errorCode == ErrorCode.REFUSED_STREAM) {
      refusedStreamCount++;
    } else if (e instanceof ConnectionShutdownException) {
      connectionShutdownCount++;
    } else {
      otherFailureCount++;
    }
  }

  @Override
  public boolean hasFailure() {
    return refusedStreamCount > 0 || connectionShutdownCount > 0 || otherFailureCount > 0;
  }

  @Override
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
    return localRouteSelector != null && localRouteSelector.hasNext();
  }

  private Route retryRoute() {
    if (refusedStreamCount > 1 || connectionShutdownCount > 1 || otherFailureCount > 0) {
      return null;
    }

    Connection connection = call.connection;
    if (connection == null) return null;

    synchronized (connection) {
      if (connection.routeFailureCount != 0) return null;
      if (!connection.noNewExchanges) return null;
      if (!CanReuseConnection.canReuseConnectionFor(connection.route().address.url, address.url)) return null;
      return connection.route();
    }
  }

  @Override
  public boolean sameHostAndPort(HttpUrl url) {
    HttpUrl routeUrl = address.url();
    return url.port() == routeUrl.port() && url.host().equals(routeUrl.host());
  }
}