package io.plaidapp.core.designernews.data.comments;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.api.errorResponseBody;
import io.plaidapp.core.designernews.data.api.model.CommentResponse;
import io.plaidapp.core.designernews.data.api.repliesResponses;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import retrofit2.Response;
import retrofit2.Retrofit;

public class DesignerNewsCommentsRemoteDataSourceTest {

  @Mock
  DesignerNewsService service;

  private DesignerNewsCommentsRemoteDataSource dataSource;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    dataSource = new DesignerNewsCommentsRemoteDataSource(service);
  }

  @Test
  public void getComments_whenRequestSuccessful()
    throws ExecutionException, InterruptedException {
    Response<List<CommentResponse>> result = Response.success(repliesResponses);
    CompletableFuture<Response<List<CommentResponse>>> future = new CompletableFuture<>();
    future.complete(result);
    Mockito.when(service.getComments("1")).thenReturn(future);

    Result<List<CommentResponse>> response = dataSource
      .getComments(Arrays.asList(1L))
      .get();

    Assert.assertNotNull(response);
    Assert.assertEquals(Result.Success(repliesResponses), response);
  }

  @Test
  public void getComments_forMultipleComments()
    throws ExecutionException, InterruptedException {
    Response<List<CommentResponse>> result = Response.success(repliesResponses);
    CompletableFuture<Response<List<CommentResponse>>> future = new CompletableFuture<>();
    future.complete(result);
    Mockito.when(service.getComments("11,12")).thenReturn(future);

    Result<List<CommentResponse>> response = dataSource
      .getComments(Arrays.asList(11L, 12L))
      .get();

    Assert.assertNotNull(response);
    Assert.assertEquals(Result.Success(repliesResponses), response);
  }

  @Test
  public void getComments_whenRequestFailed()
    throws ExecutionException, InterruptedException {
    Response<List<CommentResponse>> result = Response.error(
      400,
      errorResponseBody
    );
    CompletableFuture<Response<List<CommentResponse>>> future = new CompletableFuture<>();
    future.complete(result);
    Mockito.when(service.getComments("1")).thenReturn(future);

    Result<List<CommentResponse>> response = dataSource
      .getComments(Arrays.asList(1L))
      .get();

    Assert.assertTrue(response instanceof Result.Error);
  }

  @Test
  public void getComments_whenResponseEmpty()
    throws ExecutionException, InterruptedException {
    Response<List<CommentResponse>> result = Response.success(
      (List<CommentResponse>) null
    );
    CompletableFuture<Response<List<CommentResponse>>> future = new CompletableFuture<>();
    future.complete(result);
    Mockito.when(service.getComments("1")).thenReturn(future);

    Result<List<CommentResponse>> response = dataSource
      .getComments(Arrays.asList(1L))
      .get();

    Assert.assertTrue(response instanceof Result.Error);
  }
}
