package io.plaidapp.core.designernews;

import android.content.Context;
import com.google.gson.Gson;
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.experimental.CoroutineCallAdapterFactory;
import io.plaidapp.core.BuildConfig;
import io.plaidapp.core.data.CoroutinesContextProvider;
import io.plaidapp.core.data.api.ClientAuthInterceptor;
import io.plaidapp.core.data.api.DenvelopingConverter;
import io.plaidapp.core.data.api.DesignerNewsAuthTokenLocalDataSource;
import io.plaidapp.core.data.api.DesignerNewsService;
import io.plaidapp.core.data.comments.CommentsRepository;
import io.plaidapp.core.data.comments.DesignerNewsCommentsRemoteDataSource;
import io.plaidapp.core.data.users.UserRemoteDataSource;
import io.plaidapp.core.data.users.UserRepository;
import io.plaidapp.core.data.votes.DesignerNewsVotesRepository;
import io.plaidapp.core.data.votes.VotesRemoteDataSource;
import io.plaidapp.core.designernews.data.api.DesignerNewsAuthTokenLocalDataSourceImpl;
import io.plaidapp.core.designernews.data.api.DesignerNewsRepository;
import io.plaidapp.core.designernews.data.api.DesignerNewsServiceGenerator;
import io.plaidapp.core.designernews.data.api.LoginLocalDataSource;
import io.plaidapp.core.designernews.data.api.LoginRemoteDataSource;
import io.plaidapp.core.designernews.data.api.LoginRepository;
import io.plaidapp.core.designernews.data.comments.CommentsUseCase;
import io.plaidapp.core.designernews.data.comments.CommentsWithRepliesUseCase;
import io.plaidapp.core.designernews.data.users.User;
import io.plaidapp.core.designernews.data.users.UserRepositoryImpl;
import io.plaidapp.core.loggingInterceptor.LoggingInterceptor;
import io.plaidapp.core.provideCoroutinesContextProvider;
import io.plaidapp.core.provideSharedPreferences;
import java.io.File;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@SuppressWarnings("WeakerAccess")
public class Injection {

  public static LoginLocalDataSource provideDesignerNewsLoginLocalDataSource(
    Context context
  ) {
    return new LoginLocalDataSourceImpl(
      provideSharedPreferences(context, LoginLocalDataSource.DESIGNER_NEWS_PREF)
    );
  }

  public static LoginRepository provideDesignerNewsLoginRepository(
    Context context
  ) {
    return LoginRepository.getInstance(
      provideDesignerNewsLoginLocalDataSource(context),
      provideDesignerNewsLoginRemoteDataSource(context)
    );
  }

  public static LoginRemoteDataSource provideDesignerNewsLoginRemoteDataSource(
    Context context
  ) {
    DesignerNewsAuthTokenLocalDataSource tokenHolder = provideDesignerNewsAuthTokenLocalDataSource(
      context
    );
    return new LoginRemoteDataSource(
      tokenHolder,
      provideDesignerNewsService(tokenHolder)
    );
  }

  public static DesignerNewsAuthTokenLocalDataSource provideDesignerNewsAuthTokenLocalDataSource(
    Context context
  ) {
    return new DesignerNewsAuthTokenLocalDataSourceImpl(
      provideSharedPreferences(
        context,
        DesignerNewsAuthTokenLocalDataSource.DESIGNER_NEWS_AUTH_PREF
      )
    );
  }

  public static DesignerNewsService provideDesignerNewsService(
    Context context
  ) {
    DesignerNewsAuthTokenLocalDataSource tokenHolder = provideDesignerNewsAuthTokenLocalDataSource(
      context
    );
    return provideDesignerNewsService(tokenHolder);
  }

  private static DesignerNewsService provideDesignerNewsService(
    DesignerNewsAuthTokenLocalDataSource authTokenDataSource
  ) {
    OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
    clientBuilder.addInterceptor(
      new ClientAuthInterceptor(
        authTokenDataSource,
        BuildConfig.DESIGNER_NEWS_CLIENT_ID
      )
    );
    clientBuilder.addInterceptor(
      new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
    );
    OkHttpClient client = clientBuilder.build();
    Gson gson = new Gson();
    Retrofit retrofit = new Retrofit.Builder()
      .baseUrl(DesignerNewsService.ENDPOINT)
      .client(client)
      .addConverterFactory(new DenvelopingConverter(gson))
      .addConverterFactory(GsonConverterFactory.create(gson))
      .addCallAdapterFactory(CoroutineCallAdapterFactory.create())
      .build();
    return retrofit.create(DesignerNewsService.class);
  }

  public static DesignerNewsRepository provideDesignerNewsRepository(
    Context context
  ) {
    return provideDesignerNewsRepository(DesignerNewsPrefs.get(context).api);
  }

  private static DesignerNewsRepository provideDesignerNewsRepository(
    DesignerNewsService service
  ) {
    return DesignerNewsRepository.getInstance(
      DesignerNewsServiceGenerator.createService(DesignerNewsService.class, api)
    );
  }

  public static CommentsUseCase provideCommentsUseCase(Context context) {
    DesignerNewsService service = provideDesignerNewsService(context);
    CommentsRepository commentsRepository = provideCommentsRepository(
      provideDesignerNewsCommentsRemoteDataSource(service)
    );
    UserRepository userRepository = provideUserRepository(
      provideUserRemoteDataSource(service)
    );
    return provideCommentsUseCase(
      provideCommentsWithRepliesUseCase(commentsRepository),
      userRepository,
      provideCoroutinesContextProvider()
    );
  }

  public static CommentsRepository provideCommentsRepository(
    DesignerNewsCommentsRemoteDataSource dataSource
  ) {
    return CommentsRepository.getInstance(dataSource);
  }

  public static CommentsWithRepliesUseCase provideCommentsWithRepliesUseCase(
    CommentsRepository commentsRepository
  ) {
    return new CommentsWithRepliesUseCase(commentsRepository);
  }

  public static CommentsUseCase provideCommentsUseCase(
    CommentsWithRepliesUseCase commentsWithCommentsWithRepliesUseCase,
    UserRepository userRepository,
    CoroutinesContextProvider contextProvider
  ) {
    return new CommentsUseCase(
      commentsWithCommentsWithRepliesUseCase,
      userRepository,
      contextProvider
    );
  }

  public static UserRemoteDataSource provideUserRemoteDataSource(
    DesignerNewsService service
  ) {
    return new UserRemoteDataSource(service);
  }

  public static UserRepository provideUserRepository(
    UserRemoteDataSource dataSource
  ) {
    return new UserRepositoryImpl(dataSource);
  }

  public static DesignerNewsCommentsRemoteDataSource provideDesignerNewsCommentsRemoteDataSource(
    DesignerNewsService service
  ) {
    return DesignerNewsCommentsRemoteDataSource.getInstance(service);
  }

  public static DesignerNewsVotesRepository provideDesignerNewsVotesRepository(
    Context context
  ) {
    return provideDesignerNewsVotesRepository(
      provideVotesRemoteDataSource(provideDesignerNewsService(context)),
      provideCoroutinesContextProvider()
    );
  }

  public static VotesRemoteDataSource provideVotesRemoteDataSource(
    DesignerNewsService service
  ) {
    return new VotesRemoteDataSource(service);
  }

  public static DesignerNewsVotesRepository provideDesignerNewsVotesRepository(
    VotesRemoteDataSource remoteDataSource,
    CoroutinesContextProvider contextProvider
  ) {
    return DesignerNewsVotesRepository.getInstance(
      remoteDataSource,
      contextProvider
    );
  }
}
