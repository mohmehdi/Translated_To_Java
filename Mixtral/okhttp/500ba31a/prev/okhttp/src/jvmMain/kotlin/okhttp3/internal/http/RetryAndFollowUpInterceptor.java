

package okhttp3.internal.http;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.CanReuseConnectionFactory;
import okhttp3.internal.ExitCall;
import okhttp3.internal.Http2ConnectionShutdownException;
import okhttp3.internal.RealCall;
import okhttp3.internal.http.StatusLine;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.util.ExceptionUtils;

public class RetryAndFollowUpInterceptor implements Interceptor {

  private final OkHttpClient client;
  private static final int MAX_FOLLOW_UPS = 20;

  public RetryAndFollowUpInterceptor(OkHttpClient client) {
    this.client = client;
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    RealInterceptorChain realChain = (RealInterceptorChain) chain;
    Request request = chain.request();
    RealCall call = realChain.call();
    int followUpCount = 0;
    List<IOException> recoveredFailures = new ArrayList<>();
    Response priorResponse = null;
    boolean newExchangeFinder = true;
    while (true) {
      call.enterNetworkInterceptorExchange(request, newExchangeFinder, chain);

      Response response = null;
      boolean closeActiveExchange = true;
      try {
        if (call.isCanceled()) {
          throw new IOException("Canceled");
        }

        try {
          response = realChain.proceed(request);
          newExchangeFinder = true;
        } catch (IOException e) {
          if (!recover(e, call, request, false)) {
            throw ExceptionUtils.withSuppressed(e, recoveredFailures);
          } else {
            recoveredFailures.add(e);
          }
          newExchangeFinder = false;
          continue;
        }

        if (priorResponse != null) {
          response = response.newBuilder()
              .priorResponse(priorResponse.newBuilder()
                  .body(null)
                  .build())
              .build();
        }

        Exchange exchange = call.interceptorScopedExchange;
        Request followUp = followUpRequest(response, exchange);

        if (followUp == null) {
          if (exchange != null && exchange.isDuplex()) {
            call.timeoutEarlyExit();
          }
          closeActiveExchange = false;
          return response;
        }

        if (followUp.body() != null && followUp.body().isOneShot()) {
          closeActiveExchange = false;
          return response;
        }

        response.body().closeQuietly();

        if (++followUpCount > MAX_FOLLOW_UPS) {
          throw new ProtocolException("Too many follow-up requests: " + followUpCount);
        }

        request = followUp;
        priorResponse = response;
      } finally {
        call.exitNetworkInterceptorExchange(closeActiveExchange);
      }
    }
  }

  private boolean recover(
      IOException e, RealCall call, Request userRequest, boolean requestSendStarted) {
    if (!client.retryOnConnectionFailure()) {
      return false;
    }

    if (requestSendStarted && requestIsOneShot(e, userRequest)) {
      return false;
    }

    if (!isRecoverable(e, requestSendStarted)) {
      return false;
    }

    if (!call.retryAfterFailure()) {
      return false;
    }

    return true;
  }

  private boolean requestIsOneShot(IOException e, Request userRequest) {
    RequestBody requestBody = userRequest.body();
    return (requestBody != null && requestBody.isOneShot()) || e instanceof FileNotFoundException;
  }

  private boolean isRecoverable(IOException e, boolean requestSendStarted) {
    if (e instanceof ProtocolException) {
      return false;
    }

    if (e instanceof InterruptedIOException) {
      if (e instanceof SocketTimeoutException && !requestSendStarted) {
        return true;
      }
    }

    if (e instanceof SSLHandshakeException) {
      if (e.getCause() instanceof CertificateException) {
        return false;
      }
    }
    if (e instanceof SSLPeerUnverifiedException) {
      return false;
    }

    return true;
  }

  @Nullable
  private Request followUpRequest(Response userResponse, Exchange exchange) throws IOException {
    Route route = exchange != null ? exchange.connection().route() : null;
    int responseCode = userResponse.code();

    String method = userResponse.request().method();
    switch (responseCode) {
      case HttpURLConnection.HTTP_PROXY_AUTH:
        if (route == null || route.proxy().type() != Proxy.Type.HTTP) {
          throw new ProtocolException("Received HTTP_PROXY_AUTH (407) code while not using proxy");
        }
        return client.proxyAuthenticator().authenticate(route, userResponse);

      case HttpURLConnection.HTTP_UNAUTHORIZED:
        return client.authenticator().authenticate(route, userResponse);

      case HttpURLConnection.HTTP_PERM_REDIRECT,
          HttpURLConnection.HTTP_TEMP_REDIRECT,
          HttpURLConnection.HTTP_MULT_CHOICE,
          HttpURLConnection.HTTP_MOVED_PERM,
          HttpURLConnection.HTTP_MOVED_TEMP,
          HttpURLConnection.HTTP_SEE_OTHER:
        return buildRedirectRequest(userResponse, method);

      case HttpURLConnection.HTTP_CLIENT_TIMEOUT:
        if (!client.retryOnConnectionFailure()) {
          return null;
        }

        RequestBody requestBody = userResponse.request().body();
        if (requestBody != null && requestBody.isOneShot()) {
          return null;
        }
        Response priorResponse = userResponse.priorResponse();
        if (priorResponse != null && priorResponse.code() == HttpURLConnection.HTTP_CLIENT_TIMEOUT) {
          return null;
        }

        int retryAfter = retryAfter(userResponse, 0);
        if (retryAfter > 0) {
          return null;
        }

        return userResponse.request();

      case HttpURLConnection.HTTP_UNAVAILABLE:
        Response prior = userResponse.priorResponse();
        if (prior != null && prior.code() == HttpURLConnection.HTTP_UNAVAILABLE) {
          return null;
        }

        retryAfter = retryAfter(userResponse, Integer.MAX_VALUE);
        if (retryAfter == 0) {
          return userResponse.request();
        }

        return null;

      case HttpURLConnection.HTTP_MISDIRECTED_REQUEST:
        RequestBody body = userResponse.request().body();
        if (body != null && body.isOneShot()) {
          return null;
        }

        if (exchange == null || !exchange.connection().isCoalescedConnection()) {
          return null;
        }

        exchange.connection().noCoalescedConnections();
        return userResponse.request();

      default:
        return null;
    }
  }

  @Nullable
  private Request buildRedirectRequest(Response userResponse, String method) throws IOException {
    if (!client.followRedirects()) {
      return null;
    }

    String location = userResponse.header("Location");
    if (location == null) {
      return null;
    }

    CanReuseConnectionFactory canReuseConnectionFor = new CanReuseConnectionFactory();
    if (!canReuseConnectionFor.canReuseConnectionFor(userResponse.request().url(), location)) {
      return null;
    }

    Request.Builder requestBuilder = userResponse.request().newBuilder();
    if (HttpMethod.permitsRequestBody(method)) {
      boolean maintainBody = HttpMethod.redirectsWithBody(method)
          || userResponse.code() == HttpURLConnection.HTTP_PERM_REDIRECT
          || userResponse.code() == HttpURLConnection.HTTP_TEMP_REDIRECT;
      if (HttpMethod.redirectsToGet(method)
          && userResponse.code() != HttpURLConnection.HTTP_PERM_REDIRECT
          && userResponse.code() != HttpURLConnection.HTTP_TEMP_REDIRECT) {
        requestBuilder.method("GET", null);
      } else {
        RequestBody requestBody = maintainBody ? userResponse.request().body() : null;
        requestBuilder.method(method, requestBody);
      }
      if (!maintainBody) {
        requestBuilder.removeHeader("Transfer-Encoding");
        requestBuilder.removeHeader("Content-Length");
        requestBuilder.removeHeader("Content-Type");
      }
    }

    String url = userResponse.request().url().resolve(location).toString();
    requestBuilder.url(url);
    return requestBuilder.build();
  }

  private int retryAfter(Response userResponse, int defaultDelay) {
    String header = userResponse.header("Retry-After");
    if (header == null) {
      return defaultDelay;
    }

    if (header.matches("\\d+".toRegex())) {
      return Integer.valueOf(header);
    }
    return Integer.MAX_VALUE;
  }
}