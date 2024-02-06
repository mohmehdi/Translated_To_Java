package io.plaidapp.designernews.ui.story;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.List;
import java.util.concurrent.Executor;

import io.plaidapp.core.data.CoroutinesContextProvider;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.stories.model.Story;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.designernews.domain.CommentsWithRepliesAndUsersUseCase;
import io.plaidapp.designernews.domain.GetStoryUseCase;
import io.plaidapp.designernews.domain.PostReplyUseCase;
import io.plaidapp.designernews.domain.PostStoryCommentUseCase;
import io.plaidapp.designernews.domain.UpvoteCommentUseCase;
import io.plaidapp.designernews.domain.UpvoteStoryUseCase;

public class StoryViewModel extends ViewModel {

    private final MutableLiveData<StoryUiModel> _uiModel = new MutableLiveData<>();
    public LiveData<StoryUiModel> uiModel = _uiModel;

    private final Story story;
    private final GetStoryUseCase getStoryUseCase;
    private final PostStoryCommentUseCase postStoryComment;
    private final PostReplyUseCase postReply;
    private final CommentsWithRepliesAndUsersUseCase commentsWithRepliesAndUsers;
    private final UpvoteStoryUseCase upvoteStory;
    private final UpvoteCommentUseCase upvoteComment;
    private final CoroutinesContextProvider contextProvider;
    private final Executor executor;

    public StoryViewModel(@NonNull Long storyId,
                          @NonNull GetStoryUseCase getStoryUseCase,
                          @NonNull PostStoryCommentUseCase postStoryComment,
                          @NonNull PostReplyUseCase postReply,
                          @NonNull CommentsWithRepliesAndUsersUseCase commentsWithRepliesAndUsers,
                          @NonNull UpvoteStoryUseCase upvoteStory,
                          @NonNull UpvoteCommentUseCase upvoteComment,
                          @NonNull CoroutinesContextProvider contextProvider,
                          @NonNull Executor executor) {
        this.getStoryUseCase = getStoryUseCase;
        this.postStoryComment = postStoryComment;
        this.postReply = postReply;
        this.commentsWithRepliesAndUsers = commentsWithRepliesAndUsers;
        this.upvoteStory = upvoteStory;
        this.upvoteComment = upvoteComment;
        this.contextProvider = contextProvider;
        this.executor = executor;

        Result<Story> result = getStoryUseCase.getStory(storyId);
        if (result instanceof Result.Success) {
            story = result.getData();
            getComments();
        } else {
            throw result.getException();
        }
    }

    public void storyUpvoteRequested(Long storyId, @NonNull final ResultCallback<Unit> onResult) {
        executor.execute(() -> {
            Result<Unit> result = upvoteStory.upvoteStory(storyId);
            contextProvider.io().execute(() -> onResult.onResult(result));
        });
    }

    public void commentUpvoteRequested(Long commentId, @NonNull final ResultCallback<Unit> onResult) {
        executor.execute(() -> {
            Result<Unit> result = upvoteComment.upvoteComment(commentId);
            contextProvider.io().execute(() -> onResult.onResult(result));
        });
    }

    public void commentReplyRequested(@NonNull String text, Long commentId,
                                      @NonNull final ResultCallback<Comment> onResult) {
        executor.execute(() -> {
            Result<Comment> result = postReply.postReply(text, commentId);
            contextProvider.main().execute(() -> onResult.onResult(result));
        });
    }

    public void storyReplyRequested(@NonNull String text,
                                    @NonNull final ResultCallback<Comment> onResult) {
        executor.execute(() -> {
            Result<Comment> result = postStoryComment.postStoryComment(text, story.getId());
            contextProvider.main().execute(() -> onResult.onResult(result));
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
    }

    private void getComments() {
        executor.execute(() -> {
            Result<List<Comment>> result = commentsWithRepliesAndUsers.getComments(story.getLinks().getComments());
            if (result instanceof Result.Success) {
                emitUiModel(result.getData());
            }
        });
    }

    private void emitUiModel(@NonNull final List<Comment> comments) {
        contextProvider.main().execute(() -> _uiModel.postValue(new StoryUiModel(comments)));
    }


    public static class StoryUiModel {
        private final List<Comment> comments;

        public StoryUiModel(List<Comment> comments) {
            this.comments = comments;
        }

    }
}