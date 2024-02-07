package io.plaidapp.designernews.dagger;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProviders;
import dagger.Module;
import dagger.Provides;
import io.plaidapp.core.dagger.CoreDataModule;
import io.plaidapp.core.dagger.MarkdownModule;
import io.plaidapp.core.dagger.SharedPreferencesModule;
import io.plaidapp.core.dagger.designernews.DesignerNewsDataModule;
import io.plaidapp.core.data.CoroutinesDispatcherProvider;
import io.plaidapp.core.data.DataModule;
import io.plaidapp.designernews.data.api.DNService;
import io.plaidapp.designernews.data.comments.CommentsRemoteDataSource;
import io.plaidapp.designernews.data.comments.CommentsRepository;
import io.plaidapp.designernews.data.users.UserRemoteDataSource;
import io.plaidapp.designernews.data.users.UserRepository;
import io.plaidapp.designernews.data.votes.VotesRemoteDataSource;
import io.plaidapp.designernews.data.votes.VotesRepository;
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
    DataModule.class,
    MarkdownModule.class,
    SharedPreferencesModule.class,
  }
)
public class StoryModule {

  private final long storyId;
  private final StoryActivity activity;

  public StoryModule(long storyId, StoryActivity activity) {
    this.storyId = storyId;
    this.activity = activity;
  }

  @Provides
  public LoginViewModel provideLoginViewModel(
    DesignerNewsViewModelFactory factory
  ) {
    return ViewModelProviders.of(activity, factory).get(LoginViewModel.class);
  }

  @Provides
  public StoryViewModel provideStoryViewModel(StoryViewModelFactory factory) {
    return ViewModelProviders.of(activity, factory).get(StoryViewModel.class);
  }

  @Provides
  public StoryViewModelFactory provideStoryViewModelFactory(
    GetStoryUseCase getStoryUseCase,
    PostStoryCommentUseCase postStoryCommentUseCase,
    PostReplyUseCase postReplyUseCase,
    GetCommentsWithRepliesAndUsersUseCase commentsWithRepliesAndUsersUseCase,
    UpvoteStoryUseCase upvoteStoryUseCase,
    UpvoteCommentUseCase upvoteCommentUseCase,
    CoroutinesDispatcherProvider coroutinesDispatcherProvider
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
  public UserRepository provideUserRepository(UserRemoteDataSource dataSource) {
    return UserRepository.getInstance(dataSource);
  }

  @Provides
  public CommentsRemoteDataSource provideCommentsRemoteDataSource(
    DNService service
  ) {
    return CommentsRemoteDataSource.getInstance(service);
  }

  @Provides
  public VotesRepository provideVotesRepository(
    VotesRemoteDataSource remoteDataSource
  ) {
    return VotesRepository.getInstance(remoteDataSource);
  }

  @Provides
  public CommentsRepository provideCommentsRepository(
    CommentsRemoteDataSource dataSource
  ) {
    return CommentsRepository.getInstance(dataSource);
  }
}
