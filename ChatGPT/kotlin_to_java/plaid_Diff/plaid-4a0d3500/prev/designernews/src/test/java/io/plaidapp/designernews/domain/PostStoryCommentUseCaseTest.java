package io.plaidapp.designernews.domain;

import com.nhaarman.mockitokotlin2.mock;
import com.nhaarman.mockitokotlin2.whenever;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.comments.CommentsRepository;
import io.plaidapp.core.designernews.data.login.LoginRepository;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.designernews.loggedInUser;
import io.plaidapp.designernews.replyResponse1;
import kotlinx.coroutines.runBlocking;
import org.junit.Assert;
import org.junit.Test;
import java.io.IOException;

public class PostStoryCommentUseCaseTest {
    private final String body = "Plaid is plaidy";

    private final CommentsRepository repository = mock(CommentsRepository.class);
    private final LoginRepository loginRepository = mock(LoginRepository.class);
    private final PostStoryCommentUseCase postStoryComment = new PostStoryCommentUseCase(repository, loginRepository);

    @Test(expected = IllegalStateException.class)
    public void postStoryComment_userNull() throws Exception {
        whenever(loginRepository.getUser()).thenReturn(null);

        postStoryComment.invoke("text", 123L);

        Assert.assertEquals(Unit.INSTANCE, null);
    }

    @Test
    public void postStoryComment_errorReturned() throws Exception {
        whenever(loginRepository.getUser()).thenReturn(loggedInUser);

        whenever(repository.postStoryComment(body, 123L, 111L))
            .thenReturn(Result.Error(new IOException("Error")));

        Result result = postStoryComment.invoke(body, 123L);

        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void postStoryComment_success() throws Exception {
        whenever(loginRepository.getUser()).thenReturn(loggedInUser);

        whenever(repository.postStoryComment(replyResponse1.getBody(), 123L, 111L))
            .thenReturn(Result.Success(replyResponse1));

        Result result = postStoryComment.invoke(replyResponse1.getBody(), 123L);

        Comment expectedComment = new Comment(
            replyResponse1.getId(),
            replyResponse1.getLinks().getParentComment(),
            replyResponse1.getBody(),
            replyResponse1.getCreated_at(),
            replyResponse1.getDepth(),
            replyResponse1.getLinks().getCommentUpvotes().size(),
            loggedInUser.getId(),
            loggedInUser.getDisplayName(),
            loggedInUser.getPortraitUrl(),
            false
        );

        Assert.assertEquals(Result.Success(expectedComment), result);
    }
}