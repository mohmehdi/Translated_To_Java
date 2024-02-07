package io.plaidapp.core.dagger.designernews;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;
import io.plaidapp.core.BuildConfig;
import io.plaidapp.core.dagger.CoreDataModule;
import io.plaidapp.core.dagger.CoroutinesDispatcherProviderModule;
import io.plaidapp.core.dagger.SharedPreferencesModule;
import io.plaidapp.core.data.api.ClientAuthInterceptor;
import io.plaidapp.core.data.api.DenvelopingConverter;
import io.plaidapp.core.data.api.DesignerNewsSearchConverter;
import io.plaidapp.core.data.api.DesignerNewsService;
import io.plaidapp.core.data.comments.CommentsRemoteDataSource;
import io.plaidapp.core.data.comments.CommentsRepository;
import io.plaidapp.core.data.database.DesignerNewsDatabase;
import io.plaidapp.core.data.database.LoggedInUserDao;
import io.plaidapp.core.data.login.AuthTokenLocalDataSource;
import io.plaidapp.core.data.login.LoginLocalDataSource;
import io.plaidapp.core.data.login.LoginRemoteDataSource;
import io.plaidapp.core.data.login.LoginRepository;
import io.plaidapp.core.data.stories.StoriesRemoteDataSource;
import io.plaidapp.core.data.stories.StoriesRepository;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
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
class DesignerNewsDataModule {

  @Provides
  LoginRepository provideLoginRepository(
    LoginLocalDataSource localSource,
    LoginRemoteDataSource remoteSource
  ) {
    return LoginRepository.getInstance(localSource, remoteSource);
  }

  @Provides
  AuthTokenLocalDataSource provideAuthTokenLocalDataSource(
    SharedPreferences sharedPreferences
  ) {
    return AuthTokenLocalDataSource.getInstance(sharedPreferences);
  }

  @LocalApi
  @Provides
  OkHttpClient providePrivateOkHttpClient(
    OkHttpClient upstream,
    AuthTokenLocalDataSource tokenHolder
  ) {
    upstream =
      upstream
        .newBuilder()
        .addInterceptor(
          new ClientAuthInterceptor(
            tokenHolder,
            BuildConfig.DESIGNER_NEWS_CLIENT_ID
          )
        )
        .build();
    return upstream;
  }

  @Provides
  DesignerNewsService provideDesignerNewsService(
    Lazy<OkHttpClient> client,
    Gson gson
  ) {
    Retrofit retrofit = new Retrofit.Builder()
      .baseUrl(DesignerNewsService.ENDPOINT)
      .addConverterFactory(new DenvelopingConverter(gson))
      .addConverterFactory(new DesignerNewsSearchConverter.Factory())
      .addConverterFactory(GsonConverterFactory.create(gson))
      .addCallAdapterFactory(new CoroutineCallAdapterFactory())
      .build();

    return retrofit.create(DesignerNewsService.class);
  }

  @Provides
  LoggedInUserDao provideLoggedInUserDao(Context context) {
    return DesignerNewsDatabase.getInstance(context).loggedInUserDao();
  }

  @Provides
  StoriesRepository provideStoriesRepository(
    StoriesRemoteDataSource storiesRemoteDataSource
  ) {
    return StoriesRepository.getInstance(storiesRemoteDataSource);
  }

  @Provides
  StoriesRemoteDataSource provideStoriesRemoteDataSource(
    DesignerNewsService service
  ) {
    return StoriesRemoteDataSource.getInstance(service);
  }

  @Provides
  CommentsRepository provideCommentsRepository(
    CommentsRemoteDataSource dataSource
  ) {
    return CommentsRepository.getInstance(dataSource);
  }
}
