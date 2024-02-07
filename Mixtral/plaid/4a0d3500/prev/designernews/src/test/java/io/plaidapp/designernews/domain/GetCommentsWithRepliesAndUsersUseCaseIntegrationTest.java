package io.plaidapp.designernews.domain;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import com.nhaarman.mockitokotlin2.Mockito;
import com.nhaarman.mockitokotlin2.internal.runners.junit.JUnit5Runner;
import com.nhaarman.mockitokotlin2.verify;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.comments.CommentsRemoteDataSource;
import io.plaidapp.core.designernews.data.comments.CommentsRepository;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.designernews.data.users.UserRemoteDataSource;
import io.plaidapp.designernews.data.users.UserRepository;
import io.plaidapp.designernews.errorResponseBody;
import io.plaidapp.designernews.flattendCommentsWithReplies;
import io.plaidapp.designernews.flattenedCommentsWithoutReplies;
import io.plaidapp.designernews.parentCommentResponse;
import io.plaidapp.designernews.repliesResponses;
import io.plaidapp.designernews.reply1;
import io.plaidapp.designernews.reply1NoUser;
import io.plaidapp.designernews.replyResponse1;
import io.plaidapp.designernews.user1;
import io.plaidapp.designernews.user2;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import kotlinx.coroutines.CompletableDeferred;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@RunWith(JUnit5Runner.class)
public class GetCommentsWithRepliesAndUsersUseCaseIntegrationTest {

  private DesignerNewsService service = mock(DesignerNewsService.class);
  private CommentsRemoteDataSource dataSource = new CommentsRemoteDataSource(
    service
  );
  private CommentsRepository commentsRepository = new CommentsRepository(
    dataSource
  );
  private UserRepository userRepository = new UserRepository(
    new UserRemoteDataSource(service)
  );
  private GetCommentsWithRepliesAndUsersUseCase repository = new GetCommentsWithRepliesAndUsersUseCase(
    new GetCommentsWithRepliesUseCase(commentsRepository),
    userRepository
  );

  @Test
  public void getComments_noReplies_whenCommentsAnUserRequestsSuccessful()
    throws ExecutionException, InterruptedException {
    withComments(replyResponse1, "11");

    withUsers(Arrays.asList(user1), "111");

    Result<List<CommentResponse>> result = repository
      .getComments(Arrays.asList(11L))
      .get();

    verify(service).getComments("11");

    Assert.assertEquals(Result.Success.create(Arrays.asList(reply1)), result);
  }

  @Test
  public void getComments_noReplies_whenCommentsRequestFailed()
    throws ExecutionException, InterruptedException {
    Response<List<CommentResponse>> apiResult = Response.error(
      400,
      errorResponseBody
    );
    Mockito
      .when(service.getComments(anyString()))
      .thenReturn(CompletableDeferred.completedFuture(apiResult));

    Result<List<CommentResponse>> result = repository
      .getComments(Arrays.asList(11L))
      .get();

    Assert.assertNotNull(result);
    Assert.assertTrue(result instanceof Result.Error);
  }

  @Test
  public void getComments_multipleReplies_whenCommentsAndUsersRequestsSuccessful()
    throws ExecutionException, InterruptedException {
    withComments(parentCommentResponse, "1");

    withComments(repliesResponses, "11,12");

    withUsers(Arrays.asList(user1, user2), "222,111");

    Result<List<CommentResponse>> result = repository
      .getComments(Arrays.asList(1L))
      .get();

    verify(service).getComments("1");
    verify(service).getComments("11,12");
    verify(service).getUsers("222,111");

    Assert.assertEquals(
      Result.Success.create(flattendCommentsWithReplies),
      result
    );
  }

  @Test
  public void getComments_multipleReplies_whenRepliesRequestFailed()
    throws ExecutionException, InterruptedException {
    withComments(parentCommentResponse, "1");

    Response<List<CommentResponse>> resultChildrenError = Response.error(
      400,
      errorResponseBody
    );
    Mockito
      .when(service.getComments("11,12"))
      .thenReturn(CompletableDeferred.completedFuture(resultChildrenError));

    withUsers(Arrays.asList(user2), "222");

    Result<List<CommentResponse>> result = repository
      .getComments(Arrays.asList(1L))
      .get();

    verify(service).getComments("1");
    verify(service).getComments("11,12");
    verify(service).getUsers("222");

    Assert.assertEquals(
      Result.Success.create(flattenedCommentsWithoutReplies),
      result
    );
  }

  @Test
  public void getComments_whenUserRequestFailed()
    throws ExecutionException, InterruptedException {
    withComments(replyResponse1, "11");

    Response<List<User>> userError = Response.error(400, errorResponseBody);
    Mockito
      .when(service.getUsers(anyString()))
      .thenReturn(CompletableDeferred.completedFuture(userError));

    Result<List<CommentResponse>> result = repository
      .getComments(Arrays.asList(11L))
      .get();

    verify(service).getComments("11");
    verify(service).getUsers(anyString());

    Assert.assertEquals(
      Result.Success.create(Arrays.asList(reply1NoUser)),
      result
    );
  }

  private void withUsers(List<User> users, String ids)
    throws ExecutionException, InterruptedException {
    Response<List<User>> userResult = Response.success(users);
    Mockito
      .when(service.getUsers(ids))
      .thenReturn(CompletableDeferred.completedFuture(userResult));
  }

  private void withComments(CommentResponse commentResponse, String ids)
    throws ExecutionException, InterruptedException {
    Response<List<CommentResponse>> resultParent = Response.success(
      Arrays.asList(commentResponse)
    );
    Mockito
      .when(service.getComments(ids))
      .thenReturn(CompletableDeferred.completedFuture(resultParent));
  }

  private void withComments(List<CommentResponse> commentResponse, String ids)
    throws ExecutionException, InterruptedException {
    Response<List<CommentResponse>> resultParent = Response.success(
      commentResponse
    );
    Mockito
      .when(service.getComments(ids))
      .thenReturn(CompletableDeferred.completedFuture(resultParent));
  }
}
