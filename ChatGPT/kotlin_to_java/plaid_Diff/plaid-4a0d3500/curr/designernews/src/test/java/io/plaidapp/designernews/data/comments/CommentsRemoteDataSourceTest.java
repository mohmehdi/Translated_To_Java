package io.plaidapp.designernews.data.comments;

import com.nhaarman.mockitokotlin2.doAnswer;
import com.nhaarman.mockitokotlin2.mock;
import com.nhaarman.mockitokotlin2.whenever;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.comments.model.NewCommentRequest;
import io.plaidapp.core.designernews.data.comments.model.PostCommentResponse;
import io.plaidapp.designernews.errorResponseBody;
import io.plaidapp.designernews.repliesResponses;
import io.plaidapp.designernews.replyResponse1;
import kotlinx.coroutines.CompletableDeferred;
import kotlinx.coroutines.runBlocking;
import org.junit.Assert;
import retrofit2.Response;
import java.net.UnknownHostException;

public class CommentsRemoteDataSourceTest {

    private final String body = "Plaid is awesome";

    private final DesignerNewsService service = mock(DesignerNewsService.class);
    private final CommentsRemoteDataSource dataSource =
        new CommentsRemoteDataSource(service);

    @Test
    public void getComments_whenRequestSuccessful() throws Exception {
        
        Response<List<CommentResponse>> result = Response.success(repliesResponses);
        whenever(service.getComments("1")).thenReturn(new CompletableDeferred<>(result));

        
        Result<List<CommentResponse>> response = dataSource.getComments(Arrays.asList(1L));

        
        Assert.assertNotNull(response);
        Assert.assertEquals(Result.Success(repliesResponses), response);
    }

    @Test
    public void getComments_forMultipleComments() throws Exception {
        
        Response<List<CommentResponse>> result = Response.success(repliesResponses);
        whenever(service.getComments("11,12")).thenReturn(new CompletableDeferred<>(result));

        
        Result<List<CommentResponse>> response = dataSource.getComments(Arrays.asList(11L, 12L));

        
        Assert.assertNotNull(response);
        Assert.assertEquals(Result.Success(repliesResponses), response);
    }

    @Test
    public void getComments_whenRequestFailed() throws Exception {
        
        Response<List<CommentResponse>> result = Response.error(
            400,
            errorResponseBody
        );
        whenever(service.getComments("1")).thenReturn(new CompletableDeferred<>(result));

        
        Result<List<CommentResponse>> response = dataSource.getComments(Arrays.asList(1L));

        
        Assert.assertTrue(response instanceof Result.Error);
    }

    @Test
    public void getComments_whenResponseEmpty() throws Exception {
        
        Response<List<CommentResponse>> result = Response.success(null);
        whenever(service.getComments("1")).thenReturn(new CompletableDeferred<>(result));

        
        Result<List<CommentResponse>> response = dataSource.getComments(Arrays.asList(1L));

        
        Assert.assertTrue(response instanceof Result.Error);
    }

    @Test
    public void getComments_whenException() throws Exception {
        
        doAnswer(invocation -> { throw new UnknownHostException(); })
            .whenever(service).getComments("1");

        
        Result<List<CommentResponse>> response = dataSource.getComments(Arrays.asList(1L));

        
        Assert.assertTrue(response instanceof Result.Error);
    }

    @Test(expected = IllegalStateException.class)
    public void comment_whenParentCommentIdAndStoryIdNull() throws Exception {
        
        dataSource.comment("text", null, null, 11L);
        
        return;
    }

    @Test
    public void comment_whenException() throws Exception {
        
        NewCommentRequest request = new NewCommentRequest(body, "11", null, "111");
        doAnswer(invocation -> { throw new UnknownHostException(); })
            .whenever(service).comment(request);

        
        Result<PostCommentResponse> response = dataSource.comment(body, 11L, null, 111L);

        
        Assert.assertTrue(response instanceof Result.Error);
    }

    @Test
    public void comment_withNoComments() throws Exception {
        
        Response<PostCommentResponse> response = Response.success(new PostCommentResponse(Collections.emptyList()));
        NewCommentRequest request = new NewCommentRequest(body, "11", null, "111");
        whenever(service.comment(request)).thenReturn(new CompletableDeferred<>(response));

        
        Result<PostCommentResponse> result = dataSource.comment(body, 11L, null, 111L);

        
        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void comment_withComments() throws Exception {
        
        Response<PostCommentResponse> response = Response.success(
            new PostCommentResponse(Arrays.asList(replyResponse1))
        );
        NewCommentRequest request = new NewCommentRequest(body, "11", null, "111");
        whenever(service.comment(request)).thenReturn(new CompletableDeferred<>(response));

        
        Result<PostCommentResponse> result = dataSource.comment(body, 11L, null, 111L);

        
        Assert.assertEquals(result, Result.Success(replyResponse1));
    }
}