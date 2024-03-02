package okhttp3.internal.connection;

import okhttp3.*;
import okhttp3.internal.*;
import okhttp3.internal.concurrent.TaskRunner;
import okhttp3.internal.http.ExchangeCodec;
import okhttp3.internal.http.RealInterceptorChain;
import okhttp3.internal.http1.Http1ExchangeCodec;
import okhttp3.internal.http2.*;
import okhttp3.internal.platform.Platform;
import okhttp3.internal.tls.OkHostnameVerifier;
import okhttp3.internal.ws.RealWebSocket;
import okio.*;

import javax.net.ssl.*;
import java.io.IOException;
import java.lang.ref.Reference;
import java.net.ConnectException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownServiceException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PROXY_AUTH;

public class RealConnection extends Http2Connection.Listener implements Connection {

  private static final String NPE_THROW_WITH_NULL = "throw with null exception";
  private static final int MAX_TUNNEL_ATTEMPTS = 21;
  public static final long IDLE_CONNECTION_HEALTHY_NS = 10_000_000_000L; // 10 seconds.

  private final TaskRunner taskRunner;
  private final RealConnectionPool connectionPool;
  private final Route route;

  private Socket rawSocket;
  private Socket socket;
  private Handshake handshake;
  private Protocol protocol;
  private Http2Connection http2Connection;
  private BufferedSource source;
  private BufferedSink sink;

  private boolean noNewExchanges = false;
  private boolean noCoalescedConnections = false;
  private int routeFailureCount = 0;
  private int successCount = 0;
  private int refusedStreamCount = 0;
  private int allocationLimit = 1;

  private final List<Reference<RealCall>> calls = new java.util.ArrayList<>();
  private long idleAtNs = Long.MAX_VALUE;

  public RealConnection(TaskRunner taskRunner, RealConnectionPool connectionPool, Route route) {
    this.taskRunner = taskRunner;
    this.connectionPool = connectionPool;
    this.route = route;
  }

  public synchronized void noNewExchanges() {
    noNewExchanges = true;
  }

  public synchronized void noCoalescedConnections() {
    noCoalescedConnections = true;
  }

  public synchronized void incrementSuccessCount() {
    successCount++;
  }

  public void connect(int connectTimeout, int readTimeout, int writeTimeout,
                      int pingIntervalMillis, boolean connectionRetryEnabled,
                      Call call, EventListener eventListener) {
    if (isNew()) {
      EquipPlan equipPlan = firstEquipPlan();
      IOException firstException = null;
      while (true) {
        ConnectAndEquipResult result = connectAndEquip(connectTimeout, readTimeout, writeTimeout,
            pingIntervalMillis, connectionRetryEnabled, call, eventListener, equipPlan);

        if (result.failure != null) {
          // Accumulate failures.
          if (firstException == null) {
            firstException = result.failure;
          } else {
            firstException.addSuppressed(result.failure);
          }

          if (result.nextPlan == null) throw firstException; // Nothing left to try.
        } else {
          if (result.nextPlan == null) break; // Success.
        }

        equipPlan = result.nextPlan;
      }

      idleAtNs = System.nanoTime();
    } else {
      throw new IllegalStateException("already connected");
    }
  }

  private EquipPlan firstEquipPlan() throws IOException {
    if (route.address.sslSocketFactory == null) {
      if (!route.address.connectionSpecs.contains(ConnectionSpec.CLEARTEXT)) {
        throw new UnknownServiceException("CLEARTEXT communication not enabled for client");
      }

      String host = route.address.url.host;
      if (!Platform.get().isCleartextTrafficPermitted(host)) {
        throw new UnknownServiceException(
            "CLEARTEXT communication to " + host + " not permitted by network security policy");
      }
    } else {
      if (route.address.protocols.contains(Protocol.H2_PRIOR_KNOWLEDGE)) {
        throw new UnknownServiceException("H2_PRIOR_KNOWLEDGE cannot be used with HTTPS");
      }
    }

    Request tunnelRequest = route.requiresTunnel() ? createTunnelRequest() : null;

    return new EquipPlan(tunnelRequest);
  }

  private static class ConnectAndEquipResult {
    final IOException failure;
    final EquipPlan nextPlan;

    ConnectAndEquipResult(IOException failure, EquipPlan nextPlan) {
      this.failure = failure;
      this.nextPlan = nextPlan;
    }
  }

  private ConnectAndEquipResult connectAndEquip(int connectTimeout, int readTimeout, int writeTimeout,
                                                int pingIntervalMillis, boolean connectionRetryEnabled,
                                                Call call, EventListener eventListener,
                                                EquipPlan equipPlan) {
    List<ConnectionSpec> connectionSpecs = route.address.connectionSpecs;
    EquipPlan nextTlsEquipPlan = null;

    try {
      eventListener.connectStart(call, route.socketAddress, route.proxy);
      connectSocket(connectTimeout, readTimeout);

      if (equipPlan.tunnelRequest != null) {
        ConnectAndEquipResult tunnelResult = connectTunnel(readTimeout, writeTimeout, call,
            equipPlan, eventListener);

        if (tunnelResult.nextPlan != null || tunnelResult.failure != null) {
          return tunnelResult;
        }
      }

      if (route.address.sslSocketFactory != null) {
        eventListener.secureConnectStart(call);

        SSLSocket sslSocket = (SSLSocket) route.address.sslSocketFactory.createSocket(
            rawSocket, route.address.url.host, route.address.url.port, true);

        EquipPlan tlsEquipPlan = equipPlan.withCurrentOrInitialConnectionSpec(connectionSpecs, sslSocket);
        ConnectionSpec connectionSpec = connectionSpecs.get(tlsEquipPlan.connectionSpecIndex);

        nextTlsEquipPlan = tlsEquipPlan.nextConnectionSpec(connectionSpecs, sslSocket);

        connectionSpec.apply(sslSocket, tlsEquipPlan.isTlsFallback);
        connectTls(sslSocket, connectionSpec);
        eventListener.secureConnectEnd(call, handshake);
      } else {
        socket = rawSocket;
        protocol = (route.address.protocols.contains(Protocol.H2_PRIOR_KNOWLEDGE))
            ? Protocol.H2_PRIOR_KNOWLEDGE : Protocol.HTTP_1_1;
      }

      if (protocol == Protocol.HTTP_2 || protocol == Protocol.H2_PRIOR_KNOWLEDGE) {
        startHttp2(pingIntervalMillis);
      }

      eventListener.connectEnd(call, route.socketAddress, route.proxy, protocol);
      return new ConnectAndEquipResult(null, null); // Success.
    } catch (IOException e) {
      socketCloseQuietly();
      source = null;
      sink = null;
      handshake = null;
      protocol = null;
      http2Connection = null;
      allocationLimit = 1;

      eventListener.connectFailed(call, route.socketAddress, route.proxy, null, e);

      if (!connectionRetryEnabled || !retryTlsHandshake(e)) {
        nextTlsEquipPlan = null;
      }

      return new ConnectAndEquipResult(e, nextTlsEquipPlan);
    }
  }

  private void connectSocket(int connectTimeout, int readTimeout) throws IOException {
    Proxy proxy = route.proxy;
    Address address = route.address;

    rawSocket = (proxy.type() == Proxy.Type.DIRECT || proxy.type() == Proxy.Type.HTTP)
        ? address.socketFactory.createSocket()
        : new Socket(proxy);

    this.rawSocket = rawSocket;

    rawSocket.setSoTimeout(readTimeout);
    try {
      Platform.get().connectSocket(rawSocket, route.socketAddress, connectTimeout);
    } catch (ConnectException e) {
      throw new ConnectException("Failed to connect to " + route.socketAddress).initCause(e);
    }

    try {
      source = Okio.buffer(Okio.source(rawSocket));
      sink = Okio.buffer(Okio.sink(rawSocket));
    } catch (NullPointerException npe) {
      if (npe.getMessage().equals(NPE_THROW_WITH_NULL)) {
        throw new IOException(npe);
      }
    }
  }

  private ConnectAndEquipResult connectTunnel(int readTimeout, int writeTimeout,
                                              Call call, EquipPlan equipPlan,
                                              EventListener eventListener) {
    Request nextTunnelRequest = createTunnel(readTimeout, writeTimeout, equipPlan.tunnelRequest,
        route.address.url);

    if (nextTunnelRequest == null) {
      return new ConnectAndEquipResult(null, null); // Success.
    }

    socketCloseQuietly();
    rawSocket = null;
    sink = null;
    source = null;

    int nextAttempt = equipPlan.attempt + 1;
    if (nextAttempt < MAX_TUNNEL_ATTEMPTS) {
      eventListener.connectEnd(call, route.socketAddress, route.proxy, null);
      return new ConnectAndEquipResult(null, equipPlan.copy(attempt = nextAttempt,
          tunnelRequest = nextTunnelRequest));
    } else {
      IOException failure = new ProtocolException("Too many tunnel connections attempted: " +
          MAX_TUNNEL_ATTEMPTS);
      eventListener.connectFailed(call, route.socketAddress, route.proxy, null, failure);
      return new ConnectAndEquipResult(failure, null);
    }
  }

  private void startHttp2(int pingIntervalMillis) throws IOException {
    Socket socket = this.socket;
    BufferedSource source = this.source;
    BufferedSink sink = this.sink;

    if (socket == null || source == null || sink == null) {
      throw new IllegalStateException("Socket, source, and sink must be non-null");
    }

    socket.setSoTimeout(0); // HTTP/2 connection timeouts are set per-stream.
    http2Connection = new Http2Connection.Builder(true, taskRunner)
        .socket(socket, route.address.url.host, source, sink)
        .listener(this)
        .pingIntervalMillis(pingIntervalMillis)
        .build();
    this.http2Connection = http2Connection;
    this.allocationLimit = Http2Connection.DEFAULT_SETTINGS.getMaxConcurrentStreams();
    http2Connection.start();
  }

  private void connectTls(SSLSocket sslSocket, ConnectionSpec connectionSpec) throws IOException {
    Address address = route.address;
    boolean success = false;
    try {
      if (connectionSpec.supportsTlsExtensions) {
        Platform.get().configureTlsExtensions(sslSocket, address.url.host, address.protocols);
      }

      sslSocket.startHandshake();
      SSLSession sslSocketSession = sslSocket.getSession();
      Handshake unverifiedHandshake = Handshake.get(sslSocketSession);

      if (!address.hostnameVerifier.verify(address.url.host, sslSocketSession)) {
        List<X509Certificate> peerCertificates = unverifiedHandshake.peerCertificates;
        if (!peerCertificates.isEmpty()) {
          X509Certificate cert = peerCertificates.get(0);
          throw new SSLPeerUnverifiedException("Hostname " + address.url.host + " not verified:\n" +
              "    certificate: " + CertificatePinner.pin(cert) + "\n" +
              "    DN: " + cert.getSubjectDN().getName() + "\n" +
              "    subjectAltNames: " + OkHostnameVerifier.allSubjectAltNames(cert));
        } else {
          throw new SSLPeerUnverifiedException("Hostname " + address.url.host +
              " not verified (no certificates)");
        }
      }

      CertificatePinner certificatePinner = address.certificatePinner;

      handshake = new Handshake(unverifiedHandshake.tlsVersion, unverifiedHandshake.cipherSuite,
          unverifiedHandshake.localCertificates,
          () -> certificatePinner.certificateChainCleaner.clean(
              unverifiedHandshake.peerCertificates, address.url.host));

      certificatePinner.check(address.url.host,
          () -> handshake.peerCertificates.stream().map(c -> (X509Certificate) c));
      String maybeProtocol = (connectionSpec.supportsTlsExtensions)
          ? Platform.get().getSelectedProtocol(sslSocket)
          : null;
      socket = sslSocket;
      source = sslSocket.source().buffer();
      sink = sslSocket.sink().buffer();
      protocol = (maybeProtocol != null) ? Protocol.get(maybeProtocol) : Protocol.HTTP_1_1;
      success = true;
    } finally {
      Platform.get().afterHandshake(sslSocket);
      if (!success) {
        sslSocket.closeQuietly();
      }
    }
  }

  private Request createTunnel(int readTimeout, int writeTimeout, Request tunnelRequest,
                               HttpUrl url) throws IOException {
    Request nextRequest = tunnelRequest;
    String requestLine = "CONNECT " + url.toHostHeader(true) + " HTTP/1.1";
    while (true) {
      BufferedSource source = this.source;
      BufferedSink sink = this.sink;
      Http1ExchangeCodec tunnelCodec = new Http1ExchangeCodec(null, this, source, sink);
      source.timeout().timeout(readTimeout, MILLISECONDS);
      sink.timeout().timeout(writeTimeout, MILLISECONDS);
      tunnelCodec.writeRequest(nextRequest.headers, requestLine);
      tunnelCodec.finishRequest();
      Response response = tunnelCodec.readResponseHeaders(false)
          .request(nextRequest)
          .build();
      tunnelCodec.skipConnectBody(response);

      switch (response.code) {
        case HTTP_OK:
          if (!source.buffer.exhausted() || !sink.buffer.exhausted()) {
            throw new IOException("TLS tunnel buffered too many bytes!");
          }
          return null;

        case HTTP_PROXY_AUTH:
          nextRequest = route.address.proxyAuthenticator.authenticate(route, response);
          if ("close".equalsIgnoreCase(response.header("Connection"))) {
            return nextRequest;
          }
          break;

        default:
          throw new IOException("Unexpected response code for CONNECT: " + response.code);
      }
    }
  }

  private Request createTunnelRequest() {
    Request proxyConnectRequest = new Request.Builder()
        .url(route.address.url)
        .method("CONNECT", null)
        .header("Host", route.address.url.toHostHeader(true))
        .header("Proxy-Connection", "Keep-Alive") // For HTTP/1.0 proxies like Squid.
        .header("User-Agent", Internal.userAgent)
        .build();

    Response fakeAuthChallengeResponse = new Response.Builder()
        .request(proxyConnectRequest)
        .protocol(Protocol.HTTP_1_1)
        .code(HTTP_PROXY_AUTH)
        .message("Preemptive Authenticate")
        .body(Internal.EMPTY_RESPONSE)
        .sentRequestAtMillis(-1L)
        .receivedResponseAtMillis(-1L)
        .header("Proxy-Authenticate", "OkHttp-Preemptive")
        .build();

    Request authenticatedRequest = route.address.proxyAuthenticator
        .authenticate(route, fakeAuthChallengeResponse);

    return (authenticatedRequest != null) ? authenticatedRequest : proxyConnectRequest;
  }

  boolean isEligible(Address address, List<Route> routes) {
    assertThreadHoldsLock();

    if (calls.size() >= allocationLimit || noNewExchanges) {
      return false;
    }

    if (!this.route.address.equalsNonHost(address)) {
      return false;
    }

    if (address.url.host.equals(this.route().address.url.host)) {
      return true; // This connection is a perfect match.
    }

    if (http2Connection == null) {
      return false;
    }

    if (routes == null || !routeMatchesAny(routes)) {
      return false;
    }

    if (address.hostnameVerifier != OkHostnameVerifier.INSTANCE) {
      return false;
    }

    if (!supportsUrl(address.url)) {
      return false;
    }

    try {
      address.certificatePinner.check(address.url.host, handshake().peerCertificates);
    } catch (SSLPeerUnverifiedException ignored) {
      return false;
    }

    return true;
  }

  private boolean routeMatchesAny(List<Route> candidates) {
    return candidates.stream()
        .anyMatch(r -> r.proxy.type() == Proxy.Type.DIRECT &&
            route.proxy.type() == Proxy.Type.DIRECT &&
            route.socketAddress.equals(r.socketAddress));
  }

  private boolean supportsUrl(HttpUrl url) {
    assertThreadHoldsLock();

    HttpUrl routeUrl = route.address.url;

    if (url.port() != routeUrl.port()) {
      return false; // Port mismatch.
    }

    if (url.host().equals(routeUrl.host())) {
      return true; // Host match. The URL is supported.
    }

    return !noCoalescedConnections && handshake != null && certificateSupportHost(url, handshake);
  }

  private boolean certificateSupportHost(HttpUrl url, Handshake handshake) {
    List<Certificate> peerCertificates = handshake.peerCertificates;

    return !peerCertificates.isEmpty() &&
        OkHostnameVerifier.INSTANCE.verify(url.host(), (X509Certificate) peerCertificates.get(0));
  }

  ExchangeCodec newCodec(OkHttpClient client, RealInterceptorChain chain) throws SocketException {
    Socket socket = this.socket;
    BufferedSource source = this.source;
    BufferedSink sink = this.sink;
    Http2Connection http2Connection = this.http2Connection;

    if (http2Connection != null) {
      return new Http2ExchangeCodec(client, this, chain, http2Connection);
    } else {
      socket.setSoTimeout(chain.readTimeoutMillis());
      source.timeout().timeout(chain.readTimeoutMillis(), MILLISECONDS);
      sink.timeout().timeout(chain.writeTimeoutMillis(), MILLISECONDS);
      return new Http1ExchangeCodec(client, this, source, sink);
    }
  }

  RealWebSocket.Streams newWebSocketStreams(Exchange exchange) {
    Socket socket = this.socket;
    BufferedSource source = this.source;
    BufferedSink sink = this.sink;

    socket.setSoTimeout(0);
    noNewExchanges();
    return new RealWebSocket.Streams(true, source, sink) {
      @Override
      public void close() {
        exchange.bodyComplete(-1L, true, true, null);
      }
    };
  }

  @Override
  public Route route() {
    return route;
  }

  void cancel() {
    socketCloseQuietly();
  }

  @Override
  public Socket socket() {
    return socket;
  }

  boolean isHealthy(boolean doExtensiveChecks) {
    assertThreadDoesntHoldLock();

    long nowNs = System.nanoTime();

    Socket rawSocket = this.rawSocket;
    Socket socket = this.socket;
    BufferedSource source = this.source;

    if (rawSocket == null || socket == null || source == null) {
      return false;
    }

    if (rawSocket.isClosed() || socket.isClosed() || socket.isInputShutdown() ||
        socket.isOutputShutdown()) {
      return false;
    }

    Http2Connection http2Connection = this.http2Connection;
    if (http2Connection != null) {
      return http2Connection.isHealthy(nowNs);
    }

    long idleDurationNs;
    synchronized (this) {
      idleDurationNs = nowNs - idleAtNs;
    }
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

  void connectFailed(OkHttpClient client, Route failedRoute, IOException failure) {
    if (failedRoute.proxy.type() != Proxy.Type.DIRECT) {
      Address address = failedRoute.address;
      address.proxySelector.connectFailed(address.url.uri(), failedRoute.proxy.address(), failure);
    }

    client.routeDatabase().failed(failedRoute);
  }

  synchronized void trackFailure(RealCall call, IOException e) {
    if (e instanceof StreamResetException) {
      StreamResetException resetException = (StreamResetException) e;
      switch (resetException.errorCode) {
        case ErrorCode.REFUSED_STREAM:
          refusedStreamCount++;
          if (refusedStreamCount > 1) {
            noNewExchanges = true;
            routeFailureCount++;
          }
          break;
        case ErrorCode.CANCEL:
          if (call.isCanceled()) {
            // Permit any number of CANCEL errors on locally-canceled calls.
          } else {
            // Everything else wants a fresh connection.
            noNewExchanges = true;
            routeFailureCount++;
          }
          break;
        default:
          // Everything else wants a fresh connection.
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
    return "Connection{" + route.address.url.host + ":" + route.address.url.port + ", " +
        "proxy=" + route.proxy + " hostAddress=" + route.socketAddress + " cipherSuite=" +
        (handshake != null ? handshake.cipherSuite() : "none") + " protocol=" + protocol + "}";
  }

  static RealConnection newTestConnection(TaskRunner taskRunner, RealConnectionPool connectionPool,
                                          Route route, Socket socket, long idleAtNs) {
    RealConnection result = new RealConnection(taskRunner, connectionPool, route);
    result.socket = socket;
    result.idleAtNs = idleAtNs;
    return result;
  }
}
