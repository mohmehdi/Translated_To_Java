package io.plaidapp.designernews.dagger;

import com.google.gson.Gson;
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory;

import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import io.plaidapp.core.BuildConfig;
import io.plaidapp.core.dagger.CoreDataModule;
import io.plaidapp.core.dagger.CoroutinesDispatcherProviderModule;
import io.plaidapp.core.dagger.SharedPreferencesModule;
import io.plaidapp.core.data.api.DenvelopingConverter;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.login.AuthTokenLocalDataSource;
import io.plaidapp.designernews.data.api.ClientAuthInterceptor;
import io.plaidapp.designernews.data.api.DNService;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import javax.inject.Qualifier;

import static java.lang.annotation.RetentionPolicy.CLASS;

@Retention(CLASS)
@Qualifier
@interface LocalApi {}

@Module(
        includes = {
                SharedPreferencesModule.class,
                CoreDataModule.class,
                CoroutinesDispatcherProviderModule.class
        }
)
public class DataModule {

    @LocalApi
    @Provides
    public OkHttpClient providePrivateOkHttpClient(
            OkHttpClient upstream,
            AuthTokenLocalDataSource tokenHolder
    ) {
        return upstream.newBuilder()
                .addInterceptor(new ClientAuthInterceptor(tokenHolder, BuildConfig.DESIGNER_NEWS_CLIENT_ID))
                .build();
    }

    @Provides
    public DNService provideDNService(
            @LocalApi Lazy<OkHttpClient> client,
            Gson gson
    ) {
        return new Retrofit.Builder()
                .baseUrl(DesignerNewsService.ENDPOINT)
                .callFactory(request -> client.get().newCall(request))
                .addConverterFactory(new DenvelopingConverter(gson))
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(CoroutineCallAdapterFactory.create())
                .build()
                .create(DNService.class);
    }
}