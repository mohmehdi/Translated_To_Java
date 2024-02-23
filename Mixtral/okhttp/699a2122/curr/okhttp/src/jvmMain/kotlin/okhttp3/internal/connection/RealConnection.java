

package okhttp3.internal.connection;

import java.io.IOException;
import java.lang.ref.Reference;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import okhttp3.Address;
import okhttp3.Connection;
import okhttp3.Handshake;
import okhttp3.Protocol;
import okhttp3.Route;
import okhttp3.internal.assertThreadDoesntHoldLock;
import okhttp3.internal.assertThreadHoldsLock;
import okhttp3.internal.closeQuietly;
import okhttp3.internal.concurrent.TaskRunner;
import okhttp3.internal.http.ExchangeCodec;
import okhttp3.internal.http.RealInterceptorChain;
import okhttp3.internal.http1.Http1ExchangeCodec;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.Http2Connection;
import okhttp3.internal.http2.Http2ExchangeCodec;
import okhttp3.internal.http2.Http2Stream;
import okhttp3.internal.http2.Settings;
import okhttp3.internal.http2.StreamResetException;
import okhttp3.internal.isHealthy;
import okhttp3.internal.tls.OkHostnameVerifier;
import okhttp3.internal.ws.RealWebSocket;
import okio.BufferedSink;
import okio.BufferedSource;

public class RealConnection implements Connection, ExchangeCodec.Carrier {
  private TaskRunner taskRunner;
  private RealConnectionPool connectionPool;
  private Route route;
  private Socket rawSocket;
  private Socket socket;
  private Handshake handshake;
  private Protocol protocol;
  private BufferedSource source;
  private BufferedSink sink;
  private final int pingIntervalMillis;
  private Http2Connection http2Connection;
  private boolean noNewExchanges;
  private boolean noCoalescedConnections;
  private int routeFailureCount;
  private int successCount;
  private int refusedStreamCount;
  private int allocationLimit;
  private final List<Reference<RealCall>> calls;
  private long idleAtNs;
  private boolean isMultiplexed;

  public RealConnection(
      TaskRunner taskRunner,
      RealConnectionPool connectionPool,
      Route route,
      Socket rawSocket,
      Socket socket,
      Handshake handshake,
      Protocol protocol,
      BufferedSource source,
      BufferedSink sink,
      int pingIntervalMillis) {
    this.taskRunner = taskRunner;
    this.connectionPool = connectionPool;
    this.route = route;
    this.rawSocket = rawSocket;
    this.socket = socket;
    this.handshake = handshake;
    this.protocol = protocol;
    this.source = source;
    this.sink = sink;
    this.pingIntervalMillis = pingIntervalMillis;
  }

  @Override
  public synchronized void noNewExchanges() {
    noNewExchanges = true;
  }

  @Override
  public synchronized void noCoalescedConnections() {
    noCoalescedConnections = true;
  }

  @Override
  public synchronized void incrementSuccessCount() {
    successCount++;
  }

  @Override
  public synchronized void start() throws IOException {
    idleAtNs = System.nanoTime();
    if (protocol == Protocol.HTTP_2 || protocol == Protocol.H2_PRIOR_KNOWLEDGE) {
      startHttp2();
    }
  }

  private void startHttp2() throws IOException {
    Socket socket = this.socket;
    BufferedSource source = this.source;
    BufferedSink sink = this.sink;
    socket.setSoTimeout(0);
    Http2Connection.Builder http2ConnectionBuilder =
        new Http2Connection.Builder(true, taskRunner)
            .socket(socket, route.address().url().host(), source, sink)
            .listener(this)
            .pingIntervalMillis(pingIntervalMillis);
    Http2Connection http2Connection = http2ConnectionBuilder.build();
    this.http2Connection = http2Connection;
    this.allocationLimit = Http2Connection.DEFAULT_SETTINGS.getMaxConcurrentStreams();
    http2Connection.start();
  }

  @Override
  public boolean isEligible(Address address, List<Route> routes) {
    assertThreadHoldsLock();

    if (calls.size() >= allocationLimit || noNewExchanges) return false;

    if (!route.address().equalsNonHost(address)) return false;

    if (address.url().host().equals(route().address().url().host())) {
      return true;
    }

    if (http2Connection == null) return false;

    if (routes == null || !routeMatchesAny(routes)) return false;

    if (address.hostnameVerifier() != OkHostnameVerifier.INSTANCE) return false;
    if (!supportsUrl(address.url())) return false;

    try {
      address.certificatePinner().check(address.url().host(), handshake().peerCertificates());
    } catch (SSLPeerUnverifiedException e) {
      return false;
    }

    return true;
  }

  private boolean routeMatchesAny(List<Route> candidates) {
    return candidates.stream()
        .anyMatch(
            candidate ->
                candidate.proxy().type() == Proxy.Type.DIRECT
                    && route.proxy().type() == Proxy.Type.DIRECT
                    && route.socketAddress().equals(candidate.socketAddress()));
  }

  private boolean supportsUrl(HttpUrl url) {
    assertThreadHoldsLock();

    Route.Address routeUrl = route.address();

    if (url.port() != routeUrl.url().port()) {
      return false;
    }

    if (url.host().equals(routeUrl.url().host())) {
      return true;
    }

    if (!noCoalescedConnections
        && handshake != null
        && certificateSupportHost(url, handshake)) {
      return true;
    }

    return false;
  }

  private boolean certificateSupportHost(HttpUrl url, Handshake handshake) {
    List<X509Certificate> peerCertificates = handshake.peerCertificates();

    return !peerCertificates.isEmpty()
        && OkHostnameVerifier.verify(url.host(), peerCertificates.get(0));
  }

  @Override
  public ExchangeCodec newCodec(OkHttpClient client, RealInterceptorChain chain)
      throws SocketException {
    Socket socket = this.socket;
    BufferedSource source = this.source;
    BufferedSink sink = this.sink;
    Http2Connection http2Connection = this.http2Connection;

    if (http2Connection != null) {
      return new Http2ExchangeCodec(client, this, chain, http2Connection);
    } else {
      socket.setSoTimeout(chain.readTimeoutMillis());
      source.timeout().timeout(chain.readTimeoutMillis(), TimeUnit.MILLISECONDS);
      sink.timeout().timeout(chain.writeTimeoutMillis(), TimeUnit.MILLISECONDS);
      return new Http1ExchangeCodec(client, this, source, sink);
    }
  }

  @Override
  public RealWebSocket.Streams newWebSocketStreams(Exchange exchange) {
    Socket socket = this.socket;
    BufferedSource source = this.source;
    BufferedSink sink = this.sink;

    socket.setSoTimeout(0);
    noNewExchanges();
    return new RealWebSocket.Streams(true, source, sink) {
      @Override
      public void close() {
        exchange.bodyComplete(null);
      }
    };
  }

  @Override
  public Route route() {
    return route;
  }

  @Override
  public void cancel() {
    closeQuietly(rawSocket);
  }

  @Override
  public Socket socket() {
    return socket;
  }

  public boolean isHealthy(boolean doExtensiveChecks) {
    assertThreadDoesntHoldLock();

    long nowNs = System.nanoTime();

    Socket rawSocket = this.rawSocket;
    Socket socket = this.socket;
    BufferedSource source = this.source;
    if (rawSocket.isClosed()
        || socket.isClosed()
        || socket.isInputShutdown()
        || socket.isOutputShutdown()) {
      return false;
    }

    Http2Connection http2Connection = this.http2Connection;
    if (http2Connection != null) {
      return http2Connection.isHealthy(nowNs);
    }

    long idleDurationNs = nowNs - idleAtNs;
    if (idleDurationNs >= IDLE_CONNECTION_HEALTHY_NS && doExtensiveChecks) {
      return socket.isHealthy(source);
    }

    return true;
  }

  @Override
  public void onStream(Http2Stream stream) {
    stream.close(ErrorCode.REFUSED_STREAM, null);
  }

  @Override
  public synchronized void onSettings(Http2Connection connection, Settings settings) {
    allocationLimit = settings.getMaxConcurrentStreams();
  }

  @Override
  public Handshake handshake() {
    return handshake;
  }

  public void connectFailed(OkHttpClient client, Route failedRoute, IOException failure) {
    if (failedRoute.proxy().type() != Proxy.Type.DIRECT) {
      Address address = failedRoute.address();
      address.proxySelector().connectFailed(
          address.url().toURI(), failedRoute.proxy().address(), failure);
    }

    client.routeDatabase().failed(failedRoute);
  }

  @Override
  public synchronized void trackFailure(RealCall call, IOException e) {
    if (e instanceof StreamResetException) {
      StreamResetException streamResetException = (StreamResetException) e;
      int errorCode = streamResetException.errorCode();
      if (errorCode == ErrorCode.REFUSED_STREAM) {
        refusedStreamCount++;
        if (refusedStreamCount > 1) {
          noNewExchanges = true;
          routeFailureCount++;
        }
      } else if (errorCode == ErrorCode.CANCEL && call.isCanceled()) {
        // Do nothing
      } else {
        noNewExchanges = true;
        routeFailureCount++;
      }
    } else if (!isMultiplexed || e instanceof ConnectionShutdownException) {
      noNewExchanges = true;

      if (successCount == 0) {
        if (e != null) {
          connectFailed(call.client(), route, e);
        }
        routeFailureCount++;
      }
    }
  }

  @Override
  public Protocol protocol() {
    return protocol;
  }

  @Override
  public String toString() {
    return "Connection{"
        + route.address().url().host()
        + ":"
        + route.address().url().port()
        + ", proxy="
        + route.proxy()
        + ", hostAddress="
        + route.socketAddress()
        + ", cipherSuite="
        + (handshake != null ? handshake.cipherSuite() : "none")
        + ", protocol="
        + protocol
        + '}';
  }

  public static RealConnection newTestConnection(
      TaskRunner taskRunner,
      RealConnectionPool connectionPool,
      Route route,
      Socket socket,
      long idleAtNs) {
    RealConnection result =
        new RealConnection(
            taskRunner,
            connectionPool,
            route,
            null,
            socket,
            null,
            null,
            null,
            null,
            0);
    result.idleAtNs = idleAtNs;
    return result;
  }
}