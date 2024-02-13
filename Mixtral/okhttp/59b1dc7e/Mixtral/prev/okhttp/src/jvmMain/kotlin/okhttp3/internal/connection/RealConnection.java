
package okhttp3.internal.connection;

import java.io.IOException;
import java.lang.ref.Reference;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.HttpURLConnection.HTTP_OK;
import java.net.HttpURLConnection.HTTP_PROXY_AUTH;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownServiceException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import okhttp3.Address;
import okhttp3.Call;
import okhttp3.CertificatePinner;
import okhttp3.Connection;
import okhttp3.ConnectionSpec;
import okhttp3.ConnectionSpecSelector;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.Handshake.Companion;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.internal.EMPTY_RESPONSE;
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
import okhttp3.internal.platform.Platform;
import okhttp3.internal.tls.OkHostnameVerifier;
import okhttp3.internal.toHostHeader;
import okhttp3.internal.userAgent;
import okhttp3.internal.ws.RealWebSocket;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Buffer;
import okio.Sink;
import okio.Source;

class RealConnection implements Connection, Http2Connection.Listener {

  private Socket rawSocket;
  private Socket socket;
  private Handshake handshake;
  private Protocol protocol;
  private Http2Connection http2Connection;
  private BufferedSource source;
  private BufferedSink sink;

  private boolean noNewExchanges;
  private boolean noCoalescedConnections;
  private int routeFailureCount;
  private int successCount;
  private int refusedStreamCount;
  private int allocationLimit;
  private List<Reference<RealCall>> calls;
  private long idleAtNs;

  RealConnection(
      TaskRunner taskRunner,
      RealConnectionPool connectionPool,
      Route route
  ) {
    this.taskRunner = taskRunner;
    this.connectionPool = connectionPool;
    this.route = route;
  }



    private static final String NPE_THROW_WITH_NULL = "throw with null exception";
    private static final int MAX_TUNNEL_ATTEMPTS = 21;
    public static final long IDLE_CONNECTION_HEALTHY_NS = 10_000_000_000L; // 10 seconds.

    public static RealConnection newTestConnection(
            TaskRunner taskRunner,
            RealConnectionPool connectionPool,
            Route route,
            Socket socket,
            long idleAtNs) {
        RealConnection result = new RealConnection(taskRunner, connectionPool, route);
        result.socket = socket;
        result.idleAtNs = idleAtNs;
        return result;
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

public void connect(
        int connectTimeout,
        int readTimeout,
        int writeTimeout,
        int pingIntervalMillis,
        boolean connectionRetryEnabled,
        Call call,
        EventListener eventListener) {
    if (protocol != null) {
        throw new IllegalStateException("already connected");
    }

    RouteException routeException = null;
    ConnectionSpecSelector connectionSpecSelector = new ConnectionSpecSelector(route.address.connectionSpecs);

    if (route.address.sslSocketFactory == null) {
        if (!connectionSpecs.contains(ConnectionSpec.CLEARTEXT)) {
            throw new RouteException(
                    new UnknownServiceException(
                            "CLEARTEXT communication not enabled for client"
                    )
            );
        }
        String host = route.address.url.getHost();
        if (!Platform.get().isCleartextTrafficPermitted(host)) {
            throw new RouteException(
                    new UnknownServiceException(
                            "CLEARTEXT communication to " + host + " not permitted by network security policy"
                    )
            );
        }
    } else {
        if (route.address.protocols.contains(Protocol.H2_PRIOR_KNOWLEDGE)) {
            throw new RouteException(
                    new UnknownServiceException(
                            "H2_PRIOR_KNOWLEDGE cannot be used with HTTPS"
                    )
            );
        }
    }

    while (true) {
        try {
            if (route.requiresTunnel()) {
                connectTunnel(connectTimeout, readTimeout, writeTimeout, call, eventListener);
                if (rawSocket == null) {
                    break;
                }
            } else {
                connectSocket(connectTimeout, readTimeout, call, eventListener);
            }
            establishProtocol(connectionSpecSelector, pingIntervalMillis, call, eventListener);
            eventListener.connectEnd(call, route.socketAddress, route.proxy, protocol);
            break;
        } catch (IOException e) {
            closeQuietly(socket);
            closeQuietly(rawSocket);
            socket = null;
            rawSocket = null;
            source = null;
            sink = null;
            handshake = null;
            protocol = null;
            http2Connection = null;
            allocationLimit = 1;

            eventListener.connectFailed(call, route.socketAddress, route.proxy, null, e);

            if (routeException == null) {
                routeException = new RouteException(e);
            } else {
                routeException.addConnectException(e);
            }

            if (!connectionRetryEnabled || !connectionSpecSelector.connectionFailed(e)) {
                throw routeException;
            }
        }
    }

    if (route.requiresTunnel() && rawSocket == null) {
        throw new RouteException(
                new ProtocolException(
                        "Too many tunnel connections attempted: " + MAX_TUNNEL_ATTEMPTS
                )
        );
    }

    idleAtNs = System.nanoTime();
}

  void connectTunnel(int connectTimeout, int readTimeout, int writeTimeout, Call call, EventListener eventListener) throws IOException {
    Request tunnelRequest = createTunnelRequest();
    HttpUrl url = tunnelRequest.url();
    for (int i = 0; i < MAX_TUNNEL_ATTEMPTS; i++) {
      connectSocket(connectTimeout, readTimeout, call, eventListener);
      tunnelRequest = createTunnel(readTimeout, writeTimeout, tunnelRequest, url);
      if (tunnelRequest == null) break;

      if (rawSocket != null) rawSocket.closeQuietly();
      rawSocket = null;
      source = null;
      sink = null;
      eventListener.connectEnd(call, route.socketAddress(), route.proxy(), null);
    }
  }

  void connectSocket(int connectTimeout, int readTimeout, Call call, EventListener eventListener) throws IOException {
    Proxy.Type proxyType = route.proxy().type();
    SocketAddress address = route.address();

    Socket rawSocket = (proxyType == Proxy.Type.DIRECT || proxyType == Proxy.Type.HTTP)
        ? address.socketFactory().createSocket()
        : new Socket(route.proxy());
    this.rawSocket = rawSocket;

    eventListener.connectStart(call, route.socketAddress(), route.proxy());
    rawSocket.setSoTimeout(readTimeout);
    try {
      Platform.get().connectSocket(rawSocket, route.socketAddress(), connectTimeout);
    } catch (SocketTimeoutException e) {
      throw new ConnectException("Failed to connect to " + route.socketAddress());
    }

    try {
      source = Okio.buffer(rawSocket.getSource());
      sink = Okio.buffer(rawSocket.getSink());
    } catch (NullPointerException npe) {
      if ("throw with null".equals(npe.getMessage())) {
        throw new IOException(npe);
      }
    }
  }

  void establishProtocol(ConnectionSpecSelector connectionSpecSelector, int pingIntervalMillis, Call call, EventListener eventListener) throws IOException {
    SSLSocketFactory sslSocketFactory = route.address().sslSocketFactory();
    if (sslSocketFactory == null) {
      if (Protocol.H2_PRIOR_KNOWLEDGE.isIn(route.address().protocols())) {
        socket = rawSocket;
        protocol = Protocol.H2_PRIOR_KNOWLEDGE.toString();
        startHttp2(pingIntervalMillis);
        return;
      }

      socket = rawSocket;
      protocol = Protocol.HTTP_1_1.toString();
      return;
    }

    eventListener.secureConnectStart(call);
    connectTls(connectionSpecSelector);
    eventListener.secureConnectEnd(call, handshake);

    if (protocol.equals(Protocol.HTTP_2.toString())) {
      startHttp2(pingIntervalMillis);
    }
  }

  void startHttp2(int pingIntervalMillis) throws IOException {
    Socket socket = this.socket;
    Source source = this.source;
    Sink sink = this.sink;
    socket.setSoTimeout(0);
    Http2Connection http2Connection = new Http2Connection.Builder(true, taskRunner)
        .socket(socket, route.address().url().host(), source, sink)
        .listener(this)
        .pingIntervalMillis(pingIntervalMillis)
        .build();
    this.http2Connection = http2Connection;
    this.allocationLimit = Http2Connection.DEFAULT_SETTINGS.getMaxConcurrentStreams();
    http2Connection.start();
  }

  void connectTls(ConnectionSpecSelector connectionSpecSelector) throws IOException {
    Address address = route.address();
    SSLSocketFactory sslSocketFactory = address.sslSocketFactory();
    boolean success = false;
    SSLSocket sslSocket = null;
    try {
      sslSocket = (SSLSocket) sslSocketFactory.createSocket(
          rawSocket, address.url().host(), address.url().port(), true /* autoClose */);

      ConnectionSpec connectionSpec = connectionSpecSelector.configureSecureSocket(sslSocket);
      if (connectionSpec.supportsTlsExtensions()) {
        Platform.get().configureTlsExtensions(sslSocket, address.url().host(), address.protocols());
      }

      sslSocket.startHandshake();
      SSLSession sslSocketSession = sslSocket.getSession();
      Handshake unverifiedHandshake = sslSocketSession.getHandshake();

      if (!address.hostnameVerifier().verify(address.url().host(), sslSocketSession)) {
        List<X509Certificate> peerCertificates = unverifiedHandshake.peerCertificates();
        if (!peerCertificates.isEmpty()) {
          X509Certificate cert = peerCertificates.get(0);
          throw new SSLPeerUnverifiedException(
              "Hostname " + address.url().host() + " not verified:\n" +
                      "    certificate: " + CertificatePinner.pin(cert) + "\n" +
                      "    DN: " + cert.getSubjectDN().getName() + "\n" +
                      "    subjectAltNames: " + OkHostnameVerifier.allSubjectAltNames(cert));
        } else {
          throw new SSLPeerUnverifiedException(
              "Hostname " + address.url().host() + " not verified (no certificates)");
        }
      }

      handshake = new Handshake(
          unverifiedHandshake.tlsVersion(),
          unverifiedHandshake.cipherSuite(),
          unverifiedHandshake.localCertificates());

      address.certificatePinner().check(address.url().host(), handshake.peerCertificates());

      String maybeProtocol = connectionSpec.supportsTlsExtensions()
          ? Platform.get().getSelectedProtocol(sslSocket)
          : null;
      socket = sslSocket;
      source = Okio.buffer(sslSocket.getSource());
      sink = Okio.buffer(sslSocket.getSink());
      protocol = maybeProtocol != null ? maybeProtocol : Protocol.HTTP_1_1.toString();
      success = true;
    } finally {
      if (sslSocket != null) {
        Platform.get().afterHandshake(sslSocket);
      }
      if (!success) {
        sslSocket.closeQuietly();
      }
    }
  }

  Request createTunnel(int readTimeout, int writeTimeout, Request tunnelRequest, HttpUrl url) throws IOException {
    Request nextRequest = tunnelRequest;
    String requestLine = "CONNECT " + url.toHostHeader(true) + " HTTP/1.1";
    while (true) {
      Source source = this.source;
      Sink sink = this.sink;
      Http1ExchangeCodec tunnelCodec = new Http1ExchangeCodec(null, this, source, sink);
      source.timeout().timeout(readTimeout, TimeUnit.MILLISECONDS);
      sink.timeout().timeout(writeTimeout, TimeUnit.MILLISECONDS);
      tunnelCodec.writeRequest(nextRequest.headers(), requestLine);
      tunnelCodec.finishRequest();
      Response response = tunnelCodec.readResponseHeaders(false);
      nextRequest = response.request().newBuilder().build();

      if (response.code() == HTTP_OK) {
        if (!source.buffer().exhausted() || !sink.buffer().exhausted()) {
          throw new IOException("TLS tunnel buffered too many bytes!");
        }
        return null;
      }

      if (response.code() == HTTP_PROXY_AUTH) {
        nextRequest = route.address().proxyAuthenticator().authenticate(route, response)
            .orElseThrow(() -> new IOException("Failed to authenticate with proxy"));

        if ("close".equalsIgnoreCase(response.header("Connection"))) {
          return nextRequest;
        }
      }

      throw new IOException("Unexpected response code for CONNECT: " + response.code());
    }
  }
        private Request createTunnelRequest() throws IOException {
        Builder proxyConnectRequestBuilder = new Request.Builder()
                .url(route.address.url())
                .method("CONNECT", null)
                .header("Host", route.address.url().toHostHeader(true))
                .header("Proxy-Connection", "Keep-Alive")
                .header("User-Agent", userAgent);

        Response.Builder fakeAuthChallengeResponseBuilder = new Response.Builder()
                .request(proxyConnectRequestBuilder.build())
                .protocol(Protocol.HTTP_1_1)
                .code(HTTP_PROXY_AUTH)
                .message("Preemptive Authenticate")
                .body(ResponseBody.create(null, new byte[0]))
                .sentRequestAtMillis(-1L)
                .receivedResponseAtMillis(-1L)
                .header("Proxy-Authenticate", "OkHttp-Preemptive");

        Request authenticatedRequest = route.address.proxyAuthenticator()
                .authenticate(route, fakeAuthChallengeResponseBuilder.build());

        return authenticatedRequest != null ? authenticatedRequest : proxyConnectRequestBuilder.build();
    }

    public boolean isEligible(Address address, List<Route> routes) {
        if (calls.size() >= allocationLimit || noNewExchanges) return false;

        if (!route.address.equalsNonHost(address)) return false;

        if (address.url().host().equals(route.address.url().host())) {
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
        return candidates.stream().anyMatch(candidate ->
                candidate.proxy().type() == Proxy.Type.DIRECT &&
                        route.proxy().type() == Proxy.Type.DIRECT &&
                        route.socketAddress().equals(candidate.socketAddress()));
    }

    private boolean supportsUrl(HttpUrl url) {
        if (url.port() != route.address.url().port()) {
            return false;
        }

        if (url.host().equals(route.address.url().host())) {
            return true;
        }

        return !noCoalescedConnections && handshake != null && certificateSupportHost(url, handshake);
    }

    private boolean certificateSupportHost(HttpUrl url, Handshake handshake) {
        List<Certificate> peerCertificates = handshake.peerCertificates();

        return peerCertificates.size() > 0 &&
                OkHostnameVerifier.INSTANCE.verify(url.host(), peerCertificates.get(0));
    }

    @Throws(SocketException.class)
    public ExchangeCodec newCodec(OkHttpClient client, RealInterceptorChain chain) {
        Socket socket = this.socket;
        Source source = this.source;
        Sink sink = this.sink;
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

    @Throws(SocketException.class)
    public RealWebSocket.Streams newWebSocketStreams(Exchange exchange) {
        Socket socket = this.socket;
        Source source = this.source;
        Sink sink = this.sink;

        socket.setSoTimeout(0);
        noNewExchanges();
        return new RealWebSocket.Streams(true, source, sink) {
            @Override
            public void close() {
                exchange.bodyComplete(-1L, true, true, null);
            }
        };
    }

    public Route route() {
        return route;
    }

    public void cancel() {
        if (rawSocket != null) {
            rawSocket.closeQuietly();
        }
    }

    public Socket socket() {
        return socket;
    }

    public boolean isHealthy(boolean doExtensiveChecks) {
        long nowNs = System.nanoTime();

        Socket socket = this.socket;
        if (socket.isClosed() || socket.isInputShutdown() || socket.isOutputShutdown()) {
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

    @Throws(IOException.class)
    public void onStream(Http2Stream stream) {
        stream.close(ErrorCode.REFUSED_STREAM, null);
    }

    @Synchronized
    public void onSettings(Http2Connection connection, Settings settings) {
        allocationLimit = settings.getMaxConcurrentStreams();
    }

    public Handshake handshake() {
        return handshake;
    }

    public void connectFailed(OkHttpClient client, Route failedRoute, IOException failure) {
        if (failedRoute.proxy().type() != Proxy.Type.DIRECT) {
            Address address = failedRoute.address();
            address.proxySelector().connectFailed(
                    address.url().toURI(), failedRoute.proxy().address(), failure
            );
        }

        client.routeDatabase().failed(failedRoute);
    }

    @Synchronized
    public void trackFailure(RealCall call, IOException e) {
        if (e instanceof StreamResetException) {
            StreamResetException streamResetException = (StreamResetException) e;
            if (streamResetException.errorCode() == ErrorCode.REFUSED_STREAM) {
                refusedStreamCount++;
                if (refusedStreamCount > 1) {
                    noNewExchanges = true;
                    routeFailureCount++;
                }
            } else if (streamResetException.errorCode() == ErrorCode.CANCEL && call.isCanceled()) {
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

    public Protocol protocol() {
        return protocol;
    }

    @Override
    public String toString() {
        return "Connection{" +
                "route.address.url.host=" + route.address.url().host() +
                ", route.address.url.port=" + route.address.url().port() +
                ", route.proxy=" + route.proxy() +
                ", route.socketAddress=" + route.socketAddress() +
                ", cipherSuite=" + (handshake != null ? handshake.cipherSuite() : "none") +
                ", protocol=" + protocol +
                '}';
    }
}