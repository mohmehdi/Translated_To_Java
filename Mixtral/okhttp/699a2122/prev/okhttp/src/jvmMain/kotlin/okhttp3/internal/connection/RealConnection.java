package okhttp3.internal.connection;

import java.io.IOException;
import java.lang.ref.Reference;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownServiceException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import okhttp3.Address;
import okhttp3.Call;
import okhttp3.Call.Factory;
import okhttp3.CertificatePinner;
import okhttp3.Connection;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.Handshake.Builder;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.internal.EmptyResponse;
import okhttp3.internal.Internal;
import okhttp3.internal.Platform;
import okhttp3.internal.RealCall;
import okhttp3.internal.RouteDatabase;
import okhttp3.internal.SocketPolicy;
import okhttp3.internal.UserAgent;
import okhttp3.internal.connection.RealConnection.EquipPlan;
import okhttp3.internal.connection.RealConnection.ConnectAndEquipResult;
import okhttp3.internal.http.ExchangeCodec;
import okhttp3.internal.http.Http1ExchangeCodec;
import okhttp3.internal.http.Http2Connection;
import okhttp3.internal.http.Http2ExchangeCodec;
import okhttp3.internal.http.Http2Stream;
import okhttp3.internal.http.Requestor;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.Settings;
import okhttp3.internal.platform.PlatformJdk;
import okhttp3.internal.tls.OkHostnameVerifier;
import okhttp3.internal.userAgent;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ForwardingSink;
import okio.ForwardingSource;
import okio.Okio;

public class RealConnection implements Connection {

  private static final String NPE_THROW_WITH_NULL = "throw with null exception";
  private static final int MAX_TUNNEL_ATTEMPTS = 21;
  private static final long IDLE_CONNECTION_HEALTHY_NS = 10_000_000_000 L; // 10 seconds.

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

  private boolean noNewExchanges;
  private boolean noCoalescedConnections;
  private int routeFailureCount;
  private int successCount;
  private int refusedStreamCount;
  private int allocationLimit;
  private List < Reference > calls;
  private long idleAtNs;

  private final Object callLock = new Object();
  private final AtomicInteger refCount = new AtomicInteger();

  public RealConnection(TaskRunner taskRunner, RealConnectionPool connectionPool, Route route) {
    this.taskRunner = taskRunner;
    this.connectionPool = connectionPool;
    this.route = route;
    this.calls = new ArrayList < > ();
  }

  @Override
  public synchronized Route route() {
    return route;
  }

  private void connectSocket(int connectTimeout, int readTimeout) throws IOException {
    Proxy proxy = route.proxy();
    Address address = route.address();

    rawSocket = address.socketFactory().createSocket();
    this.rawSocket.setSoTimeout(readTimeout);

    PlatformJdk.connectSocket(rawSocket, route.socketAddress(), connectTimeout);

    source = Okio.buffer(Okio.source(rawSocket));
    sink = Okio.buffer(Okio.sink(rawSocket));
  }

  private void connectTls(SSLSocket sslSocket, ConnectionSpec connectionSpec) throws IOException {
    Address address = route.address();

    if (connectionSpec.supportsTlsExtensions()) {
      PlatformJdk.configureTlsExtensions(sslSocket, address.url().host(), address.protocols());
    }

    sslSocket.startHandshake();
    Handshake.Builder builder = new Handshake.Builder();
    builder.tlsVersion(sslSocket.getSession().getProtocol());
    builder.cipherSuite(sslSocket.getSession().getCipherSuite());
    List localCertificates = new ArrayList < > ();
    localCertificates.add(sslSocket.getSession().getLocalCertificates()[0]);
    builder.localCertificates(localCertificates);
    handshake = builder.build();

    CertificatePinner certificatePinner = address.certificatePinner();
    List peerCertificates = sslSocket.getSession().getPeerCertificates();
    certificatePinner.check(address.url().host(), peerCertificates);

    socket = sslSocket;
    source = Okio.buffer(sslSocket.getSource());
    sink = Okio.buffer(sslSocket.getSink());

    if (connectionSpec.supportsTlsExtensions()) {
      String maybeProtocol = PlatformJdk.getSelectedProtocol(sslSocket);
      protocol = maybeProtocol != null ? Protocol.get(maybeProtocol) : Protocol.HTTP_1_1;
    } else {
      protocol = Protocol.HTTP_1_1;
    }
  }

  void connect(int connectTimeout, int readTimeout, int writeTimeout, int pingIntervalMillis, boolean connectionRetryEnabled, Call call, EventListener eventListener) {
    check(isNew) {
      "already connected"
    };
    EquipPlan equipPlan = firstEquipPlan();
    IOException firstException = null;
    while (true) {
      val(failure, nextPlan) = connectAndEquip(
        connectTimeout, readTimeout, writeTimeout, pingIntervalMillis, connectionRetryEnabled, call, eventListener, equipPlan);
      if (failure != null) {
        // Accumulate failures.
        if (firstException == null) {
          firstException = failure;
        } else {
          firstException.addSuppressed(failure);
        }
        if (nextPlan == null) throw firstException; // Nothing left to try.
      } else {
        if (nextPlan == null) break; // Success.
      }
      equipPlan = nextPlan;
    }
    idleAtNs = System.nanoTime();
}

  private EquipPlan firstEquipPlan() {
    if (route.address().sslSocketFactory() == null) {
      if (!route.address().connectionSpecs().contains(ConnectionSpec.CLEARTEXT)) {
        throw new UnknownServiceException("CLEARTEXT communication not enabled for client");
      }

      String host = route.address().url().host();
      if (!PlatformJdk.isCleartextTrafficPermitted(host)) {
        throw new UnknownServiceException(
          "CLEARTEXT communication to " + host + " not permitted by network security policy");
      }
    } else {
      if (Protocol.H2_PRIOR_KNOWLEDGE.equals(route.address().protocols()[0])) {
        throw new UnknownServiceException("H2_PRIOR_KNOWLEDGE cannot be used with HTTPS");
      }
    }

    Request tunnelRequest = null;
    if (route.requiresTunnel()) {
      tunnelRequest = createTunnelRequest();
    }

    return new EquipPlan(tunnelRequest);
  }

  private class EquipPlan {
    private final Request tunnelRequest;
    private final int attempt;

    EquipPlan(Request tunnelRequest) {
      this(tunnelRequest, 0);
    }

    EquipPlan(Request tunnelRequest, int attempt) {
      this.tunnelRequest = tunnelRequest;
      this.attempt = attempt;
    }

    EquipPlan withCurrentOrInitialConnectionSpec(List connectionSpecs,
      SSLSocket sslSocket) {
      ConnectionSpec connectionSpec =
        connectionSpecs.get(Internal.sslConnectionSocketFactory().getSupportedConnectionSpecs().indexOf(
          connectionSpecs.get(0)));
      return new EquipPlan(tunnelRequest, attempt, connectionSpec, sslSocket);
    }

    EquipPlan nextConnectionSpec(List connectionSpecs, SSLSocket sslSocket) {
      int index =
        connectionSpecs.indexOf(Internal.sslConnectionSocketFactory().getSupportedConnectionSpecs().get(
          connectionSpec.index()));
      if (index + 1 < connectionSpecs.size()) {
        return new EquipPlan(tunnelRequest, attempt, connectionSpecs.get(index + 1), sslSocket);
      } else {
        return null;
      }
    }
  }

 

  private ConnectAndEquipResult connectAndEquip(int connectTimeout, int readTimeout,
    int writeTimeout, int pingIntervalMillis, boolean connectionRetryEnabled, Call call,
    EventListener eventListener, EquipPlan equipPlan) throws IOException {
    ConnectAndEquipResult result = null;

    while (true) {
      result = connectAndEquip(connectTimeout, readTimeout, writeTimeout, pingIntervalMillis,
        connectionRetryEnabled, call, eventListener, equipPlan, result);
      if (result.failure != null) {
        if (result.nextPlan == null) {
          throw result.failure;
        }
        equipPlan = result.nextPlan;
      } else {
        break;
      }
    }

    idleAtNs = System.nanoTime();
    return result;
  }

  private ConnectAndEquipResult connectAndEquip(int connectTimeout, int readTimeout,
    int writeTimeout, int pingIntervalMillis, boolean connectionRetryEnabled, Call call,
    EventListener eventListener, EquipPlan equipPlan, ConnectAndEquipResult previousResult) throws IOException {
    ConnectAndEquipResult result = new ConnectAndEquipResult();

    if (equipPlan.tunnelRequest != null) {
      result = connectTunnel(readTimeout, writeTimeout, call, equipPlan, eventListener);
      if (result.failure != null || result.nextPlan != null) {
        return result;
      }
    }

    if (route.address().sslSocketFactory() != null) {
      eventListener.secureConnectStart(call);

      SSLSocket sslSocket =
        route.address().sslSocketFactory().createSocket(rawSocket, route.address().url().host(),
          route.address().url().port(), true);

      EquipPlan nextEquipPlan = equipPlan.withCurrentOrInitialConnectionSpec(
        route.address().connectionSpecs(), sslSocket);
      ConnectionSpec connectionSpec =
        route.address().connectionSpecs().get(nextEquipPlan.connectionSpec.index());

      nextEquipPlan = nextEquipPlan.nextConnectionSpec(route.address().connectionSpecs(), sslSocket);

      connectionSpec.configure(sslSocket, true);
      connectTls(sslSocket, connectionSpec);
      eventListener.secureConnectEnd(call, handshake);
    } else {
      socket = rawSocket;
      protocol =
        PlatformJdk.get().isH2ConnectionAllowed(route.address().url().host(), route.address().protocols()) ?
        Protocol.H2_PRIOR_KNOWLEDGE :
        Protocol.HTTP_1_1;
    }

    if (protocol == Protocol.HTTP_2 || protocol == Protocol.H2_PRIOR_KNOWLEDGE) {
      startHttp2(pingIntervalMillis);
    }

    eventListener.connectEnd(call, route.socketAddress(), route.proxy(), protocol);
    return new ConnectAndEquipResult();
  }

  private ConnectAndEquipResult connectTunnel(int readTimeout, int writeTimeout, Call call,
    EquipPlan equipPlan, EventListener eventListener) throws IOException {
    if (equipPlan.attempt >= MAX_TUNNEL_ATTEMPTS) {
      throw new ProtocolException(
        "Too many tunnel connections attempted: " + MAX_TUNNEL_ATTEMPTS);
    }

    if (equipPlan.tunnelRequest == null) {
      return new ConnectAndEquipResult();
    }

    rawSocket.closeQuietly();
    rawSocket = null;
    sink.closeQuietly();
    sink = null;
    source.closeQuietly();
    source = null;

    equipPlan = equipPlan.nextConnectionSpec(route.address().connectionSpecs(), null);

    eventListener.connectEnd(call, route.socketAddress(), route.proxy(), null);

    return new ConnectAndEquipResult(null, equipPlan);
  }

  private void startHttp2(int pingIntervalMillis) throws IOException {
    Socket socket = this.socket;
    BufferedSource source = this.source;
    BufferedSink sink = this.sink;

    socket.setSoTimeout(0);

    Http2Connection.Builder builder = new Http2Connection.Builder(true, taskRunner);
    builder.socket(socket, route.address().url().host(), source, sink);
    builder.listener(this);
    builder.pingIntervalMillis(pingIntervalMillis);
    http2Connection = builder.build();
    http2Connection.start();

    this.allocationLimit = Http2Connection.DEFAULT_SETTINGS.getMaxConcurrentStreams();
  }

  private Request createTunnel(int readTimeout, int writeTimeout, Request tunnelRequest, HttpUrl url) throws IOException {
    Request nextRequest = tunnelRequest;
    String requestLine = "CONNECT " + url.toHostHeader(true) + " HTTP/1.1";
    FunctionWithThrows < Http1ExchangeCodec, Void, IOException > writeRequest = (tunnelCodec) -> {
      tunnelCodec.writeRequest(nextRequest.headers(), requestLine);
      tunnelCodec.finishRequest();
      return null;
    };
    while (true) {
      OkHttpClient client = new OkHttpClient.Builder()
        .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
        .writeTimeout(writeTimeout, TimeUnit.MILLISECONDS)
        .build();
      Http1ExchangeCodec tunnelCodec = new Http1ExchangeCodec(null, client, null, null);
      Source source = tunnelCodec.source();
      BufferedSource bufferedSource = Okio.buffer(source);
      BufferedSink sink = Okio.buffer(tunnelCodec.sink());
      Timeout sourceTimeout = bufferedSource.timeout();
      Timeout sinkTimeout = sink.timeout();
      sourceTimeout.timeout(readTimeout, TimeUnit.MILLISECONDS);
      sinkTimeout.timeout(writeTimeout, TimeUnit.MILLISECONDS);
      writeRequest.apply(tunnelCodec);
      Response response = tunnelCodec.readResponseHeaders(false);
      nextRequest = response.request().newBuilder().build();
      ResponseBody responseBody = response.body();
      if (responseBody != null) {
        responseBody.close();
      }
      tunnelCodec.skipConnectBody(response);

      int responseCode = response.code();
      if (responseCode == HTTP_OK) {
        Buffer sourceBuffer = bufferedSource.buffer();
        Buffer sinkBuffer = sink.buffer();
        if (!sourceBuffer.exhausted() || !sinkBuffer.exhausted()) {
          throw new IOException("TLS tunnel buffered too many bytes!");
        }
        return null;
      } else if (responseCode == HTTP_PROXY_AUTH) {
        if ("close".equalsIgnoreCase(response.header("Connection"))) {
          return nextRequest;
        }
        // TODO: implement proxy authentication
        throw new IOException("Failed to authenticate with proxy");
      } else {
        throw new IOException("Unexpected response code for CONNECT: " + responseCode);
      }
    }


  private Request createTunnelRequest() {
    Request.Builder builder = new Request.Builder();
    builder.url(route.address().url());
    builder.method("CONNECT", null);
    builder.header("Host", route.address().url().toHostHeader(true));
    builder.header("Proxy-Connection", "Keep-Alive");
    builder.header("User-Agent", UserAgent.get());
    return builder.build();
  }

  private boolean isEligible(Address address, List routes) {
    if (calls.size() >= allocationLimit || noNewExchanges) {
      return false;
    }

    if (!route.address().equalsNonHost(address)) {
      return false;
    }

    if (address.url().host().equals(route().address().url().host())) {
      return true; // This connection is a perfect match.
    }

    if (http2Connection == null) {
      return false;
    }

    if (routes == null || !routeMatchesAny(routes)) {
      return false;
    }

    if (address.hostnameVerifier() != OkHostnameVerifier.INSTANCE) {
      return false;
    }
    if (!supportsUrl(address.url())) {
      return false;
    }

    try {
      address.certificatePinner().check(address.url().host(), handshake().peerCertificates());
    } catch (SSLPeerUnverifiedException e) {
      return false;
    }

    return true;
  }

  private boolean routeMatchesAny(List candidates) {
    return candidates.stream().anyMatch(
      route::matches);
  }

  private boolean supportsUrl(HttpUrl url) {
    if (!url.host().equals(route().address().url().host())) {
      return false;
    }

    if (url.port() != route().address().url().port()) {
      return false;
    }

    return true;
  }

  private boolean certificateSupportHost(HttpUrl url, Handshake handshake) {
        X509Certificate[] peerCertificates = handshake.peerCertificates();

        if (peerCertificates.length == 0) {
            return false;
        }

        return OkHostnameVerifier.verify(url.host(), peerCertificates[0]);
    }

  private ExchangeCodec newCodec(OkHttpClient client, RealInterceptorChain chain) throws SocketException {
    Socket socket = this.socket;
    BufferedSource source = this.source;
    BufferedSink sink = this.sink;
    Http2Connection http2Connection = this.http2Connection;

    if (http2Connection != null) {
      return new Http2ExchangeCodec(client, this, chain, http2Connection);
    }

    socket.setSoTimeout(chain.readTimeoutMillis());
    source.timeout().timeout(chain.readTimeoutMillis(), TimeUnit.MILLISECONDS);
    sink.timeout().timeout(chain.writeTimeoutMillis(), TimeUnit.MILLISECONDS);
    return new Http1ExchangeCodec(client, this, source, sink);
  }

  private RealWebSocket.Streams newWebSocketStreams(Exchange exchange) {
    Socket socket = this.socket;
    BufferedSource source = this.source;
    BufferedSink sink = this.sink;

    socket.setSoTimeout(0);
    noNewExchanges();

    return new RealWebSocket.Streams(true, source, sink) {
      @Override
      public void close() {
        exchange.bodyComplete(null, responseDone, requestDone, null);
      }
    };
  }

  @Override
  public Socket socket() {
    return socket;
  }

  @Override
  public synchronized void cancel() {
    rawSocket.closeQuietly();
  }

  @Override
  public synchronized boolean isHealthy(SocketPolicy policy) {
    long nowNs = System.nanoTime();

    if (rawSocket.isClosed() || socket.isClosed() || socket.isInputShutdown() ||
      socket.isOutputShutdown()) {
      return false;
    }

    if (http2Connection != null) {
      return http2Connection.isHealthy(nowNs);
    }

    long idleDurationNs = nowNs - idleAtNs;
    if (idleDurationNs >= IDLE_CONNECTION_HEALTHY_NS) {
      if (policy == SocketPolicy.OMIT_CLOSE_AFTER_RESPONSE_BODY) {
        return socket.isHealthy(source);
      }
    }

    return true;
  }

  @Override
  public synchronized void onStream(Http2Stream stream) {
    stream.close(ErrorCode.REFUSED_STREAM, null);
  }

  @Synchronized
  public synchronized void onSettings(Http2Connection connection, Settings settings) {
    allocationLimit = settings.getMaxConcurrentStreams();
  }

  @Override
  public Handshake handshake() {
    return handshake;
  }

  public synchronized void noNewExchanges() {
    noNewExchanges = true;
  }

  public synchronized void noCoalescedConnections() {
    noCoalescedConnections = true;
  }
sink
  public synchronized void incrementSuccessCount() {
    successCount++;
  }

  public void connectFailed(OkHttpClient client, Route failedRoute, IOException failure) {
    Proxy.Type proxyType = failedRoute.proxy().type();
    if (proxyType != Proxy.Type.DIRECT) {
        Address address = failedRoute.address();
        address.proxySelector().connectFailed(
                address.url().toURI(), failedRoute.proxy().address(), failure
        );
    }

    client.routeDatabase().failed(failedRoute);
}

  public synchronized void trackFailure(RealCall call, IOException e) {
    if (e instanceof StreamResetException) {
      StreamResetException streamResetException = (StreamResetException) e;
      if (streamResetException.errorCode() == ErrorCode.REFUSED_STREAM) {
        refusedStreamCount++;
        if (refusedStreamCount > 1) {
          noNewExchanges = true;
          routeFailureCount++;
        }
      } else if (streamResetException.errorCode() == ErrorCode.CANCEL && call.isCanceled()) {} else {
        noNewExchanges = true;
        routeFailureCount++;
      }
    } else if (!isMultiplexed || e instanceof ConnectionShutdownException) {
      noNewExchanges = true;

      if (successCount == 0) {
        connectFailed(call.client(), route, e);
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
    return "Connection{" +
      "route=" + route +
      ", rawSocket=" + rawSocket +
      ", socket=" + socket +
      ", handshake=" + handshake +
      ", protocol=" + protocol +
      ", http2Connection=" + http2Connection +
      ", source=" + source +
      ", sink=" + sink +
      ", noNewExchanges=" + noNewExchanges +
      ", noCoalescedConnections=" + noCoalescedConnections +
      ", routeFailureCount=" + routeFailureCount +
      ", successCount=" + successCount +
      ", refusedStreamCount=" + refusedStreamCount +
      ", allocationLimit=" + allocationLimit +
      ", idleAtNs=" + idleAtNs +
      '}';
  }

  public static RealConnection newTestConnection(TaskRunner taskRunner,
    RealConnectionPool connectionPool, Route route, Socket socket, long idleAtNs) {
    RealConnection result = new RealConnection(taskRunner, connectionPool, route);
    result.socket = socket;
    result.idleAtNs = idleAtNs;
    return result;
  }
}

class ConnectAndEquipResult {
  public IOException failure;
  public EquipPlan nextPlan;

  ConnectAndEquipResult(IOException failure, EquipPlan nextPlan) {
    this.failure = failure;
    this.nextPlan = nextPlan;
  }

  ConnectAndEquipResult() {
    this(null, null);
  }
}

