

package io.plaidapp.designernews.data.comments;

import com.nhaarman.mockitokotlin2.DoAnswer;
import com.nhaarman.mockitokotlin2.KotlinMockitos;
import com.nhaarman.mockitokotlin2.mock.Mock;
import com.nhaarman.mockitokotlin2.whenever;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.comments.model.NewCommentRequest;
import io.plaidapp.core.designernews.data.comments.model.PostCommentResponse;
import io.plaidapp.designernews.errorResponseBody;
import io.plaidapp.designernews.repliesResponses;
import io.plaidapp.designernews.replyResponse1;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;
import kotlinx.coroutines.CompletableDeferred;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Response;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CommentsRemoteDataSourceTest {

    private final String body = "Plaid is awesome";
    private Mock<DesignerNewsService> service;
    private CommentsRemoteDataSource dataSource;

    @Before
    public void setUp() {
        service = KotlinMockitos.mock(DesignerNewsService.class);
        dataSource = new CommentsRemoteDataSource(service.getObject());
    }

    @Test
    public void getComments_whenRequestSuccessful() throws Exception {
        Response<List<CommentResponse>> result = Response.success(repliesResponses);
        CompletableDeferred<Response<List<CommentResponse>>> deferred = new CompletableDeferred<>();
        deferred.complete(result);
        when(service.getObject().getComments("1")).thenReturn(deferred);

        Result<List<CommentResponse>> response = dataSource.getComments(List.of(1L));

        Assert.assertNotNull(response);
        assertEquals(Result.Success.create(repliesResponses), response);
    }

    @Test
    public void getComments_forMultipleComments() throws Exception {
        Response<List<CommentResponse>> result = Response.success(repliesResponses);
        CompletableDeferred<Response<List<CommentResponse>>> deferred = new CompletableDeferred<>();
        deferred.complete(result);
        when(service.getObject().getComments("11,12")).thenReturn(deferred);

        Result<List<CommentResponse>> response = dataSource.getComments(List.of(11L, 12L));

        Assert.assertNotNull(response);
        assertEquals(Result.Success.create(repliesResponses), response);
    }

    @Test
    public void getComments_whenRequestFailed() throws Exception {
        Response<List<CommentResponse>> result = Response.error(
                400,
                errorResponseBody
        );
        CompletableDeferred<Response<List<CommentResponse>>> deferred = new CompletableDeferred<>();
        deferred.complete(result);
        when(service.getObject().getComments("1")).thenReturn(deferred);

        Result<List<CommentResponse>> response = dataSource.getComments(List.of(1L));

        Assert.assertTrue(response instanceof Result.Error);
    }

    @Test
    public void getComments_whenResponseEmpty() throws Exception {
        Response<List<CommentResponse>> result = Response.success(null);
        CompletableDeferred<Response<List<CommentResponse>>> deferred = new CompletableDeferred<>();
        deferred.complete(result);
        when(service.getObject().getComments("1")).thenReturn(deferred);

        Result<List<CommentResponse>> response = dataSource.getComments(List.of(1L));

        Assert.assertTrue(response instanceof Result.Error);
    }

    @Test
    public void getComments_whenException() throws Exception {
        doAnswer(invocation -> {
            throw new UnknownHostException();
        }).when(service.getObject()).getComments("1");

        Result<List<CommentResponse>> response = dataSource.getComments(List.of(1L));

        Assert.assertTrue(response instanceof Result.Error);
    }

    @Test(expected = IllegalStateException.class)
    public void comment_whenParentCommentIdAndStoryIdNull() throws Exception {
        dataSource.comment(body, null, null, 11L);
    }

    @Test
    public void comment_whenException() throws Exception {
        NewCommentRequest request = new NewCommentRequest(body, "11", null, "111");
        doAnswer(invocation -> {
            throw new UnknownHostException();
        }).when(service.getObject()).comment(request);

        Result<PostCommentResponse> response = dataSource.comment(body, 11L, null, 111L);

        Assert.assertTrue(response instanceof Result.Error);
    }

    @Test
    public void comment_withNoComments() throws Exception {
        Response<PostCommentResponse> response = Response.success(new PostCommentResponse(List.of()));
        NewCommentRequest request = new NewCommentRequest(body, "11", null, "111");
        CompletableDeferred<Response<PostCommentResponse>> deferred = new CompletableDeferred<>();
        deferred.complete(response);
        when(service.getObject().comment(request)).thenReturn(deferred);

        Result<PostCommentResponse> result = dataSource.comment(body, 11L, null, 111L);

        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void comment_withComments() throws Exception {
        Response<PostCommentResponse> response = Response.success(
                new PostCommentResponse(List.of(replyResponse1))
        );
        NewCommentRequest request = new NewCommentRequest(body, "11", null, "111");
        CompletableDeferred<Response<PostCommentResponse>> deferred = new CompletableDeferred<>();
        deferred.complete(response);
        when(service.getObject().comment(request)).thenReturn(deferred);

        Result<PostCommentResponse> result = dataSource.comment(body, 11L, null, 111L);

        Assert.assertEquals(result, Result.Success.create(replyResponse1));
    }
}