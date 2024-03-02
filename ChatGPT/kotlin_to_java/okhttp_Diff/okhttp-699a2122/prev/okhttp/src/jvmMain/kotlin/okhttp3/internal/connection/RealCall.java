package okhttp3.internal.connection;

import okhttp3.*;
import okhttp3.internal.assertion.*;
import okhttp3.internal.cache.CacheInterceptor;
import okhttp3.internal.connection.RealConnectionPool;
import okhttp3.internal.http.*;
import okhttp3.internal.http.RetryAndFollowUpInterceptor;
import okhttp3.internal.platform.Platform;
import okhttp3.internal.threadName;
import okio.AsyncTimeout;
import okio.Timeout;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;

class RealCall implements Call {
    final OkHttpClient client;
    final Request originalRequest;
    final boolean forWebSocket;
    private final RealConnectionPool connectionPool;
    final EventListener eventListener;

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
    private boolean timeoutEarlyExit = false;

    private Exchange interceptorScopedExchange;

    private boolean requestBodyOpen = false;

    private boolean responseBodyOpen = false;

    private boolean expectMoreExchanges = true;

    private volatile boolean canceled = false;
    private volatile Exchange exchange = null;
    private final CopyOnWriteArrayList<RealConnection> connectionsToCancel = new CopyOnWriteArrayList<>();

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
        if (canceled) return; // Already canceled.

        canceled = true;
        exchange.cancel();
        for (RealConnection connection : connectionsToCancel) {
            connection.cancel();
        }

        eventListener.canceled(this);
    }

    @Override
    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public Response execute() throws IOException {
        check(executed.compareAndSet(false, true), "Already Executed");

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
        check(executed.compareAndSet(false, true), "Already Executed");

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

    @SuppressWarnings("IOException")
    Response getResponseWithInterceptorChain() throws IOException {
        // Build a full stack of interceptors.
        List<Interceptor> interceptors = new ArrayList<>();
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
        check(interceptorScopedExchange == null);

        synchronized (this) {
            check(!responseBodyOpen, "cannot make a new request because the previous response is still open: " +
                    "please call response.close()");
            check(!requestBodyOpen);
        }

        if (newRoutePlanner) {
            this.routePlanner = new RealRoutePlanner(
                    client,
                    createAddress(request.url),
                    this,
                    chain
            );
        }
    }

    Exchange initExchange(RealInterceptorChain chain) {
        synchronized (this) {
            check(expectMoreExchanges, "released");
            check(!responseBodyOpen);
            check(!requestBodyOpen);
        }

        RoutePlanner routePlanner = this.routePlanner;
        RealConnection connection = client.fastFallback
                ? new FastFallbackExchangeFinder(routePlanner, client.taskRunner).find()
                : new ExchangeFinder(routePlanner).find();
        BufferedSource source = connection.source();
        BufferedSink sink = connection.sink();
        this.interceptorScopedExchange = new Exchange(this, eventListener, routePlanner, source, sink);
        this.exchange = interceptorScopedExchange;

        synchronized (this) {
            this.requestBodyOpen = true;
            this.responseBodyOpen = true;
        }

        if (canceled) throw new IOException("Canceled");
        return interceptorScopedExchange;
    }

    void acquireConnectionNoEvents(RealConnection connection) {
        connection.assertThreadHoldsLock();

        check(this.connection == null);
        this.connection = connection;
        connection.calls.add(new CallReference(this, callStackTrace));
    }

    <E extends IOException> E messageDone(Exchange exchange, boolean requestDone, boolean responseDone, E e) {
        if (exchange != this.exchange) return e; // This exchange was detached violently!

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
                toClose = releaseConnectionNoEvents(); // Sets this.connection to null.
            }
            if (this.connection == null) {
                toClose.closeQuietly();
                eventListener.connectionReleased(this, connection);
            } else {
                check(toClose == null); // If we still have a connection we shouldn't be closing any sockets.
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
        int index = indexOfReference(calls, this);
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
        @SuppressWarnings("unchecked") // E is either IOException or IOException?
                E result = (E) e;
        return result;
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

    private Address createAddress(HttpUrl url) {
        SSLSocketFactory sslSocketFactory = null;
        HostnameVerifier hostnameVerifier = null;
        CertificatePinner certificatePinner = null;
        if (url.isHttps()) {
            sslSocketFactory = client.sslSocketFactory;
            hostnameVerifier = client.hostnameVerifier;
            certificatePinner = client.certificatePinner;
        }

        return new Address(
                url.host(),
                url.port(),
                client.dns,
                client.socketFactory,
                sslSocketFactory,
                hostnameVerifier,
                certificatePinner,
                client.proxyAuthenticator,
                client.proxy,
                client.protocols,
                client.connectionSpecs,
                client.proxySelector
        );
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

    class AsyncCall implements Runnable {
        private final Callback responseCallback;
        private final AtomicInteger callsPerHost = new AtomicInteger(0);

        AsyncCall(Callback responseCallback) {
            this.responseCallback = responseCallback;
        }

        void reuseCallsPerHostFrom(AsyncCall other) {
            this.callsPerHost.set(other.callsPerHost.get());
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
                    client.dispatcher.finished(AsyncCall.this); // This call is no longer running!
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
                        // Do not signal the callback twice!
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
