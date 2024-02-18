

package okhttp3.internal.connection;

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
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
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Route;
import okhttp3.internal.Internal;
import okhttp3.internal.Platform;
import okhttp3.internal.RealCall;
import okhttp3.internal.RetryAndFollowUpInterceptor;
import okhttp3.internal.Util;
import okhttp3.internal.http.BridgeInterceptor;
import okhttp3.internal.http.CallServerInterceptor;
import okhttp3.internal.http.HttpCodec;
import okhttp3.internal.http.HttpHeaders;
import okhttp3.internal.http.RealInterceptorChain;
import okhttp3.internal.http.StreamAllocation;
import okhttp3.internal.http.StreamAllocationListener;
import okhttp3.internal.http.UnrepeatableRequestBody;
import okhttp3.internal.platform.PlatformJdk;
import okhttp3.internal.platform.PlatformTest;
import okhttp3.internal.platform.Platforms;
import okhttp3.internal.tls.RealConnection;
import okio.AsyncTimeout;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.ForwardingTimeout;
import okio.Okio;
import okio.Timeout;

public class RealCall implements Call {
  private final OkHttpClient client;
  private final Request originalRequest;
  private final boolean forWebSocket;
  private RealConnection connection;
  private RoutePlanner routePlanner;
  private Exchange interceptorScopedExchange;
  private boolean requestBodyOpen;
  private boolean responseBodyOpen;
  private boolean expectMoreExchanges;
  private boolean canceled;
  private Exchange exchange;
  private final CopyOnWriteArrayList<RealConnection> connectionsToCancel;
  private final AtomicBoolean executed;
  private final AsyncTimeout timeout;
  private final AtomicInteger callsPerHost;
  private final EventListener eventListener;
  private Object callStackTrace;
  private boolean timeoutEarlyExit;
  private volatile Callback responseCallback;
  private volatile Runnable asyncCallback;

  public RealCall(OkHttpClient client, Request originalRequest, boolean forWebSocket) {
    this.client = client;
    this.originalRequest = originalRequest;
    this.forWebSocket = forWebSocket;
    this.executed = new AtomicBoolean(false);
    this.connectionsToCancel = new CopyOnWriteArrayList<>();
    this.timeout = new AsyncTimeout();
    this.callsPerHost = new AtomicInteger(0);
    this.eventListener = client.eventListenerFactory().create(this);
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
    if (!executed.compareAndSet(false, true)) {
      throw new IllegalStateException("Already Executed");
    }

    timeout.enter();
    callStart();
    try {
      client.dispatcher().executed(this);
      return getResponseWithInterceptorChain();
    } finally {
      client.dispatcher().finished(this);
    }
  }

  @Override
  public void enqueue(Callback responseCallback) {
    if (!executed.compareAndSet(false, true)) {
      throw new IllegalStateException("Already Executed");
    }

    callStart();
    client.dispatcher().enqueue(new AsyncCall(responseCallback));
  }

  @Override
  public boolean isExecuted() {
    return executed.get();
  }

  private void callStart() {
    this.callStackTrace = PlatformJdk.getStackTraceForCloseable("response.body().close()");
    eventListener.callStart(this);
  }

  private Response getResponseWithInterceptorChain() throws IOException {
    List<Interceptor> interceptors = new ArrayList<>();
    interceptors.addAll(client.interceptors());
    interceptors.add(new RetryAndFollowUpInterceptor(client));
    interceptors.add(new BridgeInterceptor(client.cookieJar()));
    interceptors.add(new CacheInterceptor(client.cache()));
    interceptors.add(new ConnectInterceptor(client));
    if (!forWebSocket) {
      interceptors.addAll(client.networkInterceptors());
    }
    interceptors.add(new CallServerInterceptor(forWebSocket));

    RealInterceptorChain chain = new RealInterceptorChain(
        this,
        interceptors,
        0,
        null,
        originalRequest,
        client.connectTimeoutMillis(),
        client.readTimeoutMillis(),
        client.writeTimeoutMillis());

    try {
      return chain.proceed(originalRequest);
    } finally {
      chain.release();
    }
  }

  public void enterNetworkInterceptorExchange(
      Request request, boolean newRoutePlanner, RealInterceptorChain chain) {
    synchronized (this) {
      if (interceptorScopedExchange != null) {
        throw new IllegalStateException(
            "cannot make a new request because the previous response is still open: "
                + "please call response.close()");
      }
      if (requestBodyOpen) {
        throw new IllegalStateException(
            "cannot make a new request because the previous request body is still open");
      }
    }

    if (newRoutePlanner) {
      this.routePlanner =
          new RoutePlanner(
              client,
              createAddress(request.url()),
              this,
              chain,
              PlatformTest.get());
    }
  }

  private Exchange initExchange(RealInterceptorChain chain) throws IOException {
    synchronized (this) {
      if (expectMoreExchanges) {
        expectMoreExchanges = false;
        requestBodyOpen = true;
        responseBodyOpen = true;
      } else {
        throw new IllegalStateException("released");
      }
    }

    HttpCodec codec =
        new ExchangeFinder(routePlanner)
            .find()
            .newCodec(client, chain);
    Exchange result = new Exchange(this, eventListener, routePlanner, codec);
    interceptorScopedExchange = result;
    exchange = result;

    return result;
  }

  public void acquireConnectionNoEvents(RealConnection connection) {
    connection.assertThreadHoldsLock();

    if (this.connection != null) {
      throw new IllegalStateException("Already acquired");
    }
    this.connection = connection;
    connection.calls.add(new CallReference(this, callStackTrace));
  }

  private IOException messageDone(
      Exchange exchange,
      boolean requestDone,
      boolean responseDone,
      IOException e) {
    if (exchange != this.exchange) {
      return e;
    }

    boolean bothStreamsDone = false;
    boolean callDone = false;
    synchronized (this) {
      if (requestDone && requestBodyOpen || responseDone && responseBodyOpen) {
        if (requestDone) {
          requestBodyOpen = false;
        }
        if (responseDone) {
          responseBodyOpen = false;
        }
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

  private IOException noMoreExchanges(IOException e) {
    if (expectMoreExchanges) {
      expectMoreExchanges = false;
    }

    if (exchange != null) {
      exchange.detachWithViolence();
    }

    interceptorScopedExchange = null;

    return e;
  }

  private IOException callDone(IOException e) {
    assertThreadDoesntHoldLock();

    Socket socket = null;
    synchronized (connection) {
      socket = releaseConnectionNoEvents();
    }

    if (socket != null) {
      Platform.get().log("Connection released", Platform.INFO);
    }

    if (e != null) {
      eventListener.callFailed(this, e);
    } else {
      eventListener.callEnd(this);
    }

    return e;
  }

  private Socket releaseConnectionNoEvents() {
    connection.assertThreadHoldsLock();

    List<CallReference> calls = connection.calls;
    int index = calls.indexOf(new WeakReference<>(this));
    if (index == -1) {
      throw new IllegalStateException("Call not found in calls list");
    }

    CallReference callReference = calls.remove(index);
    if (calls.isEmpty()) {
      connection.idleAtNanos = System.nanoTime();
      if (connectionPool().connectionBecameIdle(connection)) {
        return connection.socket();
      }
    }

    callReference.clear();

    return null;
  }


    public void timeoutEarlyExit() {
    if (timeoutEarlyExit) {
        throw new IllegalStateException("timeoutEarlyExit already set");
    }
    timeoutEarlyExit = true;
    timeout.exit();
    }

    internal void exitNetworkInterceptorExchange(boolean closeExchange) {
        synchronized (this) {
            if (!expectMoreExchanges) {
                throw new IllegalStateException("released");
            }
        }

        if (closeExchange && exchange != null) {
            exchange.detachWithViolence();
        }

        interceptorScopedExchange = null;
    }

    private Address createAddress(HttpUrl url) {
        SSLSocketFactory sslSocketFactory = null;
        HostnameVerifier hostnameVerifier = null;
        CertificatePinner certificatePinner = null;
        if (url.isHttps()) {
            sslSocketFactory = client.sslSocketFactory();
            hostnameVerifier = client.hostnameVerifier();
            certificatePinner = client.certificatePinner();
        }

        return new Address(
                uriHost = url.host(),
                uriPort = url.port(),
                dns = client.dns(),
                socketFactory = client.socketFactory(),
                sslSocketFactory = sslSocketFactory,
                hostnameVerifier = hostnameVerifier,
                certificatePinner = certificatePinner,
                proxyAuthenticator = client.proxyAuthenticator(),
                proxy = client.proxy(),
                protocols = client.protocols(),
                connectionSpecs = client.connectionSpecs(),
                proxySelector = client.proxySelector());
    }

    public boolean retryAfterFailure() {
        return routePlanner != null && routePlanner.retryAfterFailure();
    }

    private String toLoggableString() {
        return (isCanceled() ? "canceled " : "") +
                (forWebSocket ? "web socket" : "call") +
                " to " + redactedUrl();
    }

    internal String redactedUrl() {
        return originalRequest.url().redact();
    }




  private class CallReference extends WeakReference<RealCall> {
    private final Object callStackTrace;

    public CallReference(RealCall referent, Object callStackTrace) {
      super(referent);
      this.callStackTrace = callStackTrace;
    }
  }

  private class AsyncCall implements Runnable, Callback {
    private final Callback responseCallback;
    private final AtomicInteger callsPerHost;
    private final String host;
    private final Request request;
    private final RealCall call;

    public AsyncCall(Callback responseCallback) {
      this.responseCallback = responseCallback;
      this.callsPerHost = RealCall.this.callsPerHost;
      this.host = originalRequest.url().host();
      this.request = originalRequest;
      this.call = RealCall.this;
    }

    public void executeOn(ExecutorService executorService) {
    client.dispatcher().assertThreadDoesntHoldLock();

    boolean success = false;
    try {
        executorService.execute(this);
        success = true;
    } catch (RejectedExecutionException e) {
        InterruptedIOException ioException = new InterruptedIOException("executor rejected");
        ioException.initCause(e);
        noMoreExchanges(ioException);
        responseCallback.onFailure(this, ioException);
    } finally {
        if (!success) {
            client.dispatcher().finished(this); // This call is no longer running!
        }
    }
}

    public void reuseCallsPerHostFrom(AsyncCall other) {
      this.callsPerHost.set(other.callsPerHost.get());
    }

    @Override
    public void run() {
      PlatformTest.threadName("OkHttp " + host);
      try {
        Response response = call.getResponseWithInterceptorChain();
        responseCallback.onResponse(call, response);
      } catch (IOException e) {
        responseCallback.onFailure(call, e);
      }
    }
  }
}