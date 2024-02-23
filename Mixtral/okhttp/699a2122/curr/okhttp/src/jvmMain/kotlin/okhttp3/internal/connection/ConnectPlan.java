

package okhttp3.internal.connection;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownServiceException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.List;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import okhttp3.CertificatePinner;
import okhttp3.ConnectionSpec;
import okhttp3.Handshake;
import okhttp3.Handshake.Handler;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Route;
import okhttp3.internal.closeQuietly;
import okhttp3.internal.connection.RoutePlanner.Plan;
import okhttp3.internal.http.ExchangeCodec;
import okhttp3.internal.http1.Http1ExchangeCodec;
import okhttp3.internal.platform.Platform;
import okhttp3.internal.tls.OkHostnameVerifier;
import okhttp3.internal.toHostHeader;
import okhttp3.tls.ConnectionSpecSelector;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Buffer;
import okio.Okio;

class ConnectPlan implements Plan, ExchangeCodec.Carrier {
  private final OkHttpClient client;
  private final RealCall call;
  private final RealRoutePlanner routePlanner;

  private final Route route;
  private final List<Route> routes;
  private final int attempt;
  private final Request tunnelRequest;
  private final int connectionSpecIndex;
  private final boolean isTlsFallback;

  private final EventListener eventListener;

  private Socket rawSocket;
  private Socket socket;
  private Handshake handshake;
  private Protocol protocol;
  private BufferedSource source;
  private BufferedSink sink;
  private RealConnection connection;

  ConnectPlan(
      OkHttpClient client,
      RealCall call,
      RealRoutePlanner routePlanner,
      Route route,
      List<Route> routes,
      int attempt,
      Request tunnelRequest,
      int connectionSpecIndex,
      boolean isTlsFallback) {
    this.client = client;
    this.call = call;
    this.routePlanner = routePlanner;
    this.route = route;
    this.routes = routes;
    this.attempt = attempt;
    this.tunnelRequest = tunnelRequest;
    this.connectionSpecIndex = connectionSpecIndex;
    this.isTlsFallback = isTlsFallback;
    this.eventListener = call.eventListener;
  }

  @Override
  public boolean isConnected() {
    return protocol != null;
  }

  private ConnectPlan copy(int attempt, Request tunnelRequest, int connectionSpecIndex, boolean isTlsFallback) {
    return new ConnectPlan(
        client, call, routePlanner, route, routes, attempt, tunnelRequest, connectionSpecIndex, isTlsFallback);
  }

  @Override
  public ConnectResult connect() throws IOException {
    if (isConnected) throw new IllegalStateException("already connected");

    List<ConnectionSpec> connectionSpecs = route.getAddress().connectionSpecs();
    ConnectPlan retryTlsConnection = null;
    boolean success = false;

    call.plansToCancel.add(this);
    try {
      eventListener.connectStart(call, route.socketAddress(), route.proxy());
      connectSocket();

      if (tunnelRequest != null) {
        ConnectResult tunnelResult = connectTunnel();

        if (tunnelResult.nextPlan != null || tunnelResult.throwable != null) {
          return tunnelResult;
        }
      }

      if (route.getAddress().sslSocketFactory() != null) {
        eventListener.secureConnectStart(call);

        SSLSocket sslSocket = (SSLSocket) route.getAddress().sslSocketFactory().createSocket(
            rawSocket,
            route.getAddress().url().host(),
            route.getAddress().url().port(),
            true);

        ConnectPlan tlsEquipPlan = planWithCurrentOrInitialConnectionSpec(connectionSpecs, sslSocket);
        ConnectionSpec connectionSpec = connectionSpecs.get(tlsEquipPlan.connectionSpecIndex);

        retryTlsConnection = tlsEquipPlan.nextConnectionSpec(connectionSpecs, sslSocket);

        connectionSpec.configureSocket(sslSocket, isFallback(tlsEquipPlan));
        connectTls(sslSocket, connectionSpec);
        eventListener.secureConnectEnd(call, handshake);
      } else {
        socket = rawSocket;
        protocol = (Protocol.H2_PRIOR_KNOWLEDGE.isInList(route.getAddress().protocols())
            ? Protocol.H2_PRIOR_KNOWLEDGE
            : Protocol.HTTP_1_1);
      }

      RealConnection connection = new RealConnection(
          client.taskRunner(),
          client.connectionPool().delegate(),
          route,
          rawSocket,
          socket,
          handshake,
          protocol,
          source,
          sink,
          client.pingIntervalMillis());
      this.connection = connection;
      connection.start();

      eventListener.connectEnd(call, route.socketAddress(), route.proxy(), protocol);
      success = true;
      return new ConnectResult(this);
    } catch (IOException e) {
      eventListener.connectFailed(call, route.socketAddress(), route.proxy(), null, e);

      if (!client.retryOnConnectionFailure() || !retryTlsHandshake(e)) {
        retryTlsConnection = null;
      }

      return new ConnectResult(this, retryTlsConnection, e);
    } finally {
      call.plansToCancel.remove(this);
      if (!success) {
        closeQuietly(socket);
        closeQuietly(rawSocket);
      }
    }
  }

  private void connectSocket() throws IOException {
    rawSocket = when (route.proxy().type()) {
      Proxy.Type.DIRECT, Proxy.Type.HTTP -> route.address().socketFactory().createSocket();
      default -> new Socket(route.proxy());
    };

    rawSocket.setSoTimeout(client.readTimeoutMillis());
    try {
      Platform.get().connectSocket(rawSocket, route.socketAddress(), client.connectTimeoutMillis());
    } catch (ConnectException e) {
      throw new ConnectException("Failed to connect to " + route.socketAddress());
    }

    source = Okio.buffer(rawSocket.getSource());
    sink = Okio.buffer(rawSocket.getSink());
  }

  private ConnectResult connectTunnel() throws IOException {
    Request nextTunnelRequest = createTunnel();
    if (nextTunnelRequest == null) return new ConnectResult(this);

    rawSocket.closeQuietly();

    int nextAttempt = attempt + 1;
    return (nextAttempt < MAX_TUNNEL_ATTEMPTS)
        ? new ConnectResult(this, copy(nextAttempt, nextTunnelRequest, connectionSpecIndex, isTlsFallback))
        : new ConnectResult(this, null, new ProtocolException("Too many tunnel connections attempted: " + MAX_TUNNEL_ATTEMPTS));
  }

  private void connectTls(SSLSocket sslSocket, ConnectionSpec connectionSpec) throws IOException {
    okhttp3.Address address = route.address();
    boolean success = false;
    try {
      if (connectionSpec.supportsTlsExtensions()) {
        Platform.get().configureTlsExtensions(sslSocket, address.url().host(), address.protocols());
      }

      sslSocket.startHandshake();

      Handshake unverifiedHandshake = new Handshake(
          sslSocket.getSession().getProtocol(),
          sslSocket.getSession().getCipherSuite(),
          sslSocket.getSession().getLocalCertificates());

      if (!address.hostnameVerifier().verify(address.url().host(), sslSocket.getSession())) {
        X509Certificate cert = (X509Certificate) unverifiedHandshake.peerCertificates().get(0);
        throw new SSLPeerUnverifiedException(
            "Hostname "
                + address.url().host()
                + " not verified:\n"
                + "    certificate: "
                + CertificatePinner.pin(cert)
                + "\n"
                + "    DN: "
                + cert.getSubjectDN().getName()
                + "\n"
                + "    subjectAltNames: "
                + OkHostnameVerifier.allSubjectAltNames(cert));
      }

      CertificatePinner certificatePinner = address.certificatePinner();

      handshake = new Handshake(
          unverifiedHandshake.tlsVersion(),
          unverifiedHandshake.cipherSuite(),
          unverifiedHandshake.localCertificates()) {
        @Override
        List<X509Certificate> peerCertificates() {
          return certificatePinner.certificateChainCleaner().clean(
              unverifiedHandshake.peerCertificates(), address.url().host());
        }
      };

      certificatePinner.check(address.url().host(), handshake.peerCertificates());

      String maybeProtocol = (connectionSpec.supportsTlsExtensions())
          ? Platform.get().getSelectedProtocol(sslSocket)
          : null;
      socket = sslSocket;
      source = Okio.buffer(sslSocket.getInputStream());
      sink = Okio.buffer(sslSocket.getOutputStream());
      protocol = (maybeProtocol != null) ? Protocol.get(maybeProtocol) : Protocol.HTTP_1_1;
      success = true;
    } finally {
      Platform.get().afterHandshake(sslSocket);
      if (!success) {
        sslSocket.closeQuietly();
      }
    }
  }

  private Request createTunnel() throws IOException {
    Request nextRequest = tunnelRequest;
    okhttp3.Address address = route.address();
    String requestLine = "CONNECT " + address.toHostHeader(true) + " HTTP/1.1";
    while (true) {
      Buffer sourceBuffer = source.getBuffer();
      Buffer sinkBuffer = sink.getBuffer();
      Http1ExchangeCodec tunnelCodec = new Http1ExchangeCodec(
          null, this, sourceBuffer.getInput(), sinkBuffer.getOutput());
      sourceBuffer.clear();
      sinkBuffer.clear();
      source.timeout().timeout(client.readTimeoutMillis(), TimeUnit.MILLISECONDS);
      sink.timeout().timeout(client.writeTimeoutMillis(), TimeUnit.MILLISECONDS);
      tunnelCodec.writeRequest(nextRequest.headers(), requestLine);
      tunnelCodec.finishRequest();
      okhttp3.Response response = tunnelCodec.readResponseHeaders(false).request(nextRequest).build();
      tunnelCodec.skipConnectBody(response);

      int responseCode = response.code();
      if (responseCode == HttpURLConnection.HTTP_OK) {
        if (!sourceBuffer.exhausted() || !sinkBuffer.exhausted()) {
          throw new IOException("TLS tunnel buffered too many bytes!");
        }
        return null;
      } else if (responseCode == HttpURLConnection.HTTP_PROXY_AUTH) {
        nextRequest = route.address().proxyAuthenticator().authenticate(route, response);
        if (nextRequest == null) throw new IOException("Failed to authenticate with proxy");

        if ("close".equalsIgnoreCase(response.header("Connection"))) {
          return nextRequest;
        }
      } else {
        throw new IOException("Unexpected response code for CONNECT: " + responseCode);
      }
    }
  }

  private ConnectPlan planWithCurrentOrInitialConnectionSpec(
      List<ConnectionSpec> connectionSpecs, SSLSocket sslSocket) {
    if (connectionSpecIndex != -1) return this;
    return nextConnectionSpec(connectionSpecs, sslSocket)
        .orElseThrow(() -> new UnknownServiceException(
            "Unable to find acceptable protocols."
                + " isFallback=" + isTlsFallback
                + ", modes=" + connectionSpecs
                + ", supported protocols=" + Arrays.toString(sslSocket.getEnabledProtocols())));
  }

  private Optional<ConnectPlan> nextConnectionSpec(
      List<ConnectionSpec> connectionSpecs, SSLSocket sslSocket) {
    for (int i = connectionSpecIndex + 1; i < connectionSpecs.size(); i++) {
      ConnectionSpec connectionSpec = connectionSpecs.get(i);
      if (connectionSpec.isCompatible(sslSocket)) {
        return Optional.of(copy(i, isTlsFallback));
      }
    }
    return Optional.empty();
  }

  @Override
  public RealConnection handleSuccess() {
    client.routeDatabase().connected(route);

    RealConnection pooled3 = routePlanner().planReusePooledConnection(this, routes);
    if (pooled3 != null) return pooled3.connection;

    RealConnection connection = this.connection;
    synchronized (connection) {
      client.connectionPool().delegate().put(connection);
      call.acquireConnectionNoEvents(connection);
    }

    eventListener.connectionAcquired(call, connection);
    return connection;
  }

  @Override
  public void trackFailure(RealCall call, IOException e) {

  }

  @Override
  public void noNewExchanges() {

  }

  @Override
  public void cancel() {
    closeQuietly(rawSocket);
  }

  void closeQuietly() {
    closeQuietly(socket);
  }

  
    private static final String NPE_THROW_WITH_NULL = "throw with null exception";
    private static final int MAX_TUNNEL_ATTEMPTS = 21;
}