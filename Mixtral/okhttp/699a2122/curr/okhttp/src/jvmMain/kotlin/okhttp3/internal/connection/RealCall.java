package okhttp3.internal.connection;

import okhttp3.*;
import okhttp3.internal.cache.CacheInterceptor;
import okhttp3.internal.http.BridgeInterceptor;
import okhttp3.internal.http.CallServerInterceptor;
import okhttp3.internal.http.RealInterceptorChain;
import okhttp3.internal.http.RetryAndFollowUpInterceptor;
import okhttp3.internal.platform.Platform;
import okhttp3.internal.threadName;
import okio.AsyncTimeout;
import okio.Timeout;
import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RealCall implements Call {
  private final OkHttpClient client;
  private final Request originalRequest;
  private final boolean forWebSocket;
  private RealConnectionPool connectionPool;
  private EventListener eventListener;
  private AsyncTimeout timeout;
  private AtomicBoolean executed;
  private Object callStackTrace;
  private RoutePlanner routePlanner;
  private RealConnection connection;
  private Exchange interceptorScopedExchange;
  private boolean requestBodyOpen;
  private boolean responseBodyOpen;
  private boolean expectMoreExchanges;
  private volatile boolean canceled;
  private Exchange exchange;
  private CopyOnWriteArrayList<RoutePlanner.Plan> plansToCancel;

  public RealCall(OkHttpClient client, Request originalRequest, boolean forWebSocket) {
    this.client = client;
    this.originalRequest = originalRequest;
    this.forWebSocket = forWebSocket;
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
    List<Interceptor> interceptors = new ArrayList<>();
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
        exchange,
        originalRequest,
        client.connectTimeoutMillis(),
        client.readTimeoutMillis(),
        client.writeTimeoutMillis()
    );

    try {
      return chain.proceed(originalRequest);
    } finally {
      chain.release();
    }
  }

  private void enterNetworkInterceptorExchange(
      Request request,
      boolean newRoutePlanner,
      RealInterceptorChain chain
  ) {
    if (interceptorScopedExchange != null) {
      throw new IllegalStateException("cannot make a new request because the previous response is still open: " +
          "please call response.close()");
    }

    if (newRoutePlanner) {
      this.routePlanner = new RealRoutePlanner(
          client,
          createAddress(request.url()),
          this,
          chain
      );
    }
  }

  private Exchange initExchange(RealInterceptorChain chain) {
    synchronized (this) {
      if (!expectMoreExchanges) {
        throw new IllegalStateException("released");
      }
      if (responseBodyOpen) {
        throw new IllegalStateException("cannot make a new request because the previous response is still open: " +
            "please call response.close()");
      }
    }

    RoutePlanner routePlanner = this.routePlanner;
    RealConnection connection = routePlanner.findConnection();
    Codec codec = connection.newCodec(client, chain);
    Exchange result = new Exchange(this, eventListener, routePlanner, codec);
    this.interceptorScopedExchange = result;
    this.exchange = result;
    synchronized (this) {
      requestBodyOpen = true;
      responseBodyOpen = true;
    }

    if (canceled) {
      throw new IOException("Canceled");
    }
    return result;
  }

  private void acquireConnectionNoEvents(RealConnection connection) {
    connection.assertThreadHoldsLock();

    if (this.connection != null) {
      throw new IllegalStateException("Already acquired a connection");
    }
    this.connection = connection;
    connection.calls.add(new CallReference(this, callStackTrace));
  }

  private <E extends IOException> E messageDone(
      Exchange exchange,
      boolean requestDone,
      boolean responseDone,
      E e
  ) throws E {
    if (exchange != this.exchange) {
      return e; // This exchange was detached violently!
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
      connection.incrementSuccessCount();
    }

    if (callDone) {
      return callDone(e);
    }

    return e;
  }


public IOException noMoreExchanges(IOException e) {
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


  private <E extends IOException> E callDone(E e) throws E {
    assertThreadDoesntHoldLock();

    Socket socket = null;
    synchronized (this) {
      if (connection != null) {
        connection.assertThreadDoesntHoldLock();
        socket = releaseConnectionNoEvents();
      }
    }

    if (socket != null) {
      eventListener.connectionReleased(this, connection);
    }

    E result = timeoutExit(e);
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

    List<Closeable> calls = connection.calls;
    int index = calls.indexOf(this);
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
    if (timeoutEarlyExit) {
      return cause;
    }
    if (!timeout.exit()) {
      return cause;
    }

    InterruptedIOException e = new InterruptedIOException("timeout");
    if (cause != null) {
      e.initCause(cause);
    }
    return (E) e;
  }

  private void timeoutEarlyExit() {
    if (timeoutEarlyExit) {
      return;
    }
    timeoutEarlyExit = true;
    timeout.exit();
  }

  public synchronized void exitNetworkInterceptorExchange(boolean closeExchange) {
    expectMoreExchanges = expectMoreExchanges && !closed; // assuming expectMoreExchanges is a boolean variable
    if (closeExchange) {
        if (exchange != null) {
            exchange.detachWithViolence();
        }
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
        client.proxySelector()
    );
  }

  private boolean retryAfterFailure() {
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

  static class AsyncCall implements Runnable {
    private final Callback responseCallback;
    private final AtomicInteger callsPerHost;
    private final String host;
    private final Request request;
    private final RealCall call;

    public AsyncCall(Callback responseCallback) {
      this.responseCallback = responseCallback;
      this.callsPerHost = new AtomicInteger(0);
      this.host = originalRequest.url().host();
      this.request = originalRequest;
      this.call = this@RealCall;
    }

    public void reuseCallsPerHostFrom(AsyncCall other) {
      this.callsPerHost.set(other.callsPerHost.get());
    }

    public String host() {
      return host;
    }

    public Request request() {
      return request;
    }

    public RealCall call() {
      return call;
    }

    public void executeOn(ExecutorService executorService) {
      client.dispatcher().assertThreadDoesntHoldLock();

      boolean success = false;
      try {
        executorService.execute(this);
        success = true;
      } catch (RejectedExecutionException e) {
        IOException ioException = new InterruptedIOException("executor rejected");
        ioException.initCause(e);
        noMoreExchanges(ioException);
        responseCallback.onFailure(call, ioException);
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
        Response response = getResponseWithInterceptorChain();
        responseCallback.onResponse(call, response);
      } catch (IOException e) {
        responseCallback.onFailure(call, e);
      }
    }
  }

  static class CallReference extends WeakReference<RealCall> {
    private final Object callStackTrace;

    public CallReference(RealCall referent, Object callStackTrace) {
      super(referent);
      this.callStackTrace = callStackTrace;
    }
  }
}