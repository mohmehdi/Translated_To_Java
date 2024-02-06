




package io.plaidapp.designernews;

import android.content.Context;

import io.plaidapp.core.CoroutinesContextProvider;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.comments.CommentsRemoteDataSource;
import io.plaidapp.core.designernews.data.comments.CommentsRepository;
import io.plaidapp.core.designernews.data.users.UserRemoteDataSource;
import io.plaidapp.core.designernews.data.users.UserRepository;
import io.plaidapp.core.designernews.data.votes.VotesRemoteDataSource;
import io.plaidapp.core.designernews.data.votes.VotesRepository;
import io.plaidapp.core.designernews.provideCommentsRepository;
import io.plaidapp.core.designernews.provideDesignerNewsService;
import io.plaidapp.core.designernews.provideLoginRepository;
import io.plaidapp.core.designernews.provideStoriesRepository;
import io.plaidapp.core.provideCoroutinesContextProvider;
import io.plaidapp.designernews.data.comments.CommentsWithRepliesAndUsersUseCase;
import io.plaidapp.designernews.data.comments.CommentsWithRepliesUseCase;
import io.plaidapp.designernews.data.GetCommentsWithRepliesAndUsersUseCase;
import io.plaidapp.designernews.data.GetCommentsWithRepliesUseCase;
import io.plaidapp.designernews.data.GetStoryUseCase;
import io.plaidapp.designernews.data.PostReplyUseCase;
import io.plaidapp.designernews.data.PostStoryCommentUseCase;
import io.plaidapp.designernews.data.UpvoteCommentUseCase;
import io.plaidapp.designernews.data.UpvoteStoryUseCase;
import io.plaidapp.designernews.domain.DesignerNewsViewModelFactory;
import io.plaidapp.designernews.domain.StoryViewModelFactory;
import io.plaidapp.designernews.ui.DesignerNewsViewModel;
import io.plaidapp.designernews.ui.story.StoryViewModel;

public class Injection {

    public static DesignerNewsViewModelFactory provideViewModelFactory(Context context) {
        return new DesignerNewsViewModelFactory(
                provideLoginRepository(context),
                provideCoroutinesContextProvider()
        );
    }

    public static StoryViewModelFactory provideStoryViewModelFactory(long storyId, Context context) {
        return new StoryViewModelFactory(
                storyId,
                provideGetStoryUseCase(context),
                providePostStoryCommentUseCase(context),
                providePostReplyUseCase(context),
                provideCommentsWithRepliesAndUsersUseCase(context),
                provideUpvoteStoryUseCase(context),
                provideUpvoteCommentUseCase(context),
                provideCoroutinesContextProvider()
        );
    }

    public static GetStoryUseCase provideGetStoryUseCase(Context context) {
        return new GetStoryUseCase(provideStoriesRepository(context));
    }

    public static UpvoteStoryUseCase provideUpvoteStoryUseCase(Context context) {
        LoginRepository loginRepository = provideLoginRepository(context);
        VotesRepository votesRepository = provideVotesRepository(context);
        return new UpvoteStoryUseCase(loginRepository, votesRepository);
    }

    public static UpvoteCommentUseCase provideUpvoteCommentUseCase(Context context) {
        LoginRepository loginRepository = provideLoginRepository(context);
        VotesRepository votesRepository = provideVotesRepository(context);
        return new UpvoteCommentUseCase(loginRepository, votesRepository);
    }

    public static CommentsWithRepliesAndUsersUseCase provideCommentsWithRepliesAndUsersUseCase(Context context) {
        DesignerNewsService service = provideDesignerNewsService(context);
        CommentsRepository commentsRepository = provideCommentsRepository(
                provideCommentsRemoteDataSource(service)
        );
        UserRepository userRepository = provideUserRepository(provideUserRemoteDataSource(service));
        return provideCommentsWithRepliesAndUsersUseCase(
                provideCommentsWithRepliesUseCase(commentsRepository),
                userRepository
        );
    }

    public static CommentsWithRepliesUseCase provideCommentsWithRepliesUseCase(CommentsRepository commentsRepository) {
        return new CommentsWithRepliesUseCase(commentsRepository);
    }

    public static CommentsWithRepliesAndUsersUseCase provideCommentsWithRepliesAndUsersUseCase(
            GetCommentsWithRepliesUseCase commentsWithGetCommentsWithReplies,
            UserRepository userRepository) {
        return new CommentsWithRepliesAndUsersUseCase(commentsWithGetCommentsWithReplies, userRepository);
    }

    private static UserRemoteDataSource provideUserRemoteDataSource(DesignerNewsService service) {
        return new UserRemoteDataSource(service);
    }

    private static UserRepository provideUserRepository(UserRemoteDataSource dataSource) {
        return UserRepository.getInstance(dataSource);
    }

    private static CommentsRemoteDataSource provideCommentsRemoteDataSource(DesignerNewsService service) {
        return CommentsRemoteDataSource.getInstance(service);
    }

    public static PostReplyUseCase providePostReplyUseCase(Context context) {
        DesignerNewsService service = provideDesignerNewsService(context);
        CommentsRepository commentsRepository = provideCommentsRepository(
                provideCommentsRemoteDataSource(service)
        );
        LoginRepository loginRepository = provideLoginRepository(context);
        return new PostReplyUseCase(commentsRepository, loginRepository);
    }

    public static PostStoryCommentUseCase providePostStoryCommentUseCase(Context context) {
        DesignerNewsService service = provideDesignerNewsService(context);
        CommentsRepository commentsRepository = provideCommentsRepository(
                provideCommentsRemoteDataSource(service)
        );
        LoginRepository loginRepository = provideLoginRepository(context);
        return new PostStoryCommentUseCase(commentsRepository, loginRepository);
    }

    public static VotesRepository provideVotesRepository(Context context) {
        return provideVotesRepository(
                provideVotesRemoteDataSource(provideDesignerNewsService(context))
        );
    }

    private static VotesRemoteDataSource provideVotesRemoteDataSource(DesignerNewsService service) {
        return new VotesRemoteDataSource(service);
    }

    private static VotesRepository provideVotesRepository(VotesRemoteDataSource remoteDataSource) {
        return VotesRepository.getInstance(remoteDataSource);
    }
}