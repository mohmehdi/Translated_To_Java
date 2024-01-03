package io.plaidapp.core.dagger.designernews;

import android.content.Context;
import android.content.SharedPreferences;

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
import io.plaidapp.core.designernews.data.api.ClientAuthInterceptor;
import io.plaidapp.core.designernews.data.api.DesignerNewsSearchConverter;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.database.DesignerNewsDatabase;
import io.plaidapp.core.designernews.data.database.LoggedInUserDao;
import io.plaidapp.core.designernews.data.login.AuthTokenLocalDataSource;
import io.plaidapp.core.designernews.data.login.LoginLocalDataSource;
import io.plaidapp.core.designernews.data.login.LoginRemoteDataSource;
import io.plaidapp.core.designernews.data.login.LoginRepository;
import io.plaidapp.core.designernews.data.stories.StoriesRemoteDataSource;
import io.plaidapp.core.designernews.data.stories.StoriesRepository;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import javax.inject.Qualifier;

import kotlin.annotation.AnnotationRetention.BINARY;

@Retention(BINARY)
@Qualifier
private @interface LocalApi {}

@Module(
    includes = {
        SharedPreferencesModule.class,
        CoreDataModule.class,
        CoroutinesDispatcherProviderModule.class
    }
)
public class DesignerNewsDataModule {

    @Provides
    public LoginRepository provideLoginRepository(
        LoginLocalDataSource localSource,
        LoginRemoteDataSource remoteSource
    ) {
        return LoginRepository.getInstance(localSource, remoteSource);
    }

    @Provides
    public AuthTokenLocalDataSource provideAuthTokenLocalDataSource(
        SharedPreferences sharedPreferences
    ) {
        return AuthTokenLocalDataSource.getInstance(sharedPreferences);
    }

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
    public DesignerNewsService provideDesignerNewsService(
        @LocalApi Lazy<OkHttpClient> client,
        Gson gson
    ) {
        return new Retrofit.Builder()
            .baseUrl(DesignerNewsService.ENDPOINT)
            .callFactory(client.get()::newCall)
            .addConverterFactory(new DenvelopingConverter(gson))
            .addConverterFactory(new DesignerNewsSearchConverter.Factory())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .addCallAdapterFactory(CoroutineCallAdapterFactory.create())
            .build()
            .create(DesignerNewsService.class);
    }

    @Provides
    public LoggedInUserDao provideLoggedInUserDao(Context context) {
        return DesignerNewsDatabase.getInstance(context).loggedInUserDao();
    }

    @Provides
    public StoriesRepository provideStoriesRepository(
        StoriesRemoteDataSource storiesRemoteDataSource
    ) {
        return StoriesRepository.getInstance(storiesRemoteDataSource);
    }

    @Provides
    public StoriesRemoteDataSource provideStoriesRemoteDataSource(DesignerNewsService service) {
        return StoriesRemoteDataSource.getInstance(service);
    }
}