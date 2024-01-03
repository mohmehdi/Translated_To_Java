package io.plaidapp.designernews.data.api;

import io.plaidapp.core.designernews.data.login.AuthTokenLocalDataSource;
import okhttp3.Interceptor;
import okhttp3.Response;

import java.io.IOException;

public class ClientAuthInterceptor implements Interceptor {
    private AuthTokenLocalDataSource authTokenDataSource;
    private String clientId;

    public ClientAuthInterceptor(AuthTokenLocalDataSource authTokenDataSource, String clientId) {
        this.authTokenDataSource = authTokenDataSource;
        this.clientId = clientId;
    }

    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
        okhttp3.Request.Builder requestBuilder = chain.request().newBuilder();
        if (!authTokenDataSource.getAuthToken().isEmpty()) {
            requestBuilder.addHeader("Authorization",
                    "Bearer " + authTokenDataSource.getAuthToken());
        } else {
            okhttp3.HttpUrl url = chain.request().url().newBuilder()
                    .addQueryParameter("client_id", clientId).build();
            requestBuilder.url(url);
        }
        return chain.proceed(requestBuilder.build());
    }
}