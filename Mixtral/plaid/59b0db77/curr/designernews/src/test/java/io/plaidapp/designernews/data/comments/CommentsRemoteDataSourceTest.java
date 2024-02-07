package io.plaidapp.designernews.data.comments;

import static com.nhaarman.mockitokotlin2.argumentCaptor;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nhaarman.mockitokotlin2.DoAnswer;
import com.nhaarman.mockitokotlin2.mock;
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

public class CommentsRemoteDataSourceTest {

  private String body = "Plaid is awesome";

  private DNService service = mock(DNService.class);
  private CommentsRemoteDataSource dataSource = new CommentsRemoteDataSource(
    service
  );

  @Test
  public void getComments_whenRequestSuccessful()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> result = new CompletableDeferred<>();
    result.complete(Response.success(repliesResponses));

    when(service.getComments("1")).thenReturn(result);

    Result<List<CommentResponse>> response = dataSource
      .getComments(Arrays.asList(1L))
      .get();

    Assert.assertNotNull(response);
    Assert.assertEquals(Result.Success.create(repliesResponses), response);
  }

  @Test
  public void getComments_forMultipleComments()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> result = new CompletableDeferred<>();
    result.complete(Response.success(repliesResponses));

    when(service.getComments("11,12")).thenReturn(result);

    Result<List<CommentResponse>> response = dataSource
      .getComments(Arrays.asList(11L, 12L))
      .get();

    Assert.assertNotNull(response);
    Assert.assertEquals(Result.Success.create(repliesResponses), response);
  }

  @Test
  public void getComments_whenRequestFailed()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> result = new CompletableDeferred<>();
    result.complete(Response.error(400, errorResponseBody));

    when(service.getComments("1")).thenReturn(result);

    Result<List<CommentResponse>> response = dataSource
      .getComments(Arrays.asList(1L))
      .get();

    assertTrue(response instanceof Result.Error);
  }

  @Test
  public void getComments_whenResponseEmpty()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> result = new CompletableDeferred<>();
    result.complete(Response.success((List<CommentResponse>) null));

    when(service.getComments("1")).thenReturn(result);

    Result<List<CommentResponse>> response = dataSource
      .getComments(Arrays.asList(1L))
      .get();

    assertTrue(response instanceof Result.Error);
  }

  @Test
  public void getComments_whenException()
    throws ExecutionException, InterruptedException {
    doThrow(UnknownHostException.class).when(service).getComments(any());

    Result<List<CommentResponse>> response = dataSource
      .getComments(Arrays.asList(1L))
      .get();

    assertTrue(response instanceof Result.Error);
  }

  @Test(expected = IllegalStateException.class)
  public void comment_whenParentCommentIdAndStoryIdNull() throws IOException {
    dataSource.comment("text", null, null, 11L);
  }

  @Test
  public void comment_whenException()
    throws ExecutionException, InterruptedException {
    NewCommentRequest request = new NewCommentRequest(body, "11", null, "111");

    doThrow(UnknownHostException.class).when(service).comment(request);

    Result response = dataSource.comment(body, 11L, null, 111L).get();

    assertTrue(response instanceof Result.Error);
  }

  @Test
  public void comment_withNoComments()
    throws ExecutionException, InterruptedException {
    Call<PostCommentResponse> call = mock(Call.class);
    when(service.comment(any())).thenReturn(call);

    PostCommentResponse postCommentResponse = new PostCommentResponse(
      Arrays.asList()
    );
    Response<PostCommentResponse> response = Response.success(
      postCommentResponse
    );

    doAnswer(invocation -> {
        ((Callback) invocation.getArgument(0)).onResponse(call, response);
        return null;
      })
      .when(call)
      .enqueue(any());

    Result result = dataSource.comment(body, 11L, null, 111L).get();

    assertTrue(result instanceof Result.Error);
  }

  @Test
  public void comment_withComments()
    throws ExecutionException, InterruptedException {
    Call<PostCommentResponse> call = mock(Call.class);
    when(service.comment(any())).thenReturn(call);

    PostCommentResponse postCommentResponse = new PostCommentResponse(
      Arrays.asList(replyResponse1)
    );
    Response<PostCommentResponse> response = Response.success(
      postCommentResponse
    );

    doAnswer(invocation -> {
        ((Callback) invocation.getArgument(0)).onResponse(call, response);
        return null;
      })
      .when(call)
      .enqueue(any());

    Result<CommentResponse> result = dataSource
      .comment(body, 11L, null, 111L)
      .get();

    Assert.assertEquals(result, Result.Success.create(replyResponse1));
  }
}
