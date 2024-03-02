package okhttp3.internal.connection;

import okhttp3.*;
import okhttp3.internal.assertThreadDoesntHoldLock;
import okhttp3.internal.assertThreadHoldsLock;
import okhttp3.internal.concurrent.TaskRunner;
import okhttp3.internal.http.ExchangeCodec;
import okhttp3.internal.http.RealInterceptorChain;
import okhttp3.internal.http1.Http1ExchangeCodec;
import okhttp3.internal.http2.*;
import okhttp3.internal.tls.OkHostnameVerifier;
import okhttp3.internal.ws.RealWebSocket;
import okio.BufferedSink;
import okio.BufferedSource;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.IOException;
import java.lang.ref.Reference;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public final class RealConnection implements Connection, ExchangeCodec.Carrier {
    private static final long IDLE_CONNECTION_HEALTHY_NS = 10_000_000_000L;

    private final TaskRunner taskRunner;
    private final RealConnectionPool connectionPool;
    private final Route route;
    private Socket rawSocket;
    private Socket socket;
    private Handshake handshake;
    private Protocol protocol;
    private BufferedSource source;
    private BufferedSink sink;
    private final int pingIntervalMillis;
    private Http2Connection http2Connection;
    private volatile boolean noNewExchanges = false;
    private volatile boolean noCoalescedConnections = false;
    private int routeFailureCount = 0;
    private int successCount = 0;
    private int refusedStreamCount = 0;
    private int allocationLimit = 1;
    private final List<Reference<RealCall>> calls = new java.util.ArrayList<>();
    private long idleAtNs = Long.MAX_VALUE;

    RealConnection(
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

    boolean isMultiplexed() {
        return http2Connection != null;
    }

    @Synchronized
    void noNewExchanges() {
        noNewExchanges = true;
    }

    @Synchronized
    void noCoalescedConnections() {
        noCoalescedConnections = true;
    }

    @Synchronized
    void incrementSuccessCount() {
        successCount++;
    }

    void start() throws IOException {
        idleAtNs = System.nanoTime();
        if (protocol == Protocol.HTTP_2 || protocol == Protocol.H2_PRIOR_KNOWLEDGE) {
            startHttp2();
        }
    }

    private void startHttp2() throws IOException {
        Socket socket = this.socket;
        BufferedSource source = this.source;
        BufferedSink sink = this.sink;
        if (socket == null || source == null || sink == null) {
            return;
        }
        socket.setSoTimeout(0);
        http2Connection = new Http2Connection.Builder(true, taskRunner)
                .socket(socket, route.address.url.host(), source, sink)
                .listener(this)
                .pingIntervalMillis(pingIntervalMillis)
                .build();
        allocationLimit = Http2Connection.DEFAULT_SETTINGS.getMaxConcurrentStreams();
        http2Connection.start();
    }

    boolean isEligible(Address address, List<Route> routes) {
        assertThreadHoldsLock();
        if (calls.size() >= allocationLimit || noNewExchanges) {
            return false;
        }
        if (!route.address.equalsNonHost(address)) {
            return false;
        }
        if (address.url.host().equals(route.address.url.host())) {
            return true;
        }
        if (http2Connection == null) {
            return false;
        }
        if (routes == null || !routeMatchesAny(routes)) {
            return false;
        }
        if (address.hostnameVerifier != OkHostnameVerifier) {
            return false;
        }
        return supportsUrl(address.url) && checkCertificatePinning(address);
    }

    private boolean routeMatchesAny(List<Route> candidates) {
        return candidates.stream()
                .anyMatch(candidate -> candidate.proxy().type() == Proxy.Type.DIRECT &&
                        route.proxy().type() == Proxy.Type.DIRECT &&
                        route.socketAddress().equals(candidate.socketAddress()));
    }

    private boolean supportsUrl(HttpUrl url) {
        assertThreadHoldsLock();
        HttpUrl routeUrl = route.address.url();
        if (url.port() != routeUrl.port()) {
            return false;
        }
        return url.host().equals(routeUrl.host()) || (!noCoalescedConnections && handshake != null &&
                certificateSupportHost(url, handshake));
    }

    private boolean certificateSupportHost(HttpUrl url, Handshake handshake) {
        List<X509Certificate> peerCertificates = handshake.peerCertificates();
        return !peerCertificates.isEmpty() &&
                OkHostnameVerifier.verify(url.host(), peerCertificates.get(0));
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

    @Override
    public void cancel() {
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
        if (rawSocket.isClosed() || socket.isClosed() || socket.isInputShutdown() ||
                socket.isOutputShutdown()) {
            return false;
        }
        Http2Connection http2Connection = this.http2Connection;
        if (http2Connection != null) {
            return http2Connection.isHealthy(nowNs);
        }
        long idleDurationNs = synchronized (this) -> nowNs - idleAtNs;
        if (idleDurationNs >= IDLE_CONNECTION_HEALTHY_NS && doExtensiveChecks) {
            return socket.isHealthy(source);
        }
        return true;
    }

    @Override
    public void onStream(Http2Stream stream) {
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
            if (resetException.errorCode() == ErrorCode.REFUSED_STREAM) {
                refusedStreamCount++;
                if (refusedStreamCount > 1) {
                    noNewExchanges = true;
                    routeFailureCount++;
                }
            } else if (resetException.errorCode() == ErrorCode.CANCEL && call.isCanceled()) {
                // Do nothing for canceled streams.
            } else {
                noNewExchanges = true;
                routeFailureCount++;
            }
        } else if (!isMultiplexed() || e instanceof ConnectionShutdownException) {
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
        return "Connection{" +
                route.address().url().host() + ":" + route.address().url().port() +
                ", proxy=" + route.proxy() +
                ", hostAddress=" + route.socketAddress() +
                ", cipherSuite=" + (handshake != null ? handshake.cipherSuite() : "none") +
                ", protocol=" + protocol +
                "}";
    }

    static RealConnection newTestConnection(
            TaskRunner taskRunner,
            RealConnectionPool connectionPool,
            Route route,
            Socket socket,
            long idleAtNs) {
        RealConnection result = new RealConnection(
                taskRunner, connectionPool, route, null, socket, null, null, null, null, 0);
        result.idleAtNs = idleAtNs;
        return result;
    }
}
