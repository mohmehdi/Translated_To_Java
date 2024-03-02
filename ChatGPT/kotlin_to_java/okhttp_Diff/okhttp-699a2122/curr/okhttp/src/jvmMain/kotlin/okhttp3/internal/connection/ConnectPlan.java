package okhttp3.internal.connection;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.Socket;
import java.net.UnknownServiceException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import okhttp3.CertificatePinner;
import okhttp3.ConnectionSpec;
import okhttp3.Handshake;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.internal.closeQuietly;
import okhttp3.internal.connection.RoutePlanner.ConnectResult;
import okhttp3.internal.http.ExchangeCodec;
import okhttp3.internal.http1.Http1ExchangeCodec;
import okhttp3.internal.platform.Platform;
import okhttp3.internal.tls.OkHostnameVerifier;
import okhttp3.internal.tls.SslSocketUtils;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public class ConnectPlan implements RoutePlanner.Plan, ExchangeCodec.Carrier {
    private final OkHttpClient client;
    private final RealCall call;
    private final RealRoutePlanner routePlanner;
    private final Route route;
    private final List<Route> routes;
    private final int attempt;
    private final Request tunnelRequest;
    private final int connectionSpecIndex;
    private final boolean isTlsFallback;

    private final RealCall.EventListener eventListener = call.eventListener;

    private Socket rawSocket;
    private Socket socket;
    private Handshake handshake;
    private Protocol protocol;
    private BufferedSource source;
    private BufferedSink sink;

    public ConnectPlan(OkHttpClient client, RealCall call, RealRoutePlanner routePlanner, Route route,
                       List<Route> routes, int attempt, Request tunnelRequest, int connectionSpecIndex,
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
    }

    @Override
    public boolean isConnected() {
        return protocol != null;
    }

    private ConnectPlan copy(int attempt, Request tunnelRequest, int connectionSpecIndex, boolean isTlsFallback) {
        return new ConnectPlan(client, call, routePlanner, route, routes, attempt, tunnelRequest,
                connectionSpecIndex, isTlsFallback);
    }

    @Override
    public ConnectResult connect() {
        check(!isConnected(), "already connected");

        List<ConnectionSpec> connectionSpecs = route.address.connectionSpecs;
        ConnectPlan retryTlsConnection = null;
        boolean success = false;

        call.plansToCancel.add(this);
        try {
            eventListener.connectStart(call, route.socketAddress, route.proxy);
            connectSocket();

            if (tunnelRequest != null) {
                ConnectResult tunnelResult = connectTunnel();

                if (tunnelResult.nextPlan != null || tunnelResult.throwable != null) {
                    return tunnelResult;
                }
            }

            if (route.address.sslSocketFactory != null) {
                eventListener.secureConnectStart(call);

                SSLSocket sslSocket = (SSLSocket) route.address.sslSocketFactory.createSocket(
                        rawSocket, route.address.url.host, route.address.url.port, true);

                ConnectPlan tlsEquipPlan = planWithCurrentOrInitialConnectionSpec(connectionSpecs, sslSocket);
                ConnectionSpec connectionSpec = connectionSpecs.get(tlsEquipPlan.connectionSpecIndex);

                retryTlsConnection = tlsEquipPlan.nextConnectionSpec(connectionSpecs, sslSocket);

                connectionSpec.apply(sslSocket, tlsEquipPlan.isTlsFallback);
                connectTls(sslSocket, connectionSpec);
                eventListener.secureConnectEnd(call, handshake);
            } else {
                socket = rawSocket;
                protocol = Protocol.H2_PRIOR_KNOWLEDGE.in(route.address.protocols) ? Protocol.H2_PRIOR_KNOWLEDGE
                        : Protocol.HTTP_1_1;
            }

            RealConnection connection = new RealConnection(
                    client.taskRunner, client.connectionPool.delegate, route, rawSocket, socket, handshake,
                    protocol, source, sink, client.pingIntervalMillis);

            this.connection = connection;
            connection.start();

            eventListener.connectEnd(call, route.socketAddress, route.proxy, protocol);
            success = true;
            return new ConnectResult(this);
        } catch (IOException e) {
            eventListener.connectFailed(call, route.socketAddress, route.proxy, null, e);

            if (!client.retryOnConnectionFailure || !retryTlsHandshake(e)) {
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

    @Override
    public ConnectResult connectTunnel() {
        Request nextTunnelRequest = createTunnel();
        if (nextTunnelRequest == null) {
            return new ConnectResult(this);
        }

        closeQuietly(rawSocket);

        int nextAttempt = attempt + 1;
        if (nextAttempt < MAX_TUNNEL_ATTEMPTS) {
            eventListener.connectEnd(call, route.socketAddress, route.proxy, null);
            return new ConnectResult(this, copy(nextAttempt, nextTunnelRequest));
        } else {
            ProtocolException failure = new ProtocolException("Too many tunnel connections attempted: " + MAX_TUNNEL_ATTEMPTS);
            eventListener.connectFailed(call, route.socketAddress, route.proxy, null, failure);
            return new ConnectResult(this, failure);
        }
    }

    private void connectSocket() throws IOException {
        Socket rawSocket;
        if (route.proxy.type() == Proxy.Type.DIRECT || route.proxy.type() == Proxy.Type.HTTP) {
            rawSocket = route.address.socketFactory.createSocket();
        } else {
            rawSocket = new Socket(route.proxy);
        }

        this.rawSocket = rawSocket;

        rawSocket.setSoTimeout(client.readTimeoutMillis);
        try {
            Platform.get().connectSocket(rawSocket, route.socketAddress, client.connectTimeoutMillis);
        } catch (ConnectException e) {
            throw new ConnectException("Failed to connect to " + route.socketAddress).initCause(e);
        }

        try {
            source = Okio.buffer(Okio.source(rawSocket));
            sink = Okio.buffer(Okio.sink(rawSocket));
        } catch (NullPointerException npe) {
            if (NPE_THROW_WITH_NULL.equals(npe.getMessage())) {
                throw new IOException(npe);
            }
        }
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
            Handshake unverifiedHandshake = SslSocketUtils.toHandshake(sslSocketSession);

            if (!address.hostnameVerifier.verify(address.url.host, sslSocketSession)) {
                X509Certificate cert = (X509Certificate) unverifiedHandshake.peerCertificates.get(0);
                throw new SSLPeerUnverifiedException("Hostname " + address.url.host + " not verified:\n"
                        + "    certificate: " + CertificatePinner.pin(cert) + "\n"
                        + "    DN: " + cert.getSubjectDN().getName() + "\n"
                        + "    subjectAltNames: " + OkHostnameVerifier.allSubjectAltNames(cert));
            }

            CertificatePinner certificatePinner = address.certificatePinner;

            Handshake handshake = new Handshake(
                    unverifiedHandshake.tlsVersion, unverifiedHandshake.cipherSuite,
                    unverifiedHandshake.localCertificates, () ->
                    certificatePinner.certificateChainCleaner.clean(
                            unverifiedHandshake.peerCertificates, address.url.host)
            );
            this.handshake = handshake;

            certificatePinner.check(address.url.host, () -> {
                return handshake.peerCertificates.stream().map(c -> (X509Certificate) c);
            });

            String maybeProtocol = connectionSpec.supportsTlsExtensions ?
                    Platform.get().getSelectedProtocol(sslSocket) : null;
            socket = sslSocket;
            source = Okio.buffer(Okio.source(sslSocket));
            sink = Okio.buffer(Okio.sink(sslSocket));
            protocol = (maybeProtocol != null) ? Protocol.get(maybeProtocol) : Protocol.HTTP_1_1;
            success = true;
        } finally {
            Platform.get().afterHandshake(sslSocket);
            if (!success) {
                closeQuietly(sslSocket);
            }
        }
    }

    private Request createTunnel() throws IOException {
        Request nextRequest = tunnelRequest;

        HttpUrl url = route.address.url;
        String requestLine = "CONNECT " + url.toHostHeader(true) + " HTTP/1.1";
        while (true) {
            BufferedSource source = this.source;
            BufferedSink sink = this.sink;
            Http1ExchangeCodec tunnelCodec = new Http1ExchangeCodec(null, this, source, sink);
            source.timeout().timeout(client.readTimeoutMillis, TimeUnit.MILLISECONDS);
            sink.timeout().timeout(client.writeTimeoutMillis, TimeUnit.MILLISECONDS);
            tunnelCodec.writeRequest(nextRequest.headers, requestLine);
            tunnelCodec.finishRequest();
            Response response = tunnelCodec.readResponseHeaders(false)
                    .request(nextRequest)
                    .build();
            tunnelCodec.skipConnectBody(response);

            switch (response.code) {
                case HttpURLConnection.HTTP_OK:
                    if (!source.buffer.exhausted() || !sink.buffer.exhausted()) {
                        throw new IOException("TLS tunnel buffered too many bytes!");
                    }
                    return null;

                case HttpURLConnection.HTTP_PROXY_AUTH:
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

    private ConnectPlan planWithCurrentOrInitialConnectionSpec(List<ConnectionSpec> connectionSpecs, SSLSocket sslSocket) {
        if (connectionSpecIndex != -1) return this;
        return nextConnectionSpec(connectionSpecs, sslSocket)
                .orElseThrow(() -> new UnknownServiceException(
                        "Unable to find acceptable protocols. isFallback=" + isTlsFallback
                                + ", modes=" + connectionSpecs
                                + ", supported protocols=" + sslSocket.getEnabledProtocols()));
    }

    private Optional<ConnectPlan> nextConnectionSpec(List<ConnectionSpec> connectionSpecs, SSLSocket sslSocket) {
        for (int i = connectionSpecIndex + 1; i < connectionSpecs.size(); i++) {
            if (connectionSpecs.get(i).isCompatible(sslSocket)) {
                return Optional.of(copy(i, isTlsFallback));
            }
        }
        return Optional.empty();
    }

    @Override
    public RealConnection handleSuccess() {
        call.client.routeDatabase.connected(route);

        ConnectPlan pooled = routePlanner.planReusePooledConnection(this, routes);
        if (pooled != null) return pooled.connection;

        RealConnection connection = this.connection;
        synchronized (connection) {
            client.connectionPool.delegate.put(connection);
            call.acquireConnectionNoEvents(connection);
        }

        eventListener.connectionAcquired(call, connection);
        return connection;
    }

    @Override
    public void trackFailure(RealCall call, IOException e) {
        // No implementation provided
    }

    @Override
    public void noNewExchanges() {
        // No implementation provided
    }

    @Override
    public void cancel() {
        closeQuietly(rawSocket);
    }

    public void closeQuietly() {
        closeQuietly(socket);
    }

    private static final String NPE_THROW_WITH_NULL = "throw with null exception";
    private static final int MAX_TUNNEL_ATTEMPTS = 21;
}
