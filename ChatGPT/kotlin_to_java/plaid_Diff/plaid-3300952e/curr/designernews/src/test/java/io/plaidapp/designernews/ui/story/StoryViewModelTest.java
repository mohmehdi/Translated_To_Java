package io.plaidapp.designernews.ui.story;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import com.nhaarman.mockitokotlin2.mock;
import com.nhaarman.mockitokotlin2.whenever;
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
import io.plaidapp.test.shared.provideFakeCoroutinesContextProvider;
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

public class StoryViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private long userId = 5L;
    private long storyId = 1345L;
    private long commentId = 999L;
    private Date createdDate = new GregorianCalendar(2018, 1, 13).getTime();
    private List<Long> commentIds = listOf(11L, 12L);
    private StoryLinks storyLinks = new StoryLinks(userId, commentIds, emptyList(), emptyList());
    private Story testStory = new Story(storyId, "Plaid 2.0 was released", createdDate, userId, storyLinks);

    private GetStoryUseCase getStory = mock(GetStoryUseCase.class);
    private PostStoryCommentUseCase postStoryComment = mock(PostStoryCommentUseCase.class);
    private PostReplyUseCase postComment = mock(PostReplyUseCase.class);
    private GetCommentsWithRepliesAndUsersUseCase getCommentsWithRepliesAndUsers = mock(GetCommentsWithRepliesAndUsersUseCase.class);
    private UpvoteStoryUseCase upvoteStory = mock(UpvoteStoryUseCase.class);
    private UpvoteCommentUseCase upvoteComment = mock(UpvoteCommentUseCase.class);

    @Test
    public void loadStory_existsInRepo() {
        StoryViewModel viewModel = withViewModel();
        Assert.assertNotNull(viewModel.getStory());
    }

    @Test(expected = IllegalStateException.class)
    public void loadStory_notInRepo() {
        whenever(getStory.invoke(storyId)).thenReturn(Result.Error(new IllegalStateException()));
        StoryViewModel viewModel = new StoryViewModel(storyId, getStory, postStoryComment, postComment, getCommentsWithRepliesAndUsers, upvoteStory, upvoteComment, provideFakeCoroutinesContextProvider());
    }

    @Test
    public void commentsRequested_whenViewModelCreated() {
        StoryViewModel viewModel = withViewModel();
        StoryUiModel event = LiveDataTestUtil.getValue(viewModel.getUiModel());
        Assert.assertEquals(event.getComments(), flattendCommentsWithReplies);
    }

    @Test
    public void upvoteStory_whenUpvoteSuccessful() {
        runBlocking {
            whenever(upvoteStory.invoke(storyId)).thenReturn(Result.Success(Unit));
            StoryViewModel viewModel = withViewModel();
            Result<Unit> result = null;
            viewModel.storyUpvoteRequested(storyId, it -> result = it);
            Assert.assertEquals(Result.Success(Unit), result);
        }
    }

    @Test
    public void upvoteStory_whenUpvoteFailed() {
        runBlocking {
            Result.Error response = new Result.Error(new IOException("Error upvoting"));
            whenever(upvoteStory.invoke(storyId)).thenReturn(response);
            StoryViewModel viewModel = withViewModel();
            Result<Unit> result = null;
            viewModel.storyUpvoteRequested(storyId, it -> result = it);
            Assert.assertTrue(result instanceof Result.Error);
        }
    }

    @Test
    public void upvoteComment_whenUpvoteSuccessful() {
        runBlocking {
            whenever(upvoteComment.invoke(commentId)).thenReturn(Result.Success(Unit));
            StoryViewModel viewModel = withViewModel();
            Result<Unit> result = null;
            viewModel.commentUpvoteRequested(commentId, it -> result = it);
            Assert.assertEquals(Result.Success(Unit), result);
        }
    }

    @Test
    public void upvoteComment_whenUpvoteFailed() {
        runBlocking {
            Result.Error response = new Result.Error(new IOException("Error upvoting"));
            whenever(upvoteComment.invoke(commentId)).thenReturn(response);
            StoryViewModel viewModel = withViewModel();
            Result<Unit> result = null;
            viewModel.commentUpvoteRequested(commentId, it -> result = it);
            Assert.assertTrue(result instanceof Result.Error);
        }
    }

    @Test
    public void commentReplyRequested_withSuccess() {
        runBlocking {
            Result.Success expected = new Result.Success(reply1);
            whenever(postComment.invoke(reply1.getBody(), reply1.getParentCommentId())).thenReturn(expected);
            StoryViewModel viewModel = withViewModel();
            Result<Comment> result = null;
            viewModel.commentReplyRequested(reply1.getBody(), reply1.getParentCommentId(), it -> result = it);
            Assert.assertEquals(expected, result);
        }
    }

    @Test
    public void commentReplyRequested_withError() {
        runBlocking {
            Result.Error expected = new Result.Error(new IOException("Error"));
            whenever(postComment.invoke(reply1.getBody(), reply1.getParentCommentId())).thenReturn(expected);
            StoryViewModel viewModel = withViewModel();
            Result<Comment> result = null;
            viewModel.commentReplyRequested(reply1.getBody(), reply1.getParentCommentId(), it -> result = it);
            Assert.assertEquals(expected, result);
        }
    }

    @Test
    public void storyReplyRequested_withSuccess() {
        runBlocking {
            Result.Success expected = new Result.Success(reply1);
            whenever(postStoryComment.invoke(reply1.getBody(), storyId)).thenReturn(expected);
            StoryViewModel viewModel = withViewModel();
            Result<Comment> result = null;
            viewModel.storyReplyRequested(reply1.getBody(), it -> result = it);
            Assert.assertEquals(expected, result);
        }
    }

    @Test
    public void storyReplyRequested_withError() {
        runBlocking {
            Result.Error expected = new Result.Error(new IOException("Error"));
            whenever(postStoryComment.invoke(reply1.getBody(), storyId)).thenReturn(expected);
            StoryViewModel viewModel = withViewModel();
            Result<Comment> result = null;
            viewModel.storyReplyRequested(reply1.getBody(), it -> result = it);
            Assert.assertEquals(expected, result);
        }
    }

    private StoryViewModel withViewModel() {
        whenever(getStory.invoke(storyId)).thenReturn(Result.Success(testStory));
        runBlocking {
            whenever(getCommentsWithRepliesAndUsers.invoke(commentIds)).thenReturn(Result.Success(flattendCommentsWithReplies));
        }
        return new StoryViewModel(storyId, getStory, postStoryComment, postComment, getCommentsWithRepliesAndUsers, upvoteStory, upvoteComment, provideFakeCoroutinesContextProvider());
    }
}