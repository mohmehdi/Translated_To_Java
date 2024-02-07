package io.plaidapp.designernews.dagger;

import com.google.gson.Gson;
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;
import io.plaidapp.core.BuildConfig;
import io.plaidapp.core.dagger.CoreDataModule;
import io.plaidapp.core.dagger.CoroutinesDispatcherProviderModule;
import io.plaidapp.core.dagger.SharedPreferencesModule;
import io.plaidapp.core.data.api.ClientAuthInterceptor;
import io.plaidapp.core.data.api.DenvelopingConverter;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.login.AuthTokenLocalDataSource;
import io.plaidapp.designernews.data.api.DNService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@Qualifier
@Retention(SOURCE)
@interface LocalApi {
}

@Module(
  includes = {
    SharedPreferencesModule.class,
    CoreDataModule.class,
    CoroutinesDispatcherProviderModule.class,
  }
)
class DataModule {

  @LocalApi
  @Provides
  OkHttpClient providePrivateOkHttpClient(
    OkHttpClient upstream,
    AuthTokenLocalDataSource tokenHolder
  ) {
    OkHttpClient.Builder builder = upstream.newBuilder();
    builder.addInterceptor(
      new ClientAuthInterceptor(
        tokenHolder,
        BuildConfig.DESIGNER_NEWS_CLIENT_ID
      )
    );
    return builder.build();
  }

  @Provides
  DNService provideDNService(@LocalApi Lazy<OkHttpClient> client, Gson gson) {
    Retrofit.Builder builder = new Retrofit.Builder();
    builder.baseUrl(DesignerNewsService.ENDPOINT);
    builder.client(client.get());
    builder.addConverterFactory(new DenvelopingConverter(gson));
    builder.addConverterFactory(GsonConverterFactory.create(gson));
    builder.addCallAdapterFactory(CoroutineCallAdapterFactory.create());
    Retrofit retrofit = builder.build();
    return retrofit.create(DNService.class);
  }
}
