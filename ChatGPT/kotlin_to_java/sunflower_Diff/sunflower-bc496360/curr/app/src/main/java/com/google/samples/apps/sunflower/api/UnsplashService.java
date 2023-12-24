
package com.google.samples.apps.sunflower.api;

import com.google.samples.apps.sunflower.BuildConfig;
import com.google.samples.apps.sunflower.data.UnsplashSearchResponse;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface UnsplashService {

    @GET("search/photos")
    UnsplashSearchResponse searchPhotos(
        @Query("query") String query,
        @Query("page") int page,
        @Query("per_page") int perPage,
        @Query("client_id") String clientId
    );

    class Companion {
        private static final String BASE_URL = "https://";

        public static UnsplashService create() {
            HttpLoggingInterceptor logger = new HttpLoggingInterceptor();
            logger.setLevel(Level.BASIC);

            OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logger)
                .build();

            return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(UnsplashService.class);
        }
    }
}
