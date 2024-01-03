package io.plaidapp.core.designernews.data.comments;

import com.nhaarman.mockitokotlin2.doAnswer;
import com.nhaarman.mockitokotlin2.mock;
import com.nhaarman.mockitokotlin2.whenever;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.comments.model.NewCommentRequest;
import io.plaidapp.core.designernews.data.comments.model.PostCommentResponse;
import io.plaidapp.core.designernews.errorResponseBody;
import io.plaidapp.core.designernews.repliesResponses;
import io.plaidapp.core.designernews.replyResponse1;
import kotlinx.coroutines.CompletableDeferred;
import kotlinx.coroutines.runBlocking;
import org.junit.Assert;

import org.junit.Test;
import retrofit2.Response;
import java.net.UnknownHostException;
import java.util.List;

public class CommentsRemoteDataSourceTest {

    private final String body = "Plaid is awesome";

    private final DesignerNewsService service = mock(DesignerNewsService.class);
    private final CommentsRemoteDataSource dataSource = new CommentsRemoteDataSource(service);

    @Test
    public void getComments_whenRequestSuccessful() throws InterruptedException {
        
        Response<List<CommentResponse>> result = Response.success(repliesResponses);
        CompletableDeferred<Response<List<CommentResponse>>> deferredResult = new CompletableDeferred<>();
        deferredResult.complete(result);
        whenever(service.getComments("1")).thenReturn(deferredResult);

        
        Result<List<CommentResponse>> response = runBlocking(() -> dataSource.getComments(List.of(1L)));

        
        Assert.assertNotNull(response);
        Assert.assertEquals(Result.Success(repliesResponses), response);
    }

    @Test
    public void getComments_forMultipleComments() throws InterruptedException {
        
        Response<List<CommentResponse>> result = Response.success(repliesResponses);
        CompletableDeferred<Response<List<CommentResponse>>> deferredResult = new CompletableDeferred<>();
        deferredResult.complete(result);
        whenever(service.getComments("11,12")).thenReturn(deferredResult);

        
        Result<List<CommentResponse>> response = runBlocking(() -> dataSource.getComments(List.of(11L, 12L)));

        
        Assert.assertNotNull(response);
        Assert.assertEquals(Result.Success(repliesResponses), response);
    }

    @Test
    public void getComments_whenRequestFailed() throws InterruptedException {
        
        Response<List<CommentResponse>> result = Response.error(
            400,
            errorResponseBody
        );
        CompletableDeferred<Response<List<CommentResponse>>> deferredResult = new CompletableDeferred<>();
        deferredResult.complete(result);
        whenever(service.getComments("1")).thenReturn(deferredResult);

        
        Result<List<CommentResponse>> response = runBlocking(() -> dataSource.getComments(List.of(1L)));

        
        Assert.assertTrue(response instanceof Result.Error);
    }

    @Test
    public void getComments_whenResponseEmpty() throws InterruptedException {
        
        Response<List<CommentResponse>> result = Response.success(null);
        CompletableDeferred<Response<List<CommentResponse>>> deferredResult = new CompletableDeferred<>();
        deferredResult.complete(result);
        whenever(service.getComments("1")).thenReturn(deferredResult);

        
        Result<List<CommentResponse>> response = runBlocking(() -> dataSource.getComments(List.of(1L)));

        
        Assert.assertTrue(response instanceof Result.Error);
    }

    @Test
    public void getComments_whenException() throws InterruptedException {
        
        doAnswer(invocation -> { throw new UnknownHostException(); })
            .whenever(service).getComments("1");

        
        Result<List<CommentResponse>> response = runBlocking(() -> dataSource.getComments(List.of(1L)));

        
        Assert.assertTrue(response instanceof Result.Error);
    }

    @Test(expected = IllegalStateException.class)
    public void comment_whenParentCommentIdAndStoryIdNull() throws InterruptedException {
        
        runBlocking(() -> {
            dataSource.comment("text", null, null, 11L);
            return Unit.INSTANCE;
        });
    }

    @Test
    public void comment_whenException() throws InterruptedException {
        
        NewCommentRequest request = new NewCommentRequest(body, "11", null, "111");
        doAnswer(invocation -> { throw new UnknownHostException(); })
            .whenever(service).comment(request);

        
        Result<PostCommentResponse> response = runBlocking(() -> dataSource.comment(body, 11L, null, 111L));

        
        Assert.assertTrue(response instanceof Result.Error);
    }

    @Test
    public void comment_withNoComments() throws InterruptedException {
        
        Response<PostCommentResponse> response = Response.success(new PostCommentResponse(List.of()));
        NewCommentRequest request = new NewCommentRequest(body, "11", null, "111");
        CompletableDeferred<Response<PostCommentResponse>> deferredResult = new CompletableDeferred<>();
        deferredResult.complete(response);
        whenever(service.comment(request)).thenReturn(deferredResult);

        
        Result<PostCommentResponse> result = runBlocking(() -> dataSource.comment(body, 11L, null, 111L));

        
        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void comment_withComments() throws InterruptedException {
        
        Response<PostCommentResponse> response = Response.success(
            new PostCommentResponse(List.of(replyResponse1))
        );
        NewCommentRequest request = new NewCommentRequest(body, "11", null, "111");
        CompletableDeferred<Response<PostCommentResponse>> deferredResult = new CompletableDeferred<>();
        deferredResult.complete(response);
        whenever(service.comment(request)).thenReturn(deferredResult);

        
        Result<PostCommentResponse> result = runBlocking(() -> dataSource.comment(body, 11L, null, 111L));

        
        Assert.assertEquals(result, Result.Success(replyResponse1));
    }
}