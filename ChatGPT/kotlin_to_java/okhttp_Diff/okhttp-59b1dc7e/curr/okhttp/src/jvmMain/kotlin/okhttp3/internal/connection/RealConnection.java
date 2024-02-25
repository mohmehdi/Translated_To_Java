package okhttp3.internal.connection;

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

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;

import okhttp3.Address;
import okhttp3.Call;
import okhttp3.CertificatePinner;
import okhttp3.Connection;
import okhttp3.ConnectionSpec;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.internal.EmptyResponse;
import okhttp3.internal.Util;
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
import okhttp3.internal.tls.OkHostnameVerifier;
import okhttp3.internal.ws.RealWebSocket;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Sink;
import okio.Source;

public class RealConnection extends Http2Connection.Listener implements Connection {
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

    private final List<Reference<RealCall>> calls = Util.newMutableList();

    private long idleAtNs = Long.MAX_VALUE;

    public RealConnection(TaskRunner taskRunner, RealConnectionPool connectionPool, Route route) {
        this.taskRunner = taskRunner;
        this.connectionPool = connectionPool;
        this.route = route;
    }

    public void noNewExchanges() {
        synchronized (this) {
            noNewExchanges = true;
        }
    }

    public void noCoalescedConnections() {
        synchronized (this) {
            noCoalescedConnections = true;
        }
    }

    public void incrementSuccessCount() {
        synchronized (this) {
            successCount++;
        }
    }

    public void connect(int connectTimeout, int readTimeout, int writeTimeout, int pingIntervalMillis,
                        boolean connectionRetryEnabled, Call call, EventListener eventListener) {
        if (isNew()) {
            throw new IllegalStateException("already connected");
        }

        RouteException routeException = null;
        List<ConnectionSpec> connectionSpecs = route.address.connectionSpecs();
        ConnectionSpecSelector connectionSpecSelector = new ConnectionSpecSelector(connectionSpecs);

        if (route.address().sslSocketFactory() == null) {
            if (!connectionSpecs.contains(ConnectionSpec.CLEARTEXT)) {
                throw new RouteException(
                        new UnknownServiceException("CLEARTEXT communication not enabled for client"));
            }
            String host = route.address().url().host();
            if (!Platform.get().isCleartextTrafficPermitted(host)) {
                throw new RouteException(
                        new UnknownServiceException("CLEARTEXT communication to " + host +
                                " not permitted by network security policy"));
            }
        } else {
            if (route.address().protocols().contains(Protocol.H2_PRIOR_KNOWLEDGE)) {
                throw new RouteException(
                        new UnknownServiceException("H2_PRIOR_KNOWLEDGE cannot be used with HTTPS"));
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
                eventListener.connectEnd(call, route.socketAddress(), route.proxy(), protocol);
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

                eventListener.connectFailed(call, route.socketAddress(), route.proxy(), null, e);

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
                    new ProtocolException("Too many tunnel connections attempted: " + MAX_TUNNEL_ATTEMPTS));
        }

        idleAtNs = System.nanoTime();
    }

    private void connectTunnel(int connectTimeout, int readTimeout, int writeTimeout, Call call,
                               EventListener eventListener) throws IOException {
        Request tunnelRequest = createTunnelRequest();
        HttpUrl url = tunnelRequest.url();
        for (int i = 0; i < MAX_TUNNEL_ATTEMPTS; i++) {
            connectSocket(connectTimeout, readTimeout, call, eventListener);
            tunnelRequest = createTunnel(readTimeout, writeTimeout, tunnelRequest, url);
            if (tunnelRequest == null) {
                break;
            }
            closeQuietly(rawSocket);
            rawSocket = null;
            sink = null;
            source = null;
            eventListener.connectEnd(call, route.socketAddress(), route.proxy(), null);
        }
    }

    private void connectSocket(int connectTimeout, int readTimeout, Call call, EventListener eventListener)
            throws IOException {
        Proxy proxy = route.proxy();
        Address address = route.address();

        Socket rawSocket;
        if (proxy.type() == Proxy.Type.DIRECT || proxy.type() == Proxy.Type.HTTP) {
            rawSocket = address.socketFactory().createSocket();
        } else {
            rawSocket = new Socket(proxy);
        }
        this.rawSocket = rawSocket;

        eventListener.connectStart(call, route.socketAddress(), proxy);
        rawSocket.setSoTimeout(readTimeout);
        try {
            Platform.get().connectSocket(rawSocket, route.socketAddress(), connectTimeout);
        } catch (ConnectException e) {
            throw new ConnectException("Failed to connect to " + route.socketAddress()).initCause(e);
        }

        try {
            source = Okio.buffer(Okio.source(raw

Socket));
            sink = Okio.buffer(Okio.sink(rawSocket));
        } catch (NullPointerException npe) {
            if (npe.getMessage().equals(NPE_THROW_WITH_NULL)) {
                throw new IOException(npe);
            }
        }
    }

    private void establishProtocol(ConnectionSpecSelector connectionSpecSelector, int pingIntervalMillis,
                                   Call call, EventListener eventListener) throws IOException {
        if (route.address().sslSocketFactory() == null) {
            if (route.address().protocols().contains(Protocol.H2_PRIOR_KNOWLEDGE)) {
                socket = rawSocket;
                protocol = Protocol.H2_PRIOR_KNOWLEDGE;
                startHttp2(pingIntervalMillis);
                return;
            }

            socket = rawSocket;
            protocol = Protocol.HTTP_1_1;
            return;
        }

        eventListener.secureConnectStart(call);
        connectTls(connectionSpecSelector);
        eventListener.secureConnectEnd(call, handshake);

        if (protocol == Protocol.HTTP_2) {
            startHttp2(pingIntervalMillis);
        }
    }

    private void startHttp2(int pingIntervalMillis) throws IOException {
        Socket socket = this.socket;
        BufferedSource source = this.source;
        BufferedSink sink = this.sink;

        socket.setSoTimeout(0); // HTTP/2 connection timeouts are set per-stream.
        Http2Connection http2Connection = new Http2Connection.Builder(true, taskRunner)
                .socket(socket, route.address().url().host(), source, sink)
                .listener(this)
                .pingIntervalMillis(pingIntervalMillis)
                .build();
        this.http2Connection = http2Connection;
        this.allocationLimit = Settings.DEFAULT_MAX_CONCURRENT_STREAMS;
        http2Connection.start();
    }

    private void connectTls(ConnectionSpecSelector connectionSpecSelector) throws IOException {
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
            Handshake unverifiedHandshake = Handshake.get(sslSocketSession);

            if (!address.hostnameVerifier().verify(address.url().host(), sslSocketSession)) {
                List<Certificate> peerCertificates = unverifiedHandshake.peerCertificates();
                if (!peerCertificates.isEmpty()) {
                    X509Certificate cert = (X509Certificate) peerCertificates.get(0);
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

            CertificatePinner certificatePinner = address.certificatePinner();

            handshake = new Handshake(
                    unverifiedHandshake.tlsVersion(),
                    unverifiedHandshake.cipherSuite(),
                    unverifiedHandshake.localCertificates(),
                    unverifiedHandshake.peerCertificates(),
                    certificatePinner.certificateChainCleaner().clean(
                            unverifiedHandshake.peerCertificates(), address.url().host())
            );

            certificatePinner.check(address.url().host(), handshake.peerCertificates());

            String maybeProtocol = connectionSpec.supportsTlsExtensions() ?
                    Platform.get().getSelectedProtocol(sslSocket) : null;

            socket = sslSocket;
            source = Okio.buffer(Okio.source(sslSocket));
            sink = Okio.buffer(Okio.sink(sslSocket));
            protocol = (maybeProtocol != null) ? Protocol.get(maybeProtocol) : Protocol.HTTP_1_1;

            success = true;
        } finally {
            if (sslSocket != null) {
                Platform.get().afterHandshake(sslSocket);
            }
            if (!success) {
                closeQuietly(sslSocket);
            }
        }
    }

    private Request createTunnel(int readTimeout, int writeTimeout, Request tunnelRequest, HttpUrl url)
            throws IOException {
        String requestLine = "CONNECT " + url.toHostHeader(true) + " HTTP/1.1";
        while (true) {
            BufferedSource source = this.source;
            BufferedSink sink = this.sink;
            Http1ExchangeCodec tunnelCodec = new Http1ExchangeCodec(null, this, source, sink);
            source.timeout().timeout(readTimeout, TimeUnit.MILLISECONDS);
            sink.timeout().timeout(writeTimeout, TimeUnit.MILLISECONDS);
            tunnelCodec.writeRequest(tunnelRequest.headers(), requestLine);
            tunnelCodec.finishRequest();
            Response response = tunnelCodec.readResponseHeaders(false)
                    .request(tunnelRequest)
                    .build();
            tunnelCodec.skipConnectBody(response);

            switch (response.code()) {
                case HTTP_OK:
                    if (!source.buffer().exhausted() || !sink.buffer().exhausted()) {
                        throw new IOException("TLS tunnel buffered too many bytes!");
                    }
                    return null;
                case HTTP_PROXY_AUTH:
                    tunnelRequest = route.address().proxyAuthenticator()
                            .authenticate(route, response);
                    if (tunnelRequest == null) {
                        return null;
                    }
                    if ("close".equalsIgnoreCase(response.header("Connection"))) {
                        return tunnelRequest;
                    }
                    break;
                default:
                    throw new IOException("Unexpected response code for CONNECT: " + response.code());
            }
        }
    }

    private Request createTunnelRequest() {
        Request proxyConnectRequest = new Request.Builder()
                .url(route.address().url())
                .method("CONNECT", null)
                .header("Host", route.address().url().toHostHeader(true))
                .header("Proxy-Connection", "Keep-Alive") // For HTTP/1.0 proxies like Squid.
                .header("User-Agent", userAgent)
                .build();

        Response fakeAuthChallengeResponse = new Response.Builder()
                .request(proxyConnectRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(HTTP_PROXY_AUTH)
                .message("Preemptive Authenticate")
                .body(EmptyResponse.INSTANCE)
                .sentRequestAtMillis(-1L)
                .receivedResponseAtMillis(-1L)
                .header("Proxy-Authenticate", "OkHttp-Preemptive")
                .build();

        Request authenticatedRequest = route.address().proxyAuthenticator()
                .authenticate(route, fakeAuthChallengeResponse);

        return (authenticatedRequest != null) ? authenticatedRequest : proxyConnectRequest;
    }

    boolean isEligible(Address address, List<Route> routes) {
        assertThreadHoldsLock();

        if (calls.size() >= allocationLimit || noNewExchanges) {
            return false;
        }

        if (!this.route.address().equalsNonHost(address)) {
            return false;
        }

        if (

address.url().host().equals(this.route.address().url().host())) {
            return true;
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
        } catch (SSLPeerUnverifiedException ignored) {
            return false;
        }

        return true;
    }

    private boolean routeMatchesAny(List<Route> candidates) {
        for (Route candidate : candidates) {
            if (candidate.proxy().type() == Proxy.Type.DIRECT &&
                    route.proxy().type() == Proxy.Type.DIRECT &&
                    route.socketAddress().equals(candidate.socketAddress())) {
                return true;
            }
        }
        return false;
    }

    private boolean supportsUrl(HttpUrl url) {
        assertThreadHoldsLock();

        HttpUrl routeUrl = route.address().url();

        if (url.port() != routeUrl.port()) {
            return false; // Port mismatch.
        }

        if (url.host().equals(routeUrl.host())) {
            return true;
        }

        return !noCoalescedConnections && handshake != null && certificateSupportHost(url, handshake);
    }

    private boolean certificateSupportHost(HttpUrl url, Handshake handshake) {
        List<Certificate> peerCertificates = handshake.peerCertificates();
        return !peerCertificates.isEmpty()
                && OkHostnameVerifier.INSTANCE.verify(url.host(), (X509Certificate) peerCertificates.get(0));
    }

    ExchangeCodec newCodec(OkHttpClient client, RealInterceptorChain chain) throws SocketException {
        Socket socket = this.socket;
        BufferedSource source = this.source;
        BufferedSink sink = this.sink;
        Http2Connection http2Connection = this.http2Connection;

        return (http2Connection != null)
                ? new Http2ExchangeCodec(client, this, chain, http2Connection)
                : new Http1ExchangeCodec(client, this, source, sink);
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
        closeQuietly(rawSocket);
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
        if (rawSocket.isClosed() || socket.isClosed() || socket.isInputShutdown()
                || socket.isOutputShutdown()) {
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
    public void onStream(Http2Stream stream) throws IOException {
        stream.close(ErrorCode.REFUSED_STREAM, null);
    }

    @Synchronized
    @Override
    public void onSettings(Http2Connection connection, Settings settings) {
        allocationLimit = settings.getMaxConcurrentStreams();
    }

    @Override
    public Handshake handshake() {
        return handshake;
    }

    void connectFailed(OkHttpClient client, Route failedRoute, IOException failure) {
        if (failedRoute.proxy().type() != Proxy.Type.DIRECT) {
            Address address = failedRoute.address();
            address.proxySelector().connectFailed(
                    address.url().uri(), failedRoute.proxy().address(), failure);
        }

        client.routeDatabase().failed(failedRoute);
    }

    @Synchronized
    void trackFailure(RealCall call, IOException e) {
        if (e instanceof StreamResetException) {
            StreamResetException resetException = (StreamResetException) e;
            if (resetException.errorCode == ErrorCode.REFUSED_STREAM) {
                refusedStreamCount++;
                if (refusedStreamCount > 1) {
                    noNewExchanges = true;
                    routeFailureCount++;
                }
            } else if (resetException.errorCode == ErrorCode.CANCEL && call.isCanceled()) {
                // Permit any number of CANCEL errors on locally-canceled calls.
            } else {
                // Everything else wants a fresh connection.
                noNewExchanges = true;
                routeFailureCount++;
            }
        } else if (!isMultiplexed() || e instanceof ConnectionShutdownException) {
            noNewExchanges = true;

            // If this route hasn't completed a call, avoid it for new connections.
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
        return "Connection{" +
                route.address().url().host() + ":" + route.address().url().port() +
                ", proxy=" + route.proxy() +
                " hostAddress=" + route.socketAddress() +
                " cipherSuite=" + (handshake != null ? handshake.cipherSuite() : "none") +
                " protocol=" + protocol +
                "}";
    }

    private static final String NPE_THROW_WITH_NULL = "throw with null exception";
    private static final int MAX_TUNNEL_ATTEMPTS = 21;
    static final long IDLE_CONNECTION_HEALTHY_NS = 10_000_000_000L; // 10 seconds.
    static RealConnection newTestConnection(
            TaskRunner taskRunner, RealConnectionPool connectionPool, Route route,
            Socket socket, long idleAtNs) {
        RealConnection result = new RealConnection(taskRunner, connectionPool, route);
        result.socket = socket;
        result.idleAtNs = idleAtNs;
        return result;
    }

}