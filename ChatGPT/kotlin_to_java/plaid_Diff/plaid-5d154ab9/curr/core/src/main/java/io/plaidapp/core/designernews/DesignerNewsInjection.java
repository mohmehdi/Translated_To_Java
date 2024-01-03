import android.content.Context;
import com.google.gson.Gson;
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.experimental.CoroutineCallAdapterFactory;
import io.plaidapp.core.BuildConfig;
import io.plaidapp.core.data.CoroutinesContextProvider;
import io.plaidapp.core.data.api.DenvelopingConverter;
import io.plaidapp.core.designernews.data.api.ClientAuthInterceptor;
import io.plaidapp.core.designernews.data.login.DesignerNewsAuthTokenLocalDataSource;
import io.plaidapp.core.designernews.data.stories.DesignerNewsRepository;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.login.LoginLocalDataSource;
import io.plaidapp.core.designernews.data.login.LoginRemoteDataSource;
import io.plaidapp.core.designernews.data.login.LoginRepository;
import io.plaidapp.core.designernews.data.comments.CommentsRepository;
import io.plaidapp.core.designernews.data.votes.DesignerNewsVotesRepository;
import io.plaidapp.core.designernews.data.votes.VotesRemoteDataSource;
import io.plaidapp.core.designernews.data.comments.DesignerNewsCommentsRemoteDataSource;
import io.plaidapp.core.designernews.data.users.UserRemoteDataSource;
import io.plaidapp.core.designernews.data.users.UserRepository;
import io.plaidapp.core.designernews.domain.CommentsUseCase;
import io.plaidapp.core.designernews.domain.CommentsWithRepliesUseCase;
import io.plaidapp.core.loggingInterceptor;
import io.plaidapp.core.provideCoroutinesContextProvider;
import io.plaidapp.core.provideSharedPreferences;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class Injection {

    public static LoginLocalDataSource provideDesignerNewsLoginLocalDataSource(Context context) {
        SharedPreferences preferences = provideSharedPreferences(
                context,
                LoginLocalDataSource.DESIGNER_NEWS_PREF);
        return new LoginLocalDataSource(preferences);
    }

    public static LoginRepository provideDesignerNewsLoginRepository(Context context) {
        return LoginRepository.getInstance(
                provideDesignerNewsLoginLocalDataSource(context),
                provideDesignerNewsLoginRemoteDataSource(context));
    }

    public static LoginRemoteDataSource provideDesignerNewsLoginRemoteDataSource(Context context) {
        DesignerNewsAuthTokenLocalDataSource tokenHolder = provideDesignerNewsAuthTokenLocalDataSource(context);
        return new LoginRemoteDataSource(tokenHolder, provideDesignerNewsService(tokenHolder));
    }

    private static DesignerNewsAuthTokenLocalDataSource provideDesignerNewsAuthTokenLocalDataSource(
            Context context) {
        return DesignerNewsAuthTokenLocalDataSource.getInstance(
                provideSharedPreferences(
                        context,
                        DesignerNewsAuthTokenLocalDataSource.DESIGNER_NEWS_AUTH_PREF));
    }

    public static DesignerNewsService provideDesignerNewsService(Context context) {
        DesignerNewsAuthTokenLocalDataSource tokenHolder = provideDesignerNewsAuthTokenLocalDataSource(context);
        return provideDesignerNewsService(tokenHolder);
    }

    private static DesignerNewsService provideDesignerNewsService(
            DesignerNewsAuthTokenLocalDataSource authTokenDataSource) {
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(
                        new ClientAuthInterceptor(authTokenDataSource, BuildConfig.DESIGNER_NEWS_CLIENT_ID))
                .addInterceptor(loggingInterceptor)
                .build();
        Gson gson = new Gson();
        return new Retrofit.Builder()
                .baseUrl(DesignerNewsService.ENDPOINT)
                .client(client)
                .addConverterFactory(new DenvelopingConverter(gson))
                .addConverterFactory(GsonConverterFactory.create(gson))
                .addCallAdapterFactory(CoroutineCallAdapterFactory.create())
                .build()
                .create(DesignerNewsService.class);
    }

    public static DesignerNewsRepository provideDesignerNewsRepository(Context context) {
        return provideDesignerNewsRepository(DesignerNewsPrefs.get(context).api);
    }

    private static DesignerNewsRepository provideDesignerNewsRepository(DesignerNewsService service) {
        return DesignerNewsRepository.getInstance(service);
    }

    public static CommentsUseCase provideCommentsUseCase(Context context) {
        DesignerNewsService service = provideDesignerNewsService(context);
        CommentsRepository commentsRepository = provideCommentsRepository(
                provideDesignerNewsCommentsRemoteDataSource(service));
        UserRepository userRepository = provideUserRepository(provideUserRemoteDataSource(service));
        return provideCommentsUseCase(
                provideCommentsWithRepliesUseCase(commentsRepository),
                userRepository,
                provideCoroutinesContextProvider());
    }

    public static CommentsRepository provideCommentsRepository(DesignerNewsCommentsRemoteDataSource dataSource) {
        return CommentsRepository.getInstance(dataSource);
    }

    public static CommentsWithRepliesUseCase provideCommentsWithRepliesUseCase(CommentsRepository commentsRepository) {
        return new CommentsWithRepliesUseCase(commentsRepository);
    }

    public static CommentsUseCase provideCommentsUseCase(
            CommentsWithRepliesUseCase commentsWithCommentsWithRepliesUseCase,
            UserRepository userRepository,
            CoroutinesContextProvider contextProvider) {
        return new CommentsUseCase(commentsWithCommentsWithRepliesUseCase, userRepository, contextProvider);
    }

    private static UserRemoteDataSource provideUserRemoteDataSource(DesignerNewsService service) {
        return new UserRemoteDataSource(service);
    }

    private static UserRepository provideUserRepository(UserRemoteDataSource dataSource) {
        return UserRepository.getInstance(dataSource);
    }

    private static DesignerNewsCommentsRemoteDataSource provideDesignerNewsCommentsRemoteDataSource(DesignerNewsService service) {
        return DesignerNewsCommentsRemoteDataSource.getInstance(service);
    }

    public static DesignerNewsVotesRepository provideDesignerNewsVotesRepository(Context context) {
        return provideDesignerNewsVotesRepository(
                provideVotesRemoteDataSource(provideDesignerNewsService(context)),
                provideCoroutinesContextProvider()
        );
    }

    private static VotesRemoteDataSource provideVotesRemoteDataSource(DesignerNewsService service) {
        return new VotesRemoteDataSource(service);
    }

    private static DesignerNewsVotesRepository provideDesignerNewsVotesRepository(
            VotesRemoteDataSource remoteDataSource,
            CoroutinesContextProvider contextProvider) {
        return DesignerNewsVotesRepository.getInstance(remoteDataSource, contextProvider);
    }
}