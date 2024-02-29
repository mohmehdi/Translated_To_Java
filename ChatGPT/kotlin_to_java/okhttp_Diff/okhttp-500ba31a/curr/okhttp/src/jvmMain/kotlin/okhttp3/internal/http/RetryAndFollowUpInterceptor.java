package okhttp3.internal.http;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.canReuseConnectionFor;
import okhttp3.internal.closeQuietly;
import okhttp3.internal.connection.Exchange;
import okhttp3.internal.connection.RealCall;
import okhttp3.internal.http.StatusLine.Companion.HTTP_MISDIRECTED_REQUEST;
import okhttp3.internal.http.StatusLine.Companion.HTTP_PERM_REDIRECT;
import okhttp3.internal.http.StatusLine.Companion.HTTP_TEMP_REDIRECT;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.withSuppressed;

public class RetryAndFollowUpInterceptor implements Interceptor {
    private final OkHttpClient client;

    public RetryAndFollowUpInterceptor(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
        RealInterceptorChain realChain = (RealInterceptorChain) chain;
        Request request = chain.request();
        RealCall call = realChain.call();
        int followUpCount = 0;
        Response priorResponse = null;
        boolean newRoutePlanner = true;
        List<IOException> recoveredFailures = new ArrayList<>();
        while (true) {
            call.enterNetworkInterceptorExchange(request, newRoutePlanner, chain);

            Response response;
            boolean closeActiveExchange = true;
            try {
                if (call.isCanceled()) {
                    throw new IOException("Canceled");
                }

                try {
                    response = realChain.proceed(request);
                    newRoutePlanner = true;
                } catch (IOException e) {
                    if (!recover(e, call, request, e instanceof ConnectionShutdownException)) {
                        throw e.withSuppressed(recoveredFailures);
                    } else {
                        recoveredFailures.add(e);
                    }
                    newRoutePlanner = false;
                    continue;
                }

                if (priorResponse != null) {
                    response = response.newBuilder()
                            .priorResponse(priorResponse.newBuilder()
                                    .body(null)
                                    .build())
                            .build();
                }

                Exchange exchange = call.interceptorScopedExchange();
                Request followUp = followUpRequest(response, exchange);

                if (followUp == null) {
                    if (exchange != null && exchange.isDuplex()) {
                        call.timeoutEarlyExit();
                    }
                    closeActiveExchange = false;
                    return response;
                }

                ResponseBody followUpBody = followUp.body();
                if (followUpBody != null && followUpBody.isOneShot()) {
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

    private boolean recover(IOException e, RealCall call, Request userRequest, boolean requestSendStarted) {
        if (!client.retryOnConnectionFailure()) return false;

        if (requestSendStarted && requestIsOneShot(e, userRequest)) return false;

        if (!isRecoverable(e, requestSendStarted)) return false;

        if (!call.retryAfterFailure()) return false;

        return true;
    }

    private boolean requestIsOneShot(IOException e, Request userRequest) {
        RequestBody requestBody = userRequest.body();
        return (requestBody != null && requestBody.isOneShot()) ||
                e instanceof FileNotFoundException;
    }

    private boolean isRecoverable(IOException e, boolean requestSendStarted) {
        if (e instanceof ProtocolException) {
            return false;
        }

        if (e instanceof InterruptedIOException) {
            return e instanceof SocketTimeoutException && !requestSendStarted;
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

    private Request followUpRequest(Response userResponse, Exchange exchange) throws IOException {
        Route route = exchange != null ? exchange.connection().route() : null;
        int responseCode = userResponse.code();

        String method = userResponse.request().method();
        switch (responseCode) {
            case HttpURLConnection.HTTP_PROXY_AUTH:
                Proxy selectedProxy = route != null ? route.proxy() : null;
                if (selectedProxy != null && selectedProxy.type() != Proxy.Type.HTTP) {
                    throw new ProtocolException("Received HTTP_PROXY_AUTH (407) code while not using proxy");
                }
                return client.proxyAuthenticator().authenticate(route, userResponse);

            case HttpURLConnection.HTTP_UNAUTHORIZED:
                return client.authenticator().authenticate(route, userResponse);

            case HttpURLConnection.HTTP_PERM_REDIRECT:
            case HttpURLConnection.HTTP_TEMP_REDIRECT:
            case HttpURLConnection.HTTP_MULT_CHOICE:
            case HttpURLConnection.HTTP_MOVED_PERM:
            case HttpURLConnection.HTTP_MOVED_TEMP:
            case HttpURLConnection.HTTP_SEE_OTHER:
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

                if (retryAfter(userResponse, 0) > 0) {
                    return null;
                }

                return userResponse.request();

            case HttpURLConnection.HTTP_UNAVAILABLE:
                Response priorResponse = userResponse.priorResponse();
                if (priorResponse != null && priorResponse.code() == HttpURLConnection.HTTP_UNAVAILABLE) {
                    return null;
                }

                if (retryAfter(userResponse, Integer.MAX_VALUE) == 0) {
                    return userResponse.request();
                }

                return null;

            case HTTP_MISDIRECTED_REQUEST:
                RequestBody requestBody = userResponse.request().body();
                if (requestBody != null && requestBody.isOneShot()) {
                    return null;
                }

                if (exchange == null || !exchange.isCoalescedConnection()) {
                    return null;
                }

                exchange.connection().noCoalescedConnections();
                return userResponse.request();

            default:
                return null;
        }
    }

    private Request buildRedirectRequest(Response userResponse, String method) throws IOException {
        if (!client.followRedirects()) return null;

        String location = userResponse.header("Location");
        if (location == null) return null;

        HttpUrl url = userResponse.request().url().resolve(location);
        if (url == null) return null;

        boolean sameScheme = url.scheme().equals(userResponse.request().url().scheme());
        if (!sameScheme && !client.followSslRedirects()) return null;

        Request.Builder requestBuilder = userResponse.request().newBuilder();
        if (HttpMethod.permitsRequestBody(method)) {
            int responseCode = userResponse.code();
            boolean maintainBody = HttpMethod.redirectsWithBody(method) ||
                    responseCode == HttpURLConnection.HTTP_PERM_REDIRECT ||
                    responseCode == HttpURLConnection.HTTP_TEMP_REDIRECT;
            if (HttpMethod.redirectsToGet(method) && responseCode != HttpURLConnection.HTTP_PERM_REDIRECT && responseCode != HttpURLConnection.HTTP_TEMP_REDIRECT) {
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

        if (!userResponse.request().url().canReuseConnectionFor(url)) {
            requestBuilder.removeHeader("Authorization");
        }

        return requestBuilder.url(url).build();
    }

    private int retryAfter(Response userResponse, int defaultDelay) {
        String header = userResponse.header("Retry-After");
        if (header == null) return defaultDelay;

        if (header.matches("\\d+")) {
            return Integer.valueOf(header);
        }
        return Integer.MAX_VALUE;
    }

    private static final int MAX_FOLLOW_UPS = 20;
}