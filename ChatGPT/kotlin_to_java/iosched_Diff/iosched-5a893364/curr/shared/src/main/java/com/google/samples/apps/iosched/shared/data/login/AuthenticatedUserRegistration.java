package com.google.samples.apps.iosched.shared.data.login;

import com.google.samples.apps.iosched.shared.BuildConfig;
import com.google.samples.apps.iosched.shared.domain.internal.DefaultScheduler;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import timber.log.Timber;
import java.io.IOException;

public class AuthenticatedUserRegistration {

    private static OkHttpClient client;

    public static void callRegistrationEndpoint(String token) {
        DefaultScheduler.INSTANCE.execute(new Runnable() {
            @Override
            public void run() {
                Request request = new Request.Builder()
                        .header("Authorization", token)
                        .url(BuildConfig.REGISTRATION_ENDPOINT_URL)
                        .build();

                okhttp3.Response response;
                try {
                    response = client.newCall(request).execute();
                } catch (IOException e) {
                    Timber.e(e);
                    return;
                }
                String body = response.body() != null ? response.body().string() : "";

                if (body.isEmpty() || !response.isSuccessful()) {
                    Timber.e("Network error calling registration point (response " + response.code() + " )");
                    return;
                }
                Timber.d("Registration point called, user is registered: " + body);
                response.body().close();
            }
        });
    }

    static {
        HttpLoggingInterceptor logInterceptor = new HttpLoggingInterceptor();
        logInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);

        client = new OkHttpClient.Builder()
                .addInterceptor(logInterceptor)
                .build();
    }
}