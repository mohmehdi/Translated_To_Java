




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
import io.plaidapp.core.util.Exhaustive;
import io.plaidapp.designernews.domain.GetCommentsWithRepliesAndUsersUseCase;
import io.plaidapp.designernews.domain.GetStoryUseCase;
import io.plaidapp.designernews.domain.PostReplyUseCase;
import io.plaidapp.designernews.domain.PostStoryCommentUseCase;
import io.plaidapp.designernews.domain.UpvoteCommentUseCase;
import io.plaidapp.designernews.domain.UpvoteStoryUseCase;

public class StoryViewModel extends ViewModel {

    private final MutableLiveData<StoryUiModel> _uiModel = new MutableLiveData<>();
    public final LiveData<StoryUiModel> uiModel = _uiModel;

    public final Story story;

    public StoryViewModel(
            long storyId,
            GetStoryUseCase getStoryUseCase,
            PostStoryCommentUseCase postStoryComment,
            PostReplyUseCase postReply,
            GetCommentsWithRepliesAndUsersUseCase getCommentsWithRepliesAndUsers,
            UpvoteStoryUseCase upvoteStory,
            UpvoteCommentUseCase upvoteComment,
            CoroutinesContextProvider contextProvider) {
        Result<Story> result = getStoryUseCase.getStory(storyId);
        switch (result) {
            case Result.Success success:
                story = success.getData();
                getComments();
                break;
            case Result.Error error:
                throw error.getException();
            default:
                Exhaustive.other(result);
        }
    }

    private final Job parentJob = new Job();

    public void storyUpvoteRequested(long storyId,
                                     @NonNull Executor executor,
                                     @NonNull Runnable onResult) {
        launch(contextProvider.io, parentJob, executor, onResult, upvoteStory, storyId);
    }

    public void commentUpvoteRequested(long commentId,
                                       @NonNull Executor executor,
                                       @NonNull Runnable onResult) {
        launch(contextProvider.io, parentJob, executor, onResult, upvoteComment, commentId);
    }

    public void commentReplyRequested(CharSequence text, long commentId,
                                       @NonNull Executor executor,
                                       @NonNull ResultCallback onResult) {
        launch(contextProvider.io, parentJob, executor, onResult, postReply, text.toString(), commentId);
    }

    public void storyReplyRequested(CharSequence text,
                                     @NonNull Executor executor,
                                     @NonNull ResultCallback onResult) {
        launch(contextProvider.io, parentJob, executor, onResult, postStoryComment, text.toString(), story.getId());
    }

    @Override
    protected void onCleared() {
        parentJob.cancel();
        super.onCleared();
    }

    private void getComments() {
        launch(contextProvider.io, parentJob, result -> {
            if (result instanceof Result.Success) {
                Result.Success success = (Result.Success) result;
                emitUiModel(success.getData());
            }
        }, getCommentsWithRepliesAndUsers, story.getLinks().getComments());
    }

    private void emitUiModel(List<Comment> comments) {
        launch(contextProvider.main, parentJob, result -> {
            if (result instanceof Result.Success) {
                _uiModel.postValue(new StoryUiModel(comments));
            }
        });
    }


    public static class StoryUiModel {
        public final List<Comment> comments;

        public StoryUiModel(List<Comment> comments) {
            this.comments = comments;
        }
    }
}