package io.plaidapp.designernews.dagger;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProviders;
import dagger.Module;
import dagger.Provides;
import io.plaidapp.core.dagger.CoreDataModule;
import io.plaidapp.core.dagger.DesignerNewsDataModule;
import io.plaidapp.core.dagger.MarkdownModule;
import io.plaidapp.core.dagger.SharedPreferencesModule;
import io.plaidapp.core.data.CoroutinesDispatcherProvider;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.comments.CommentsRemoteDataSource;
import io.plaidapp.core.designernews.data.users.UserRemoteDataSource;
import io.plaidapp.core.designernews.data.users.UserRepository;
import io.plaidapp.core.designernews.data.votes.VotesRemoteDataSource;
import io.plaidapp.core.designernews.data.votes.VotesRepository;
import io.plaidapp.designernews.domain.GetCommentsWithRepliesAndUsersUseCase;
import io.plaidapp.designernews.domain.GetStoryUseCase;
import io.plaidapp.designernews.domain.PostReplyUseCase;
import io.plaidapp.designernews.domain.PostStoryCommentUseCase;
import io.plaidapp.designernews.domain.UpvoteCommentUseCase;
import io.plaidapp.designernews.domain.UpvoteStoryUseCase;
import io.plaidapp.designernews.ui.DesignerNewsViewModelFactory;
import io.plaidapp.designernews.ui.LoginViewModel;
import io.plaidapp.designernews.ui.story.StoryActivity;
import io.plaidapp.designernews.ui.story.StoryViewModel;
import io.plaidapp.designernews.ui.story.StoryViewModelFactory;

@Module(
  includes = {
    CoreDataModule.class,
    DesignerNewsDataModule.class,
    MarkdownModule.class,
    SharedPreferencesModule.class,
  }
)
public class StoryModule {

  private final long storyId;
  private final StoryActivity activity;

  public StoryModule(long storyId, @NonNull StoryActivity activity) {
    this.storyId = storyId;
    this.activity = activity;
  }

  @Provides
  public LoginViewModel provideLoginViewModel(
    @NonNull DesignerNewsViewModelFactory factory
  ) {
    return ViewModelProviders.of(activity, factory).get(LoginViewModel.class);
  }

  @Provides
  public StoryViewModel provideStoryViewModel(
    @NonNull StoryViewModelFactory factory
  ) {
    return ViewModelProviders.of(activity, factory).get(StoryViewModel.class);
  }

  @Provides
  public StoryViewModelFactory provideStoryViewModelFactory(
    @NonNull GetStoryUseCase getStoryUseCase,
    @NonNull PostStoryCommentUseCase postStoryCommentUseCase,
    @NonNull PostReplyUseCase postReplyUseCase,
    @NonNull GetCommentsWithRepliesAndUsersUseCase commentsWithRepliesAndUsersUseCase,
    @NonNull UpvoteStoryUseCase upvoteStoryUseCase,
    @NonNull UpvoteCommentUseCase upvoteCommentUseCase,
    @NonNull CoroutinesDispatcherProvider coroutinesDispatcherProvider
  ) {
    return new StoryViewModelFactory(
      storyId,
      getStoryUseCase,
      postStoryCommentUseCase,
      postReplyUseCase,
      commentsWithRepliesAndUsersUseCase,
      upvoteStoryUseCase,
      upvoteCommentUseCase,
      coroutinesDispatcherProvider
    );
  }

  @Provides
  public UserRepository provideUserRepository(
    @NonNull UserRemoteDataSource dataSource
  ) {
    return UserRepository.getInstance(dataSource);
  }

  @Provides
  public CommentsRemoteDataSource provideCommentsRemoteDataSource(
    @NonNull DesignerNewsService service
  ) {
    return CommentsRemoteDataSource.getInstance(service);
  }

  @Provides
  public VotesRepository provideVotesRepository(
    @NonNull VotesRemoteDataSource remoteDataSource
  ) {
    return VotesRepository.getInstance(remoteDataSource);
  }
}
