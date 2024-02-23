

package okhttp3.internal.connection;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.util.ArrayList;
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
import okhttp3.internal.http.RealInterceptorChain;
import okhttp3.internal.http.RetryAndFollowUpInterceptor;
import okhttp3.internal.platform.Platform;
import okhttp3.internal.threadName;
import okio.AsyncTimeout;
import okio.Timeout;

class RealCall implements Call {
  private final OkHttpClient client;
  private final Request originalRequest;
  private final boolean forWebSocket;
  private final RealConnectionPool connectionPool;
  private final EventListener eventListener;
  private final AsyncTimeout timeout;
  private final AtomicBoolean executed;
  private Object callStackTrace;
  private RoutePlanner routePlanner;
  private RealConnection connection;
  private boolean timeoutEarlyExit;
  private Exchange interceptorScopedExchange;
  private boolean requestBodyOpen;
  private boolean responseBodyOpen;
  private boolean expectMoreExchanges;
  private volatile boolean canceled;
  private volatile Exchange exchange;
  private final CopyOnWriteArrayList<RealConnection> connectionsToCancel;

  RealCall(
      OkHttpClient client,
      Request originalRequest,
      boolean forWebSocket) {
    this.client = client;
    this.originalRequest = originalRequest;
    this.forWebSocket = forWebSocket;
    this.connectionPool = client.connectionPool().delegate();
    this.eventListener = client.eventListenerFactory().create(this);
    this.timeout = new AsyncTimeout() {
      @Override
      public void timedOut() {
        cancel();
      }
    }.apply {
      timeout(client.callTimeoutMillis(), TimeUnit.MILLISECONDS);
    };
    this.executed = new AtomicBoolean();
    this.connectionsToCancel = new CopyOnWriteArrayList<RealConnection>();
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
    if (executed.compareAndSet(false, true)) {
      timeout.enter();
      callStart();
      try {
        client.dispatcher().executed(this);
        return getResponseWithInterceptorChain();
      } finally {
        client.dispatcher().finished(this);
      }
    } else {
      throw new IllegalStateException("Already Executed");
    }
  }

  @Override
  public void enqueue(Callback responseCallback) {
    if (executed.compareAndSet(false, true)) {
      callStart();
      client.dispatcher().enqueue(new AsyncCall(responseCallback));
    } else {
      throw new IllegalStateException("Already Executed");
    }
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
    ArrayList<Interceptor> interceptors = new ArrayList<>();
    interceptors.addAll(client.interceptors());
    interceptors.add(new RetryAndFollowUpInterceptor(client));
    interceptors.add(new BridgeInterceptor(client.cookieJar()));
    interceptors.add(new CacheInterceptor(client.cache()));
    interceptors.add(new ConnectInterceptor());
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

  void enterNetworkInterceptorExchange(
      Request request,
      boolean newRoutePlanner,
      RealInterceptorChain chain) {
    synchronized (this) {
      if (interceptorScopedExchange != null) {
        throw new IllegalStateException("cannot make a new request because the previous response is still open: " +
            "please call response.close()");
      }
      if (requestBodyOpen) {
        throw new IllegalStateException("cannot make a new request because the previous request body is still open");
      }
    }

    if (newRoutePlanner) {
      this.routePlanner = new RealRoutePlanner(
          client,
          createAddress(request.url()),
          this,
          chain);
    }
  }

  private Exchange initExchange(RealInterceptorChain chain) {
    synchronized (this) {
      if (expectMoreExchanges) {
        expectMoreExchanges = false;
        requestBodyOpen = true;
        responseBodyOpen = true;
      }
    }

    RealRoutePlanner routePlanner = this.routePlanner;
    RealConnection connection = routePlanner.findConnection(client.taskRunner());
    ExchangeFinder.Exchange exchange = connection.newExchange(client, chain);
    Exchange result = new Exchange(this, eventListener, routePlanner, exchange);
    this.interceptorScopedExchange = result;
    this.exchange = result;

    return result;
  }

  void acquireConnectionNoEvents(RealConnection connection) {
    connection.assertThreadHoldsLock();

    if (this.connection != null) {
      throw new IllegalStateException("Already acquired a connection");
    }
    this.connection = connection;
    connection.calls.add(new CallReference(this, callStackTrace));
  }

  private IOException messageDone(
      Exchange exchange,
      boolean requestDone,
      boolean responseDone,
      IOException e) {
    if (exchange != this.exchange) return e;

    boolean bothStreamsDone = false;
    boolean callDone = false;
    synchronized (this) {
      if (requestDone && requestBodyOpen || responseDone && responseBodyOpen) {
        if (requestDone) requestBodyOpen = false;
        if (responseDone) responseBodyOpen = false;
        bothStreamsDone = !requestBodyOpen && !responseBodyOpen;
        callDone = !requestBodyOpen && !responseBodyOpen && !expectMoreExchanges;
      }
    }

    if (bothStreamsDone) {
      this.exchange = null;
      connection.incrementSuccessCount();
    }

    if (callDone) {
      return callDone(e);
    }

    return e;
  }

  private IOException noMoreExchanges(IOException e) {
    if (expectMoreExchanges) {
      expectMoreExchanges = false;
      requestBodyOpen = false;
      responseBodyOpen = false;
    }

    return e;
  }

  private IOException callDone(IOException e) {
    assertThreadDoesntHoldLock();

    RealConnection connection = this.connection;
    if (connection != null) {
      connection.assertThreadDoesntHoldLock();

      ArrayList<WeakReference<RealCall>> calls = connection.calls();
      int index = calls.indexOf(new WeakReference<RealCall>(this));
      if (index != -1) {
        calls.remove(index);
      }
      this.connection = null;

      if (calls.isEmpty()) {
        connection.idleAtNs = System.nanoTime();
        if (connectionPool.connectionBecameIdle(connection)) {
          return connection.socket();
        }
      }
    }

    IOException result = timeoutExit(e);
    if (e != null) {
      eventListener.callFailed(this, result);
    } else {
      eventListener.callEnd(this);
    }
    return result;
  }

  private Socket releaseConnectionNoEvents() {
    RealConnection connection = this.connection;
    connection.assertThreadHoldsLock();

    ArrayList<WeakReference<RealCall>> calls = connection.calls();
    int index = calls.indexOf(new WeakReference<RealCall>(this));
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

  private IOException timeoutExit(IOException cause) {
    if (timeoutEarlyExit) return cause;
    if (!timeout.exit()) return cause;

    InterruptedIOException e = new InterruptedIOException("timeout");
    if (cause != null) e.initCause(cause);
    return e;
  }

  void timeoutEarlyExit() {
    check(!timeoutEarlyExit);
    timeoutEarlyExit = true;
    timeout.exit();
  }

  void exitNetworkInterceptorExchange(boolean closeExchange) {
    synchronized (this) {
      if (expectMoreExchanges) {
        expectMoreExchanges = false;
      }
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
      sslSocketFactory = client.sslSocketFactory();
      hostnameVerifier = client.hostnameVerifier();
      certificatePinner = client.certificatePinner();
    }

    return new Address(
        url.host(),
        url.port(),
        client.dns(),
        client.socketFactory(),
        sslSocketFactory,
        hostnameVerifier,
        certificatePinner,
        client.proxyAuthenticator(),
        client.proxy(),
        client.protocols(),
        client.connectionSpecs(),
        client.proxySelector());
  }

  boolean retryAfterFailure() {
    return routePlanner.hasFailure() && routePlanner.hasMoreRoutes();
  }

  private String toLoggableString() {
    return ((canceled ? "canceled " : "") +
        (forWebSocket ? "web socket" : "call") +
        " to " + redactedUrl());
  }

  private String redactedUrl() {
    return originalRequest.url().redact();
  }

  class AsyncCall implements Runnable {
    private final Callback responseCallback;
    private final AtomicInteger callsPerHost;
    private final String host;
    private final Request request;
    private final RealCall call;

    AsyncCall(Callback responseCallback) {
      this.responseCallback = responseCallback;
      this.callsPerHost = new AtomicInteger(0);
      this.host = originalRequest.url().host();
      this.request = originalRequest;
      this.call = RealCall.this;
    }

    void reuseCallsPerHostFrom(AsyncCall other) {
      this.callsPerHost.set(other.callsPerHost.get());
    }

    public void executeOn(ExecutorService executorService) {
    client.dispatcher().assertThreadDoesntHoldLock();

    boolean success = false;
    try {
        executorService.execute(() -> {
            this.run();
        });
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

    @Override
    public void run() {
      threadName("OkHttp " + redactedUrl());
      try {
        Response response = call.getResponseWithInterceptorChain();
        responseCallback.onResponse(call, response);
      } catch (IOException e) {
        responseCallback.onFailure(call, e);
      }
    }


  }

  static class CallReference extends WeakReference<RealCall> {
    private final Object callStackTrace;

    CallReference(RealCall referent, Object callStackTrace) {
      super(referent);
      this.callStackTrace = callStackTrace;
    }
  }
}