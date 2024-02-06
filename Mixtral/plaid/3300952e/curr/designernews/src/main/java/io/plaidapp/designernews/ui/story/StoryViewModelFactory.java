




package io.plaidapp.designernews.ui.story;

import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.support.annotation.NonNull;
import android.support.annotation.SuppressWarnings;

import io.plaidapp.core.data.CoroutinesContextProvider;
import io.plaidapp.designernews.domain.GetCommentsWithRepliesAndUsersUseCase;
import io.plaidapp.designernews.domain.GetStoryUseCase;
import io.plaidapp.designernews.domain.PostReplyUseCase;
import io.plaidapp.designernews.domain.PostStoryCommentUseCase;
import io.plaidapp.designernews.domain.UpvoteCommentUseCase;
import io.plaidapp.designernews.domain.UpvoteStoryUseCase;

public class StoryViewModelFactory implements ViewModelProvider.Factory {

    private final long storyId;
    private final GetStoryUseCase getStoryUseCase;
    private final PostStoryCommentUseCase postStoryComment;
    private final PostReplyUseCase postReply;
    private final GetCommentsWithRepliesAndUsersUseCase
            getCommentsWithRepliesAndUsersUseCase;
    private final UpvoteStoryUseCase upvoteStoryUseCase;
    private final UpvoteCommentUseCase upvoteCommentUseCase;
    private final CoroutinesContextProvider contextProvider;

    public StoryViewModelFactory(long storyId, GetStoryUseCase getStoryUseCase,
                                  PostStoryCommentUseCase postStoryComment,
                                  PostReplyUseCase postReply,
                                  GetCommentsWithRepliesAndUsersUseCase
                                          getCommentsWithRepliesAndUsersUseCase,
                                  UpvoteStoryUseCase upvoteStoryUseCase,
                                  UpvoteCommentUseCase upvoteCommentUseCase,
                                  CoroutinesContextProvider contextProvider) {
        this.storyId = storyId;
        this.getStoryUseCase = getStoryUseCase;
        this.postStoryComment = postStoryComment;
        this.postReply = postReply;
        this.getCommentsWithRepliesAndUsersUseCase =
                getCommentsWithRepliesAndUsersUseCase;
        this.upvoteStoryUseCase = upvoteStoryUseCase;
        this.upvoteCommentUseCase = upvoteCommentUseCase;
        this.contextProvider = contextProvider;
    }

}