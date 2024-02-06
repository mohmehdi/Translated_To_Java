



package io.plaidapp.designernews.ui.story;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.nhaarman.mockito_kotlin.mock;
import com.nhaarman.mockito_kotlin.whenever;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.stories.model.Story;
import io.plaidapp.core.designernews.data.stories.model.StoryLinks;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.designernews.domain.CommentsWithRepliesAndUsersUseCase;
import io.plaidapp.designernews.domain.GetStoryUseCase;
import io.plaidapp.designernews.domain.PostReplyUseCase;
import io.plaidapp.designernews.domain.PostStoryCommentUseCase;
import io.plaidapp.designernews.domain.UpvoteCommentUseCase;
import io.plaidapp.designernews.domain.UpvoteStoryUseCase;
import io.plaidapp.designernews.flattendCommentsWithReplies;
import io.plaidapp.designernews.reply1;
import io.plaidapp.test.shared.LiveDataTestUtil;
import io.plaidapp.test.shared.provideFakeCoroutinesContextProvider;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class StoryViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private long userId = 5;
    private long storyId = 1345;
    private long commentId = 999;
    private Date createdDate;
    private List<Long> commentIds;
    private StoryLinks storyLinks;
    private Story testStory;

    private GetStoryUseCase getStory;
    private PostStoryCommentUseCase postStoryComment;
    private PostReplyUseCase postComment;
    private CommentsWithRepliesAndUsersUseCase commentsWithRepliesAndUsers;
    private UpvoteStoryUseCase upvoteStory;
    private UpvoteCommentUseCase upvoteComment;


    @Test
    public void loadStory_existsInRepo() {
        StoryViewModel viewModel = withViewModel();

        Assert.assertNotNull(viewModel.getStory());
    }

    @Test(expected = IllegalStateException.class)
    public void loadStory_notInRepo() throws ExecutionException, InterruptedException {
        whenever(getStory.invoke(storyId)).thenReturn(new Result.Error<>(new IllegalStateException()));

        StoryViewModel viewModel = new StoryViewModel(
                storyId,
                getStory,
                postStoryComment,
                postComment,
                commentsWithRepliesAndUsers,
                upvoteStory,
                upvoteComment,
                provideFakeCoroutinesContextProvider()
        );

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> viewModel.loadStory());
        future.get();
        executor.shutdownNow();
    }

    @Test
    public void commentsRequested_whenViewModelCreated() throws ExecutionException, InterruptedException {
        StoryViewModel viewModel = withViewModel();

        Assert.assertEquals(flattendCommentsWithReplies, LiveDataTestUtil.getValue(viewModel.getUiModel()).getComments());
    }

    @Test
    public void upvoteStory_whenUpvoteSuccessful() throws ExecutionException, InterruptedException {
        whenever(upvoteStory.invoke(storyId)).thenReturn(new Result.Success<>(null));

        StoryViewModel viewModel = withViewModel();
        Result<Unit> result = null;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> viewModel.storyUpvoteRequested(storyId, result::setValue));
        future.get();
        executor.shutdownNow();

        Assert.assertEquals(new Result.Success<>(null), result);
    }

    @Test
    public void upvoteStory_whenUpvoteFailed() throws ExecutionException, InterruptedException {
        Result<Unit> response = new Result.Error<>(new IOException("Error upvoting"));
        whenever(upvoteStory.invoke(storyId)).thenReturn(response);

        StoryViewModel viewModel = withViewModel();
        Result<Unit> result = null;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> viewModel.storyUpvoteRequested(storyId, result::setValue));
        future.get();
        executor.shutdownNow();

        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void upvoteComment_whenUpvoteSuccessful() throws ExecutionException, InterruptedException {
        whenever(upvoteComment.invoke(commentId)).thenReturn(new Result.Success<>(null));

        StoryViewModel viewModel = withViewModel();
        Result<Unit> result = null;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> viewModel.commentUpvoteRequested(commentId, result::setValue));
        future.get();
        executor.shutdownNow();

        Assert.assertEquals(new Result.Success<>(null), result);
    }

    @Test
    public void upvoteComment_whenUpvoteFailed() throws ExecutionException, InterruptedException {
        Result<Unit> response = new Result.Error<>(new IOException("Error upvoting"));
        whenever(upvoteComment.invoke(commentId)).thenReturn(response);

        StoryViewModel viewModel = withViewModel();
        Result<Unit> result = null;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> viewModel.commentUpvoteRequested(commentId, result::setValue));
        future.get();
        executor.shutdownNow();

        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void commentReplyRequested_withSuccess() throws ExecutionException, InterruptedException {
        Result<Comment> expected = new Result.Success<>(reply1);
        whenever(postComment.invoke(reply1.getBody(), reply1.getParentCommentId())).thenReturn(expected);

        StoryViewModel viewModel = withViewModel();
        Result<Comment> result = null;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> viewModel.commentReplyRequested(reply1.getBody(), reply1.getParentCommentId(), result::setValue));
        future.get();
        executor.shutdownNow();

        Assert.assertEquals(expected, result);
    }

    @Test
    public void commentReplyRequested_withError() throws ExecutionException, InterruptedException {
        Result<Comment> expected = new Result.Error<>(new IOException("Error"));
        whenever(postComment.invoke(reply1.getBody(), reply1.getParentCommentId())).thenReturn(expected);

        StoryViewModel viewModel = withViewModel();
        Result<Comment> result = null;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> viewModel.commentReplyRequested(reply1.getBody(), reply1.getParentCommentId(), result::setValue));
        future.get();
        executor.shutdownNow();

        Assert.assertEquals(expected, result);
    }

    @Test
    public void storyReplyRequested_withSuccess() throws ExecutionException, InterruptedException {
        Result<Comment> expected = new Result.Success<>(reply1);
        whenever(postStoryComment.invoke(reply1.getBody(), storyId)).thenReturn(expected);

        StoryViewModel viewModel = withViewModel();
        Result<Comment> result = null;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> viewModel.storyReplyRequested(reply1.getBody(), result::setValue));
        future.get();
        executor.shutdownNow();

        Assert.assertEquals(expected, result);
    }

    @Test
    public void storyReplyRequested_withError() throws ExecutionException, InterruptedException {
        Result<Comment> expected = new Result.Error<>(new IOException("Error"));
        whenever(postStoryComment.invoke(reply1.getBody(), storyId)).thenReturn(expected);

        StoryViewModel viewModel = withViewModel();
        Result<Comment> result = null;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> viewModel.storyReplyRequested(reply1.getBody(), result::setValue));
        future.get();
        executor.shutdownNow();

        Assert.assertEquals(expected, result);
    }

    private StoryViewModel withViewModel() throws ExecutionException, InterruptedException {
        whenever(getStory.invoke(storyId)).thenReturn(new Result.Success<>(testStory));
        whenever(commentsWithRepliesAndUsers.invoke(commentIds)).thenReturn(new Result.Success<>(flattendCommentsWithReplies));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<List<Comment>> future = executor.submit(() -> flattendCommentsWithReplies);
        List<Comment> comments = future.get();
        executor.shutdownNow();

        return new StoryViewModel(
                storyId,
                getStory,
                postStoryComment,
                postComment,
                commentsWithRepliesAndUsers,
                upvoteStory,
                upvoteComment,
                provideFakeCoroutinesContextProvider()
        );
    }
}