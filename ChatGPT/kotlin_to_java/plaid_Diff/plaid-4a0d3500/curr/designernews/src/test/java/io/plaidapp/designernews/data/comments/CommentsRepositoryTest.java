package io.plaidapp.designernews.data.comments;

import com.nhaarman.mockitokotlin2.mock;
import com.nhaarman.mockitokotlin2.whenever;
import io.plaidapp.core.data.Result;
import io.plaidapp.designernews.repliesResponses;
import io.plaidapp.designernews.replyResponse1;
import kotlinx.coroutines.runBlocking;
import org.junit.Assert;
import org.junit.Test;
import java.io.IOException;

public class CommentsRepositoryTest {
    private final String body = "Plaid 2.0 is awesome";

    private final CommentsRemoteDataSource dataSource = mock(CommentsRemoteDataSource.class);
    private final CommentsRepository repository = new CommentsRepository(dataSource);

    @Test
    public void getComments_withSuccess() throws Exception {
        final List<Long> ids = Arrays.asList(1L);
        final Result<List<Comment>> result = Result.Success(repliesResponses);
        whenever(dataSource.getComments(ids)).thenReturn(result);

        final Result<List<Comment>> data = repository.getComments(ids);

        Assert.assertEquals(result, data);
    }

    @Test
    public void getComments_withError() throws Exception {
        final List<Long> ids = Arrays.asList(1L);
        final Result<List<Comment>> result = Result.Error(new IOException("error"));
        whenever(dataSource.getComments(ids)).thenReturn(result);

        final Result<List<Comment>> data = repository.getComments(ids);

        Assert.assertEquals(result, data);
    }

    @Test
    public void postStoryComment_withSuccess() throws Exception {
        final Result<Comment> result = Result.Success(replyResponse1);
        whenever(
            dataSource.comment(
                body,
                null,
                11L,
                111L
            )
        ).thenReturn(result);

        final Result<Comment> data = repository.postStoryComment(body, 11L, 111L);

        Assert.assertEquals(result, data);
    }

    @Test
    public void postStoryComment_withError() throws Exception {
        final Result<Comment> result = Result.Error(new IOException("error"));
        whenever(
            dataSource.comment(
                body,
                null,
                11L,
                111L
            )
        ).thenReturn(result);

        final Result<Comment> data = repository.postStoryComment(body, 11L, 111L);

        Assert.assertEquals(result, data);
    }

    @Test
    public void postReply_withSuccess() throws Exception {
        final Result<Comment> result = Result.Success(replyResponse1);
        whenever(
            dataSource.comment(
                body,
                11L,
                null,
                111L
            )
        ).thenReturn(result);

        final Result<Comment> data = repository.postReply(body, 11L, 111L);

        Assert.assertEquals(result, data);
    }

    @Test
    public void postReply_withError() throws Exception {
        final Result<Comment> result = Result.Error(new IOException("error"));
        whenever(
            dataSource.comment(
                body,
                11L,
                null,
                111L
            )
        ).thenReturn(result);

        final Result<Comment> data = repository.postReply(body, 11L, 111L);

        Assert.assertEquals(result, data);
    }
}