package io.plaidapp.core.dagger.designernews;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.room.RoomDatabase;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import io.plaidapp.core.dagger.CoreDataModule;
import io.plaidapp.core.dagger.CoroutinesDispatcherProviderModule;
import io.plaidapp.core.designernews.data.api.ClientAuthInterceptor;
import io.plaidapp.core.designernews.data.api.DenvelopingConverter;
import io.plaidapp.core.designernews.data.api.DesignerNewsSearchConverter;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.database.DesignerNewsDatabase;
import io.plaidapp.core.designernews.data.database.LoggedInUserDao;
import io.plaidapp.core.designernews.data.login.AuthTokenLocalDataSource;
import io.plaidapp.core.designernews.data.login.LoginLocalDataSource;
import io.plaidapp.core.designernews.data.login.LoginRepository;
import io.plaidapp.core.designernews.data.stories.StoriesRemoteDataSource;
import io.plaidapp.core.designernews.data.stories.StoriesRepository;
import java.util.concurrent.ExecutorService;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

@Qualifier
@interface LocalApi {
}

@Module(
  includes = {
    SharedPreferencesModule.class,
    CoreDataModule.class,
    CoroutinesDispatcherProviderModule.class,
  }
)
public class DesignerNewsDataModule {

  @Provides
  @Singleton
  static LoginRepository provideLoginRepository(
    LoginLocalDataSource localSource,
    LoginRemoteDataSource remoteSource
  ) {
    return LoginRepository.getInstance(localSource, remoteSource);
  }

  @Provides
  @Singleton
  static AuthTokenLocalDataSource provideAuthTokenLocalDataSource(
    SharedPreferences sharedPreferences
  ) {
    return AuthTokenLocalDataSource.getInstance(sharedPreferences);
  }

  @Provides
  @LocalApi
  @Singleton
  static OkHttpClient providePrivateOkHttpClient(
    OkHttpClient upstream,
    AuthTokenLocalDataSource tokenHolder
  ) {
    return upstream
      .newBuilder()
      .addInterceptor(
        new ClientAuthInterceptor(
          tokenHolder,
          BuildConfig.DESIGNER_NEWS_CLIENT_ID
        )
      )
      .build();
  }

  @Provides
  @Singleton
  static DesignerNewsService provideDesignerNewsService(
    @LocalApi Lazy<OkHttpClient> client,
    Gson gson
  ) {
    return new Retrofit.Builder()
      .baseUrl(DesignerNewsService.ENDPOINT)
      .callFactory(new OkHttpCallFactory(client.get()))
      .addConverterFactory(new DenvelopingConverter(gson))
      .addConverterFactory(DesignerNewsSearchConverter.Factory.create())
      .addConverterFactory(GsonConverterFactory.create(gson))
      .addCallAdapterFactory(new CoroutineCallAdapterFactory())
      .build()
      .create(DesignerNewsService.class);
  }

  @Provides
  @Singleton
  static LoggedInUserDao provideLoggedInUserDao(Context context) {
    return DesignerNewsDatabase.getInstance(context).loggedInUserDao();
  }

  @Provides
  @Singleton
  static StoriesRepository provideStoriesRepository(
    StoriesRemoteDataSource storiesRemoteDataSource
  ) {
    return StoriesRepository.getInstance(storiesRemoteDataSource);
  }

  @Provides
  @Singleton
  static StoriesRemoteDataSource provideStoriesRemoteDataSource(
    DesignerNewsService service
  ) {
    return StoriesRemoteDataSource.getInstance(service);
  }
}
