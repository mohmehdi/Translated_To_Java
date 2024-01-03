package io.plaidapp.designernews.ui.story;

import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import io.plaidapp.core.data.CoroutinesContextProvider;
import io.plaidapp.designernews.domain.GetCommentsWithRepliesAndUsersUseCase;
import io.plaidapp.designernews.domain.GetStoryUseCase;
import io.plaidapp.designernews.domain.PostReplyUseCase;
import io.plaidapp.designernews.domain.PostStoryCommentUseCase;
import io.plaidapp.designernews.domain.UpvoteCommentUseCase;
import io.plaidapp.designernews.domain.UpvoteStoryUseCase;

public class StoryViewModelFactory implements ViewModelProvider.Factory {

    private long storyId;
    private GetStoryUseCase getStoryUseCase;
    private PostStoryCommentUseCase postStoryComment;
    private PostReplyUseCase postReply;
    private GetCommentsWithRepliesAndUsersUseCase getCommentsWithRepliesAndUsersUseCase;
    private UpvoteStoryUseCase upvoteStoryUseCase;
    private UpvoteCommentUseCase upvoteCommentUseCase;
    private CoroutinesContextProvider contextProvider;

    public StoryViewModelFactory(long storyId, GetStoryUseCase getStoryUseCase,
                                 PostStoryCommentUseCase postStoryComment, PostReplyUseCase postReply,
                                 GetCommentsWithRepliesAndUsersUseCase getCommentsWithRepliesAndUsersUseCase,
                                 UpvoteStoryUseCase upvoteStoryUseCase, UpvoteCommentUseCase upvoteCommentUseCase,
                                 CoroutinesContextProvider contextProvider) {
        this.storyId = storyId;
        this.getStoryUseCase = getStoryUseCase;
        this.postStoryComment = postStoryComment;
        this.postReply = postReply;
        this.getCommentsWithRepliesAndUsersUseCase = getCommentsWithRepliesAndUsersUseCase;
        this.upvoteStoryUseCase = upvoteStoryUseCase;
        this.upvoteCommentUseCase = upvoteCommentUseCase;
        this.contextProvider = contextProvider;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends ViewModel> T create(Class<T> modelClass) {
        if (modelClass != StoryViewModel.class) {
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
        return (T) new StoryViewModel(storyId, getStoryUseCase, postStoryComment, postReply,
                getCommentsWithRepliesAndUsersUseCase, upvoteStoryUseCase, upvoteCommentUseCase,
                contextProvider);
    }
}