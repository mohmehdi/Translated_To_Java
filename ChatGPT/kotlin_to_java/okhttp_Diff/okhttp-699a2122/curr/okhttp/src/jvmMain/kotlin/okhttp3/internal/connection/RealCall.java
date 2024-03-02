package okhttp3.internal.connection;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import okhttp3.Address;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.CertificatePinner;
import okhttp3.EventListener;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.assertThreadDoesntHoldLock;
import okhttp3.internal.assertThreadHoldsLock;
import okhttp3.internal.cache.CacheInterceptor;
import okhttp3.internal.closeQuietly;
import okhttp3.internal.http.BridgeInterceptor;
import okhttp3.internal.http.CallServerInterceptor;
import okhttp3.internal.http.Exchange;
import okhttp3.internal.http.RealInterceptorChain;
import okhttp3.internal.http.RetryAndFollowUpInterceptor;
import okhttp3.internal.platform.Platform;
import okio.AsyncTimeout;
import okio.Timeout;

public final class RealCall implements Call {
    final OkHttpClient client;
    final Request originalRequest;
    final boolean forWebSocket;
    private final RealConnectionPool connectionPool;
    private final EventListener eventListener;
    private final AsyncTimeout timeout = new AsyncTimeout() {
        @Override
        protected void timedOut() {
            cancel();
        }
    }.timeout(0, TimeUnit.MILLISECONDS);
    private final AtomicBoolean executed = new AtomicBoolean();
    private Object callStackTrace;
    private RoutePlanner routePlanner;
    private RealConnection connection;
    private boolean timeoutEarlyExit;
    private Exchange interceptorScopedExchange;
    private boolean requestBodyOpen;
    private boolean responseBodyOpen;
    private boolean expectMoreExchanges = true;
    private volatile boolean canceled;
    private volatile Exchange exchange;
    private final List<RoutePlanner.Plan> plansToCancel = new CopyOnWriteArrayList<>();

    RealCall(OkHttpClient client, Request originalRequest, boolean forWebSocket) {
        this.client = client;
        this.originalRequest = originalRequest;
        this.forWebSocket = forWebSocket;
        this.connectionPool = client.connectionPool.delegate;
        this.eventListener = client.eventListenerFactory.create(this);
    }

    @Override
    public Timeout timeout() {
        return timeout;
    }

    @Override
    public Call clone() {
        return new RealCall(client, originalRequest, forWebSocket);
    }

    @Override
    public Request request() {
        return originalRequest;
    }

    @Override
    public void cancel() {
        if (canceled) return;

        canceled = true;
        exchange.cancel();
        for (RoutePlanner.Plan plan : plansToCancel) {
            plan.cancel();
        }

        eventListener.canceled(this);
    }

    @Override
    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public Response execute() throws IOException {
        if (!executed.compareAndSet(false, true)) {
            throw new IllegalStateException("Already Executed");
        }

        timeout.enter();
        callStart();
        try {
            client.dispatcher.executed(this);
            return getResponseWithInterceptorChain();
        } finally {
            client.dispatcher.finished(this);
        }
    }

    @Override
    public void enqueue(Callback responseCallback) {
        if (!executed.compareAndSet(false, true)) {
            throw new IllegalStateException("Already Executed");
        }

        callStart();
        client.dispatcher.enqueue(new AsyncCall(responseCallback));
    }

    @Override
    public boolean isExecuted() {
        return executed.get();
    }

    private void callStart() {
        this.callStackTrace = Platform.get().getStackTraceForCloseable("response.body().close()");
        eventListener.callStart(this);
    }

    private Response getResponseWithInterceptorChain() throws IOException {
        List<Interceptor> interceptors = new CopyOnWriteArrayList<>();
        interceptors.addAll(client.interceptors);
        interceptors.add(new RetryAndFollowUpInterceptor(client));
        interceptors.add(new BridgeInterceptor(client.cookieJar));
        interceptors.add(new CacheInterceptor(client.cache));
        interceptors.add(ConnectInterceptor.INSTANCE);
        if (!forWebSocket) {
            interceptors.addAll(client.networkInterceptors);
        }
        interceptors.add(new CallServerInterceptor(forWebSocket));

        RealInterceptorChain chain = new RealInterceptorChain(
                this,
                interceptors,
                0,
                null,
                originalRequest,
                client.connectTimeoutMillis,
                client.readTimeoutMillis,
                client.writeTimeoutMillis
        );

        boolean calledNoMoreExchanges = false;
        try {
            Response response = chain.proceed(originalRequest);
            if (isCanceled()) {
                response.closeQuietly();
                throw new IOException("Canceled");
            }
            return response;
        } catch (IOException e) {
            calledNoMoreExchanges = true;
            throw noMoreExchanges(e);
        } finally {
            if (!calledNoMoreExchanges) {
                noMoreExchanges(null);
            }
        }
    }

    void enterNetworkInterceptorExchange(Request request, boolean newRoutePlanner, RealInterceptorChain chain) {
        synchronized (this) {
            check(interceptorScopedExchange == null);

            check(!responseBodyOpen,
                    "cannot make a new request because the previous response is still open: " +
                            "please call response.close()");
            check(!requestBodyOpen);
        }

        if (newRoutePlanner) {
            this.routePlanner = new RealRoutePlanner(client, createAddress(request.url), this, chain);
        }
    }

    Exchange initExchange(RealInterceptorChain chain) {
        synchronized (this) {
            check(expectMoreExchanges, "released");
            check(!responseBodyOpen);
            check(!requestBodyOpen);
        }

        RealRoutePlanner routePlanner = (RealRoutePlanner) this.routePlanner;
        RealConnection connection;
        if (client.fastFallback) {
            connection = new FastFallbackExchangeFinder(routePlanner, client.taskRunner).find();
        } else {
            connection = new ExchangeFinder(routePlanner).find();
        }
        Exchange result = new Exchange(this, eventListener, routePlanner, connection.newCodec(client, chain));
        this.interceptorScopedExchange = result;
        this.exchange = result;
        synchronized (this) {
            this.requestBodyOpen = true;
            this.responseBodyOpen = true;
        }

        if (canceled) {
            throw new IOException("Canceled");
        }
        return result;
    }

    void acquireConnectionNoEvents(RealConnection connection) {
        connection.assertThreadHoldsLock();

        check(this.connection == null);
        this.connection = connection;
        connection.calls.add(new CallReference(this, callStackTrace));
    }

    <E extends IOException> E messageDone(Exchange exchange, boolean requestDone, boolean responseDone, E e) {
        if (exchange != this.exchange) {
            return e;
        }

        boolean bothStreamsDone = false;
        boolean callDone = false;
        synchronized (this) {
            if ((requestDone && requestBodyOpen) || (responseDone && responseBodyOpen)) {
                if (requestDone) requestBodyOpen = false;
                if (responseDone) responseBodyOpen = false;
                bothStreamsDone = !requestBodyOpen && !responseBodyOpen;
                callDone = !requestBodyOpen && !responseBodyOpen && !expectMoreExchanges;
            }
        }

        if (bothStreamsDone) {
            this.exchange = null;
            this.connection.incrementSuccessCount();
        }

        if (callDone) {
            return callDone(e);
        }

        return e;
    }

    IOException noMoreExchanges(IOException e) {
        boolean callDone = false;
        synchronized (this) {
            if (expectMoreExchanges) {
                expectMoreExchanges = false;
                callDone = !requestBodyOpen && !responseBodyOpen;
            }
        }

        if (callDone) {
            return callDone(e);
        }

        return e;
    }

    private <E extends IOException> E callDone(E e) {
        assertThreadDoesntHoldLock();

        RealConnection connection = this.connection;
        if (connection != null) {
            connection.assertThreadDoesntHoldLock();
            Socket toClose;
            synchronized (connection) {
                toClose = releaseConnectionNoEvents();
            }
            if (this.connection == null) {
                closeQuietly(toClose);
                eventListener.connectionReleased(this, connection);
            } else {
                check(toClose == null);
            }
        }

        E result = timeoutExit(e);
        if (e != null) {
            eventListener.callFailed(this, result);
        } else {
            eventListener.callEnd(this);
        }
        return result;
    }

    Socket releaseConnectionNoEvents() {
        RealConnection connection = this.connection;
        connection.assertThreadHoldsLock();

        List<CallReference> calls = connection.calls;
        int index = indexOf(calls, this);
        check(index != -1);

        calls.remove(index);
        this.connection = null;

        if (calls.isEmpty()) {
            connection.idleAtNs = System.nanoTime();
            if (connectionPool.connectionBecameIdle(connection)) {
                return connection.socket();
            }
        }

        return null;
    }

    private <E extends IOException> E timeoutExit(E cause) {
        if (timeoutEarlyExit) return cause;
        if (!timeout.exit()) return cause;

        InterruptedIOException e = new InterruptedIOException("timeout");
        if (cause != null) e.initCause(cause);
        return (E) e;
    }

    void timeoutEarlyExit() {
        check(!timeoutEarlyExit);
        timeoutEarlyExit = true;
        timeout.exit();
    }

    void exitNetworkInterceptorExchange(boolean closeExchange) {
        synchronized (this) {
            check(expectMoreExchanges, "released");
        }

        if (closeExchange) {
            exchange.detachWithViolence();
        }

        interceptorScopedExchange = null;
    }

    private String createAddress(HttpUrl url) {
        SSLSocketFactory sslSocketFactory = null;
        HostnameVerifier hostnameVerifier = null;
        CertificatePinner certificatePinner = null;
        if (url.isHttps()) {
            sslSocketFactory = client.sslSocketFactory;
            hostnameVerifier = client.hostnameVerifier;
            certificatePinner = client.certificatePinner;
        }

        return new Address.Builder()
                .uriHost(url.host())
                .uriPort(url.port())
                .dns(client.dns)
                .socketFactory(client.socketFactory)
                .sslSocketFactory(sslSocketFactory)
                .hostnameVerifier(hostnameVerifier)
                .certificatePinner(certificatePinner)
                .proxyAuthenticator(client.proxyAuthenticator)
                .proxy(client.proxy)
                .protocols(client.protocols)
                .connectionSpecs(client.connectionSpecs)
                .proxySelector(client.proxySelector)
                .build();
    }

    boolean retryAfterFailure() {
        return routePlanner.hasFailure() && routePlanner.hasMoreRoutes();
    }

    private String toLoggableString() {
        return (isCanceled() ? "canceled " : "") +
                (forWebSocket ? "web socket" : "call") +
                " to " + redactedUrl();
    }

    String redactedUrl() {
        return originalRequest.url().redact();
    }

    final class AsyncCall implements Runnable {
        private final Callback responseCallback;
        private AtomicInteger callsPerHost = new AtomicInteger(0);

        AsyncCall(Callback responseCallback) {
            this.responseCallback = responseCallback;
        }

        void reuseCallsPerHostFrom(AsyncCall other) {
            this.callsPerHost = other.callsPerHost;
        }

        String host() {
            return originalRequest.url().host();
        }

        Request request() {
            return originalRequest;
        }

        RealCall call() {
            return RealCall.this;
        }

        void executeOn(ExecutorService executorService) {
            client.dispatcher.assertThreadDoesntHoldLock();

            boolean success = false;
            try {
                executorService.execute(this);
                success = true;
            } catch (RejectedExecutionException e) {
                IOException ioException = new InterruptedIOException("executor rejected");
                ioException.initCause(e);
                noMoreExchanges(ioException);
                responseCallback.onFailure(RealCall.this, ioException);
            } finally {
                if (!success) {
                    client.dispatcher.finished(AsyncCall.this);
                }
            }
        }

        @Override
        public void run() {
            threadName("OkHttp " + redactedUrl()) {
                boolean signalledCallback = false;
                timeout.enter();
                try {
                    Response response = getResponseWithInterceptorChain();
                    signalledCallback = true;
                    responseCallback.onResponse(RealCall.this, response);
                } catch (IOException e) {
                    if (signalledCallback) {
                        Platform.get().log("Callback failure for " + toLoggableString(), Platform.INFO, e);
                    } else {
                        responseCallback.onFailure(RealCall.this, e);
                    }
                } catch (Throwable t) {
                    cancel();
                    if (!signalledCallback) {
                        IOException canceledException = new IOException("canceled due to " + t);
                        canceledException.addSuppressed(t);
                        responseCallback.onFailure(RealCall.this, canceledException);
                    }
                    throw t;
                } finally {
                    client.dispatcher.finished(AsyncCall.this);
                }
            }
        }
    }

    static final class CallReference extends WeakReference<RealCall> {
        final Object callStackTrace;

        CallReference(RealCall referent, Object callStackTrace) {
            super(referent);
            this.callStackTrace = callStackTrace;
        }
    }
}
