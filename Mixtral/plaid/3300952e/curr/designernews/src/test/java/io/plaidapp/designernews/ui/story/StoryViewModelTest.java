




package io.plaidapp.designernews.ui.story;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.nhaarman.mockito_kotlin.mock;
import com.nhaarman.mockito_kotlin.whenever;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.stories.model.Story;
import io.plaidapp.core.designernews.data.stories.model.StoryLinks;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.designernews.domain.GetCommentsWithRepliesAndUsersUseCase;
import io.plaidapp.designernews.domain.GetStoryUseCase;
import io.plaidapp.designernews.domain.PostReplyUseCase;
import io.plaidapp.designernews.domain.PostStoryCommentUseCase;
import io.plaidapp.designernews.domain.UpvoteCommentUseCase;
import io.plaidapp.designernews.domain.UpvoteStoryUseCase;
import io.plaidapp.designernews.flattendCommentsWithReplies;
import io.plaidapp.designernews.reply1;
import io.plaidapp.test.shared.LiveDataTestUtil;
import io.plaidapp.test.shared.TestCoroutineContextProvider;
import kotlinx.coroutines.experimental.runBlocking;

@RunWith(JUnit4.class)
public class StoryViewModelTest {

    @Rule
    public TestRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private long userId = 5;
    private long storyId = 1345;
    private long commentId = 999;
    private Date createdDate = new GregorianCalendar(2018, 1, 13).getTime();
    private List<Long> commentIds = Arrays.asList(11L, 12L);
    private StoryLinks storyLinks = new StoryLinks(
            userId,
            commentIds,
            new ArrayList<>(),
            new ArrayList<>()
    );
    private Story testStory = new Story(
            storyId,
            "Plaid 2.0 was released",
            createdDate,
            userId,
            storyLinks
    );

    private GetStoryUseCase getStory = mock();
    private PostStoryCommentUseCase postStoryComment = mock();
    private PostReplyUseCase postComment = mock();
    private GetCommentsWithRepliesAndUsersUseCase getCommentsWithRepliesAndUsers = mock();
    private UpvoteStoryUseCase upvoteStory = mock();
    private UpvoteCommentUseCase upvoteComment = mock();

    private StoryViewModel viewModel;


    @Test
    public void loadStory_existsInRepo() {
        viewModel = new StoryViewModel(
                storyId,
                getStory,
                postStoryComment,
                postComment,
                getCommentsWithRepliesAndUsers,
                upvoteStory,
                upvoteComment,
                new TestCoroutineContextProvider()
        );

        Assert.assertNotNull(viewModel.getStory());
    }

    @Test(expected = IllegalStateException.class)
    public void loadStory_notInRepo() {
        Mockito.when(getStory.execute(storyId)).thenReturn(Result.Error(new IllegalStateException()));

        viewModel = new StoryViewModel(
                storyId,
                getStory,
                postStoryComment,
                postComment,
                getCommentsWithRepliesAndUsers,
                upvoteStory,
                upvoteComment,
                new TestCoroutineContextProvider()
        );
    }

    @Test
    public void commentsRequested_whenViewModelCreated() {
        viewModel = new StoryViewModel(
                storyId,
                getStory,
                postStoryComment,
                postComment,
                getCommentsWithRepliesAndUsers,
                upvoteStory,
                upvoteComment,
                new TestCoroutineContextProvider()
        );

        Assert.assertNotNull(viewModel.getUiModel());
        Assert.assertEquals(flattendCommentsWithReplies, viewModel.getUiModel().getComments());
    }

    @Test
    public void upvoteStory_whenUpvoteSuccessful() throws Exception {
        Mockito.when(upvoteStory.execute(storyId)).thenReturn(Result.Success(null));

        viewModel = new StoryViewModel(
                storyId,
                getStory,
                postStoryComment,
                postComment,
                getCommentsWithRepliesAndUsers,
                upvoteStory,
                upvoteComment,
                new TestCoroutineContextProvider()
        );

        Result<Unit> result = null;
        viewModel.storyUpvoteRequested(storyId, success -> result = success);

        Assert.assertEquals(Result.Success(null), result);
    }

    @Test
    public void upvoteStory_whenUpvoteFailed() throws Exception {
        Result<IOException> response = Result.Error(new IOException("Error upvoting"));
        Mockito.when(upvoteStory.execute(storyId)).thenReturn(response);

        viewModel = new StoryViewModel(
                storyId,
                getStory,
                postStoryComment,
                postComment,
                getCommentsWithRepliesAndUsers,
                upvoteStory,
                upvoteComment,
                new TestCoroutineContextProvider()
        );

        Result<Unit> result = null;
        viewModel.storyUpvoteRequested(storyId, success -> result = success);

        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void upvoteComment_whenUpvoteSuccessful() throws Exception {
        Mockito.when(upvoteComment.execute(commentId)).thenReturn(Result.Success(null));

        viewModel = new StoryViewModel(
                storyId,
                getStory,
                postStoryComment,
                postComment,
                getCommentsWithRepliesAndUsers,
                upvoteStory,
                upvoteComment,
                new TestCoroutineContextProvider()
        );

        Result<Unit> result = null;
        viewModel.commentUpvoteRequested(commentId, success -> result = success);

        Assert.assertEquals(Result.Success(null), result);
    }

    @Test
    public void upvoteComment_whenUpvoteFailed() throws Exception {
        Result<IOException> response = Result.Error(new IOException("Error upvoting"));
        Mockito.when(upvoteComment.execute(commentId)).thenReturn(response);

        viewModel = new StoryViewModel(
                storyId,
                getStory,
                postStoryComment,
                postComment,
                getCommentsWithRepliesAndUsers,
                upvoteStory,
                upvoteComment,
                new TestCoroutineContextProvider()
        );

        Result<Unit> result = null;
        viewModel.commentUpvoteRequested(commentId, success -> result = success);

        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void commentReplyRequested_withSuccess() throws Exception {
        Result<Comment> expected = Result.Success(reply1);
        Mockito.when(postComment.invoke(reply1.getBody(), reply1.getParentCommentId()))
                .thenReturn(expected);

        viewModel = new StoryViewModel(
                storyId,
                getStory,
                postStoryComment,
                postComment,
                getCommentsWithRepliesAndUsers,
                upvoteStory,
                upvoteComment,
                new TestCoroutineContextProvider()
        );

        Result<Comment> result = null;
        viewModel.commentReplyRequested(
                reply1.getBody(),
                reply1.getParentCommentId(),
                success -> result = success
        );

        Assert.assertEquals(expected, result);
    }

    @Test
    public void commentReplyRequested_withError() throws Exception {
        Result<Comment> expected = Result.Error(new IOException("Error"));
        Mockito.when(postComment.invoke(reply1.getBody(), reply1.getParentCommentId()))
                .thenReturn(expected);

        viewModel = new StoryViewModel(
                storyId,
                getStory,
                postStoryComment,
                postComment,
                getCommentsWithRepliesAndUsers,
                upvoteStory,
                upvoteComment,
                new TestCoroutineContextProvider()
        );

        Result<Comment> result = null;
        viewModel.commentReplyRequested(
                reply1.getBody(),
                reply1.getParentCommentId(),
                success -> result = success
        );

        Assert.assertEquals(expected, result);
    }

    @Test
    public void storyReplyRequested_withSuccess() throws Exception {
        Result<Comment> expected = Result.Success(reply1);
        Mockito.when(postStoryComment.invoke(reply1.getBody(), storyId))
                .thenReturn(expected);

        viewModel = new StoryViewModel(
                storyId,
                getStory,
                postStoryComment,
                postComment,
                getCommentsWithRepliesAndUsers,
                upvoteStory,
                upvoteComment,
                new TestCoroutineContextProvider()
        );

        Result<Comment> result = null;
        viewModel.storyReplyRequested(
                reply1.getBody(),
                success -> result = success
        );

        Assert.assertEquals(expected, result);
    }

    @Test
    public void storyReplyRequested_withError() throws Exception {
        Result<Comment> expected = Result.Error(new IOException("Error"));
        Mockito.when(postStoryComment.invoke(reply1.getBody(), storyId))
                .thenReturn(expected);

        viewModel = new StoryViewModel(
                storyId,
                getStory,
                postStoryComment,
                postComment,
                getCommentsWithRepliesAndUsers,
                upvoteStory,
                upvoteComment,
                new TestCoroutineContextProvider()
        );

        Result<Comment> result = null;
        viewModel.storyReplyRequested(
                reply1.getBody(),
                success -> result = success
        );

        Assert.assertEquals(expected, result);
    }


    private StoryViewModel withViewModel() {
    when (getStory(storyId)) {
        null -> { /* handle error */ }
        else -> { /* do nothing */ }
    }
    getStoryMock.when(getStory(storyId)).thenReturn(testStory);

    StoryViewModel.CoroutinesContextProvider contextProvider = provideFakeCoroutinesContextProvider();
    return new StoryViewModel(
        storyId,
        getStory,
        postStoryComment,
        postComment,
        ids -> {
            try (val scope = contextProvider.getBlockingScope()) {
                when (commentsWithRepliesAndUsers(ids)) {
                    null -> { /* handle error */ }
                    else -> { /* do nothing */ }
                }
                commentsWithRepliesAndUsersMock.when(commentsWithRepliesAndUsers(ids)).thenReturn(flattendCommentsWithReplies);
                return CompletableFuture.completedFuture(flattendCommentsWithReplies);
            }
        },
        upvoteStory,
        upvoteComment,
        contextProvider
    );
}
}