package io.plaidapp.core.designernews;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.experimental.CoroutineCallAdapterFactory;
import io.plaidapp.core.BuildConfig;
import io.plaidapp.core.CoroutinesContextProvider;
import io.plaidapp.core.DataProvider;
import io.plaidapp.core.DesignerNewsPrefs;
import io.plaidapp.core.OkHttpClientProvider;
import io.plaidapp.core.data.api.ClientAuthInterceptor;
import io.plaidapp.core.data.api.DenvelopingConverter;
import io.plaidapp.core.data.api.DesignerNewsService;
import io.plaidapp.core.data.comments.CommentsRepository;
import io.plaidapp.core.data.comments.DesignerNewsCommentsRemoteDataSource;
import io.plaidapp.core.data.login.DesignerNewsAuthTokenLocalDataSource;
import io.plaidapp.core.data.login.LoginLocalDataSource;
import io.plaidapp.core.data.login.LoginRemoteDataSource;
import io.plaidapp.core.data.login.LoginRepository;
import io.plaidapp.core.data.stories.DesignerNewsRepository;
import io.plaidapp.core.data.users.UserRemoteDataSource;
import io.plaidapp.core.data.users.UserRepository;
import io.plaidapp.core.data.votes.DesignerNewsVotesRepository;
import io.plaidapp.core.domain.CommentsUseCase;
import io.plaidapp.core.domain.CommentsWithRepliesUseCase;
import io.plaidapp.core.loggingInterceptor;
import io.plaidapp.core.providers.SharedPreferencesProvider;
import java.util.concurrent.Executor;

@JvmName("Injection")
public class DesignerNewsInjection {

  public static LoginLocalDataSource provideDesignerNewsLoginLocalDataSource(
    Context context
  ) {
    SharedPreferences preferences = provideSharedPreferences(
      context,
      LoginLocalDataSource.DESIGNER_NEWS_PREF
    );
    return new DesignerNewsAuthTokenLocalDataSource(preferences);
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
    return DesignerNewsAuthTokenLocalDataSource.getInstance(
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
    OkHttpClient client = OkHttpClientProvider.createClient(
      new ClientAuthInterceptor(
        authTokenDataSource,
        BuildConfig.DESIGNER_NEWS_CLIENT_ID
      ),
      loggingInterceptor
    );
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
    return DesignerNewsRepository.getInstance(service);
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
    return UserRepository.getInstance(dataSource);
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
