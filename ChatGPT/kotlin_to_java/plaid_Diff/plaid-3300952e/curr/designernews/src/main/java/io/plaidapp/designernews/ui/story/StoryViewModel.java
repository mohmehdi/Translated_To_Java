package io.plaidapp.designernews.ui.story;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import io.plaidapp.core.data.CoroutinesContextProvider;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.stories.model.Story;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.core.util.exhaustive;
import io.plaidapp.designernews.domain.GetCommentsWithRepliesAndUsersUseCase;
import io.plaidapp.designernews.domain.GetStoryUseCase;
import io.plaidapp.designernews.domain.PostReplyUseCase;
import io.plaidapp.designernews.domain.PostStoryCommentUseCase;
import io.plaidapp.designernews.domain.UpvoteCommentUseCase;
import io.plaidapp.designernews.domain.UpvoteStoryUseCase;
import kotlinx.coroutines.experimental.Job;
import kotlinx.coroutines.experimental.launch;
import kotlinx.coroutines.experimental.withContext;

public class StoryViewModel extends ViewModel {

    private MutableLiveData<StoryUiModel> _uiModel = new MutableLiveData<>();
    public LiveData<StoryUiModel> uiModel = _uiModel;

    private Story story;

    private Job parentJob = new Job();

    private GetStoryUseCase getStoryUseCase;
    private PostStoryCommentUseCase postStoryComment;
    private PostReplyUseCase postReply;
    private GetCommentsWithRepliesAndUsersUseCase getCommentsWithRepliesAndUsers;
    private UpvoteStoryUseCase upvoteStory;
    private UpvoteCommentUseCase upvoteComment;
    private CoroutinesContextProvider contextProvider;

    public StoryViewModel(
            long storyId,
            GetStoryUseCase getStoryUseCase,
            PostStoryCommentUseCase postStoryComment,
            PostReplyUseCase postReply,
            GetCommentsWithRepliesAndUsersUseCase getCommentsWithRepliesAndUsers,
            UpvoteStoryUseCase upvoteStory,
            UpvoteCommentUseCase upvoteComment,
            CoroutinesContextProvider contextProvider) {
        this.getStoryUseCase = getStoryUseCase;
        this.postStoryComment = postStoryComment;
        this.postReply = postReply;
        this.getCommentsWithRepliesAndUsers = getCommentsWithRepliesAndUsers;
        this.upvoteStory = upvoteStory;
        this.upvoteComment = upvoteComment;
        this.contextProvider = contextProvider;

        Result<Story> result = getStoryUseCase.invoke(storyId);
        if (result instanceof Result.Success) {
            story = ((Result.Success<Story>) result).getData();
            getComments();
        } else if (result instanceof Result.Error) {
            throw ((Result.Error) result).getException();
        }
        exhaustive();
    }

    public void storyUpvoteRequested(long storyId, final OnResultListener<Unit> onResult) {
        launch(contextProvider.getIO(), parentJob, new Function2<CoroutineScope, Continuation<? super Unit>, Object>() {
            @Override
            public Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
                Result<Unit> result = upvoteStory.invoke(storyId);
                withContext(contextProvider.getIO(), new Function2<CoroutineScope, Continuation<? super Unit>, Object>() {
                    @Override
                    public Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
                        onResult.onResult(result);
                        return null;
                    }
                });
                return null;
            }
        });
    }

    public void commentUpvoteRequested(long commentId, final OnResultListener<Unit> onResult) {
        launch(contextProvider.getIO(), parentJob, new Function2<CoroutineScope, Continuation<? super Unit>, Object>() {
            @Override
            public Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
                Result<Unit> result = upvoteComment.invoke(commentId);
                withContext(contextProvider.getIO(), new Function2<CoroutineScope, Continuation<? super Unit>, Object>() {
                    @Override
                    public Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
                        onResult.onResult(result);
                        return null;
                    }
                });
                return null;
            }
        });
    }

    public void commentReplyRequested(
            CharSequence text,
            long commentId,
            final OnResultListener<Comment> onResult) {
        launch(contextProvider.getIO(), parentJob, new Function2<CoroutineScope, Continuation<? super Unit>, Object>() {
            @Override
            public Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
                Result<Comment> result = postReply.invoke(text.toString(), commentId);
                withContext(contextProvider.getMain(), new Function2<CoroutineScope, Continuation<? super Unit>, Object>() {
                    @Override
                    public Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
                        onResult.onResult(result);
                        return null;
                    }
                });
                return null;
            }
        });
    }

    public void storyReplyRequested(
            CharSequence text,
            final OnResultListener<Comment> onResult) {
        launch(contextProvider.getIO(), parentJob, new Function2<CoroutineScope, Continuation<? super Unit>, Object>() {
            @Override
            public Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
                Result<Comment> result = postStoryComment.invoke(text.toString(), story.getId());
                withContext(contextProvider.getMain(), new Function2<CoroutineScope, Continuation<? super Unit>, Object>() {
                    @Override
                    public Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
                        onResult.onResult(result);
                        return null;
                    }
                });
                return null;
            }
        });
    }

    @Override
    protected void onCleared() {
        parentJob.cancel();
        super.onCleared();
    }

    private void getComments() {
        launch(contextProvider.getIO(), parentJob, new Function2<CoroutineScope, Continuation<? super Unit>, Object>() {
            @Override
            public Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
                Result<List<Comment>> result = getCommentsWithRepliesAndUsers.invoke(story.getLinks().getComments());
                if (result instanceof Result.Success) {
                    emitUiModel(((Result.Success<List<Comment>>) result).getData());
                }
                return null;
            }
        });
    }

    private void emitUiModel(final List<Comment> comments) {
        launch(contextProvider.getMain(), parentJob, new Function2<CoroutineScope, Continuation<? super Unit>, Object>() {
            @Override
            public Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
                _uiModel.setValue(new StoryUiModel(comments));
                return null;
            }
        });
    }

    public interface OnResultListener<T> {
        void onResult(Result<T> result);
    }
}

public class StoryUiModel {
    private List<Comment> comments;

    public StoryUiModel(List<Comment> comments) {
        this.comments = comments;
    }

    public List<Comment> getComments() {
        return comments;
    }
}