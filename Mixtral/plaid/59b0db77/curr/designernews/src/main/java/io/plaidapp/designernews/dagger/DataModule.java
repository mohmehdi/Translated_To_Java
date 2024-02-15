

package io.plaidapp.designernews.dagger;

import com.google.gson.Gson;
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import io.plaidapp.core.BuildConfig;
import io.plaidapp.core.dagger.CoreDataModule;
import io.plaidapp.core.dagger.CoroutinesDispatcherProviderModule;
import io.plaidapp.core.dagger.SharedPreferencesModule;
import io.plaidapp.core.data.api.DenvelopingConverter;
import io.plaidapp.core.data.api.ClientAuthInterceptor;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.login.AuthTokenLocalDataSource;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Qualifier
@Retention(SOURCE)
annotation class LocalApi {
}

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
    public OkHttpClient providePrivateOkHttpClient(OkHttpClient upstream,
                                                     AuthTokenLocalDataSource tokenHolder) {
        return upstream.newBuilder()
                .addInterceptor(new ClientAuthInterceptor(tokenHolder,
                        BuildConfig.DESIGNER_NEWS_CLIENT_ID))
                .build();
    }

    @Provides
    public DNService provideDNService(@LocalApi Lazy<OkHttpClient> client, Gson gson) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(DesignerNewsService.ENDPOINT)
                .client(client.get())
                .addConverterFactory(new DenvelopingConverter(gson))
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(CoroutineCallAdapterFactory.create())
                .build();

        return retrofit.create(DNService.class);
    }
}