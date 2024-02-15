

package io.plaidapp.designernews.data.comments;

import com.nhaarman.mockitokotlin2.argumentCaptor;
import com.nhaarman.mockitokotlin2.doAnswer;
import com.nhaarman.mockitokotlin2.mock;
import com.nhaarman.mockitokotlin2.times;
import com.nhaarman.mockitokotlin2.verify;
import com.nhaarman.mockitokotlin2.whenever;
import io.plaidapp.core.data.Result;
import io.plaidapp.designernews.data.api.DNService;
import io.plaidapp.designernews.data.comments.model.CommentResponse;
import io.plaidapp.designernews.data.comments.model.NewCommentRequest;
import io.plaidapp.designernews.data.comments.model.PostCommentResponse;
import io.plaidapp.designernews.errorResponseBody;
import io.plaidapp.designernews.repliesResponses;
import io.plaidapp.designernews.replyResponse1;
import java.io.IOException;
import java.lang.IllegalStateException;
import java.net.UnknownHostException;
import kotlinx.coroutines.CompletableDeferred;
import org.junit.Assert;
import org.junit.Test;
import retrofit2.Response;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;

public class CommentsRemoteDataSourceTest {

    private final String body = "Plaid is awesome";

    private final DNService service = mock(DNService.class);
    private final CommentsRemoteDataSource dataSource =
            new io.plaidapp.designernews.data.comments.CommentsRemoteDataSource(service);

    @Test
    public void getComments_whenRequestSuccessful() throws Exception {

        final Response<List<CommentResponse>> result = Response.success(repliesResponses);
        whenever(service.getComments("1")).thenReturn(CompletableDeferred.completedFuture(result));

        final Result<List<CommentResponse>> response = dataSource.getComments(java.util.Arrays.asList(1L));

        Assert.assertNotNull(response);
        Assert.assertEquals(Result.Success.create(repliesResponses), response);
    }

    @Test
    public void getComments_forMultipleComments() throws Exception {

        final Response<List<CommentResponse>> result = Response.success(repliesResponses);
        whenever(service.getComments("11,12")).thenReturn(CompletableDeferred.completedFuture(result));

        final Result<List<CommentResponse>> response = dataSource.getComments(java.util.Arrays.asList(11L, 12L));

        Assert.assertNotNull(response);
        Assert.assertEquals(Result.Success.create(repliesResponses), response);
    }

    @Test
    public void getComments_whenRequestFailed() throws Exception {

        final Response<List<CommentResponse>> result = Response.error(
                400,
                errorResponseBody
        );
        whenever(service.getComments("1")).thenReturn(CompletableDeferred.completedFuture(result));

        final Result<List<CommentResponse>> response = dataSource.getComments(java.util.Arrays.asList(1L));

        Assert.assertTrue(response instanceof Result.Error);
    }

    @Test
    public void getComments_whenResponseEmpty() throws Exception {

        final Response<List<CommentResponse>> result = Response.success(null);
        whenever(service.getComments("1")).thenReturn(CompletableDeferred.completedFuture(result));

        final Result<List<CommentResponse>> response = dataSource.getComments(java.util.Arrays.asList(1L));

        Assert.assertTrue(response instanceof Result.Error);
    }

    @Test
    public void getComments_whenException() throws Exception {

        doAnswer(invocation -> {
            throw new UnknownHostException();
        }).when(service).getComments("1");

        final Result<List<CommentResponse>> response = dataSource.getComments(java.util.Arrays.asList(1L));

        Assert.assertTrue(response instanceof Result.Error);
    }

    @Test(expected = IllegalStateException.class)
    public void comment_whenParentCommentIdAndStoryIdNull() throws Exception {

        dataSource.comment("text", null, null, 11L);

    }

    @Test
    public void comment_whenException() throws Exception {

        final NewCommentRequest request =
                new NewCommentRequest(body, "11", null, "111");
        doAnswer(invocation -> {
            throw new UnknownHostException();
        }).when(service).comment(request);

        final Result<PostCommentResponse> response = dataSource.comment(body, 11L, null, 111L);

        Assert.assertTrue(response instanceof Result.Error);
    }

    @Test
    public void comment_withNoComments() throws Exception {

        final Response<PostCommentResponse> response = Response.success(
                new PostCommentResponse(
                        java.util.Collections.emptyList()
                )
        );
        final NewCommentRequest request =
                new NewCommentRequest(body, "11", null, "111");
        whenever(service.comment(request)).thenReturn(CompletableDeferred.completedFuture(response));

        final Result<PostCommentResponse> result = dataSource.comment(body, 11L, null, 111L);

        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void comment_withComments() throws Exception {

        final Response<PostCommentResponse> response = Response.success(
                new PostCommentResponse(java.util.Arrays.asList(replyResponse1))
        );
        final NewCommentRequest request =
                new NewCommentRequest(body, "11", null, "111");
        whenever(service.comment(request)).thenReturn(CompletableDeferred.completedFuture(response));

        final Result<PostCommentResponse> result = dataSource.comment(body, 11L, null, 111L);

        Assert.assertEquals(result, Result.Success.create(replyResponse1));
    }
}