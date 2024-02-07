package io.plaidapp.designernews.data.api;

import io.plaidapp.core.designernews.data.login.AuthTokenLocalDataSource;
import java.io.IOException;
import java.net.URL;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class ClientAuthInterceptor implements Interceptor {

  private final AuthTokenLocalDataSource authTokenDataSource;
  private final String clientId;

  public ClientAuthInterceptor(
    AuthTokenLocalDataSource authTokenDataSource,
    String clientId
  ) {
    this.authTokenDataSource = authTokenDataSource;
    this.clientId = clientId;
  }

  @Override
  public Response intercept(Interceptor.Chain chain) throws IOException {
    Request.Builder requestBuilder = chain.request().newBuilder();
    if (!authTokenDataSource.getAuthToken().isEmpty()) {
      requestBuilder.addHeader(
        "Authorization",
        "Bearer " + authTokenDataSource.getAuthToken()
      );
    } else {
      URL url = chain
        .request()
        .url()
        .newBuilder()
        .addQueryParameter("client_id", clientId)
        .build()
        .url();
      requestBuilder.url(url);
    }
    return chain.proceed(requestBuilder.build());
  }
}
