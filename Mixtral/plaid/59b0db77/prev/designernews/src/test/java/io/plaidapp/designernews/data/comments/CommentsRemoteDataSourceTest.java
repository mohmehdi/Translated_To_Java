package io.plaidapp.designernews.data.comments;

import com.nhaarman.mockitokotlin2.DoAnswer;
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
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableDeferred;
import java.util.concurrent.ExecutionException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class CommentsRemoteDataSourceTest {

  private final String body = "Plaid is awesome";

  private String body = "Plaid is awesome";

  private DesignerNewsService service = mock(DesignerNewsService.class);
  private CommentsRemoteDataSource dataSource = new CommentsRemoteDataSource(
    service
  );

  @Test
  public void getComments_whenRequestSuccessful()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> result = new CompletableDeferred<>();
    result.complete(Response.success(repliesResponses));

    whenever(service.getComments("1")).thenReturn(result);

    Result<List<CommentResponse>> response = dataSource
      .getComments(Arrays.asList(1L))
      .get();

    Assert.assertNotNull(response);
    Assert.assertEquals(Result.Success(repliesResponses), response);
  }

  @Test
  public void getComments_forMultipleComments()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> result = new CompletableDeferred<>();
    result.complete(Response.success(repliesResponses));

    whenever(service.getComments("11,12")).thenReturn(result);

    Result<List<CommentResponse>> response = dataSource
      .getComments(Arrays.asList(11L, 12L))
      .get();

    Assert.assertNotNull(response);
    Assert.assertEquals(Result.Success(repliesResponses), response);
  }

  @Test
  public void getComments_whenRequestFailed()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> result = new CompletableDeferred<>();
    result.complete(Response.error(400, errorResponseBody));

    whenever(service.getComments("1")).thenReturn(result);

    Result<List<CommentResponse>> response = dataSource
      .getComments(Arrays.asList(1L))
      .get();

    Assert.assertTrue(response instanceof Result.Error);
  }

  @Test
  public void getComments_whenResponseEmpty()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> result = new CompletableDeferred<>();
    result.complete(Response.success((List<CommentResponse>) null));

    whenever(service.getComments("1")).thenReturn(result);

    Result<List<CommentResponse>> response = dataSource
      .getComments(Arrays.asList(1L))
      .get();

    Assert.assertTrue(response instanceof Result.Error);
  }

  @Test
  public void getComments_whenException() throws Exception {
    Service serviceMock = mock(Service.class);
    when(serviceMock.getComments("1")).thenThrow(UnknownHostException.class);
    DataSource dataSource = new DataSource(serviceMock);

    Result response = dataSource.getComments(Arrays.asList(1L));
    assertTrue(response instanceof Result.Error);
  }

  @Test(expected = IllegalStateException.class)
  public void comment_whenParentCommentIdAndStoryIdNull()
    throws ExecutionException, InterruptedException {
    dataSource.comment("text", null, null, 11L);
  }

  @Test
  public void comment_whenException() {
    // Given that the service throws an exception
    NewCommentRequest request = new NewCommentRequest(body, "11", null, "111");
    when(service.comment(request))
      .thenAnswer(invocation -> {
        throw new UnknownHostException();
      });

    // When adding a comment
    Result response = dataSource.comment(body, 11L, null, 111L);

    // Then the response is not successful
    assertTrue(response instanceof Result.Error);
  }

  @Test
  public void comment_withNoComments()
    throws ExecutionException, InterruptedException {
    Response<PostCommentResponse> response = Response.success(
      new PostCommentResponse(null)
    );

    NewCommentRequest request = new NewCommentRequest(body, "11", null, "111");

    whenever(service.comment(request)).thenReturn(result);

    Result<CommentResponse> result = dataSource
      .comment(body, 11L, null, 111L)
      .get();

    Assert.assertTrue(result instanceof Result.Error);
  }

  @Test
  public void comment_withComments()
    throws ExecutionException, InterruptedException {
    Response<PostCommentResponse> response = Response.success(
      new PostCommentResponse(Arrays.asList(replyResponse1))
    );

    NewCommentRequest request = new NewCommentRequest(body, "11", null, "111");

    whenever(service.comment(request)).thenReturn(result);

    Result<CommentResponse> result = dataSource
      .comment(body, 11L, null, 111L)
      .get();

    Assert.assertEquals(result, Result.Success(replyResponse1));
  }
}
