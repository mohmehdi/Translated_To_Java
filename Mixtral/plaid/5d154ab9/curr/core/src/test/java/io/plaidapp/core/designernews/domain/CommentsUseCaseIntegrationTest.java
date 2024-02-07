package io.plaidapp.core.designernews.domain;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.comments.CommentsRepository;
import io.plaidapp.core.designernews.data.comments.DesignerNewsCommentsRemoteDataSource;
import io.plaidapp.core.designernews.data.users.UserRemoteDataSource;
import io.plaidapp.core.designernews.data.users.UserRepository;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.core.designernews.errorResponseBody;
import io.plaidapp.core.designernews.provideCommentsUseCase;
import io.plaidapp.core.designernews.provideCommentsWithRepliesUseCase;
import io.plaidapp.core.designernews.provideFakeCoroutinesContextProvider;
import io.plaidapp.core.designernews.repliesResponses;
import io.plaidapp.core.designernews.reply1;
import io.plaidapp.core.designernews.reply1NoUser;
import io.plaidapp.core.designernews.replyResponse1;
import io.plaidapp.core.designernews.user1;
import io.plaidapp.core.designernews.user2;
import io.plaidapp.test.shared.provideFakeCoroutinesContextProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableDeferred;
import java.util.concurrent.ExecutionException;
import kotlin.coroutines.experimental.BuildersKt;
import kotlin.coroutines.experimental.ChannelCoroutineContext;
import kotlin.coroutines.experimental.CoroutineContext;
import kotlin.coroutines.experimental.CoroutineStart;
import kotlin.coroutines.experimental.EmptyCoroutineContextException;
import kotlin.coroutines.experimental.intrinsics.*;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import retrofit2.Response;

public class CommentsUseCaseIntegrationTest {

  private DesignerNewsService service = Mockito.mock(DesignerNewsService.class);
  private DesignerNewsCommentsRemoteDataSource dataSource = new DesignerNewsCommentsRemoteDataSource(
    service
  );
  private CommentsRepository commentsRepository = new CommentsRepository(
    dataSource
  );
  private UserRepository userRepository = new UserRepository(
    new UserRemoteDataSource(service)
  );
  private CommentsUseCase repository = provideCommentsUseCase(
    provideCommentsWithRepliesUseCase(commentsRepository),
    userRepository,
    provideFakeCoroutinesContextProvider()
  );

  @Test
  public void getComments_noReplies_whenCommentsAnUserRequestsSuccessful()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> deferred = new CompletableDeferred<>();
    deferred.complete(Response.success(Arrays.asList(replyResponse1)));
    when(service.getComments("11")).thenReturn(deferred);

    Result<List<Comment>> result = repository
      .getComments(Arrays.asList(11L))
      .get();

    verify(service).getComments("11");
    Assert.assertEquals(Result.Success(Arrays.asList(reply1)), result);
  }

  @Test
  public void getComments_noReplies_whenCommentsRequestFailed() {
    Response<List<CommentResponse>> apiResult = Response.error(
      400,
      errorResponseBody
    );
    when(service.getComments("11"))
      .thenReturn(new CompletableDeferred<>(apiResult));

    Result<List<Comment>> result = repository
      .getComments(Arrays.asList(11L))
      .get();

    Assert.assertNotNull(result);
    Assert.assertTrue(result instanceof Result.Error);
  }

  @Test
  public void getComments_multipleReplies_whenCommentsAndUsersRequestsSuccessful()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> deferred1 = new CompletableDeferred<>();
    deferred1.complete(Response.success(Arrays.asList(parentCommentResponse)));
    when(service.getComments("1")).thenReturn(deferred1);

    CompletableDeferred<Response<List<CommentResponse>>> deferred2 = new CompletableDeferred<>();
    deferred2.complete(Response.success(repliesResponses));
    when(service.getComments("11,12")).thenReturn(deferred2);

    CompletableDeferred<Response<List<User>>> deferred3 = new CompletableDeferred<>();
    deferred3.complete(Response.success(Arrays.asList(user1, user2)));
    when(service.getUsers("222,111")).thenReturn(deferred3);

    Result<List<Comment>> result = repository
      .getComments(Arrays.asList(1L))
      .get();

    verify(service).getComments("1");
    verify(service).getComments("11,12");
    verify(service).getUsers("222,111");
    Assert.assertEquals(Result.Success(Arrays.asList(parentComment)), result);
  }

  @Test
  public void getComments_multipleReplies_whenRepliesRequestFailed()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> deferred1 = new CompletableDeferred<>();
    deferred1.complete(Response.success(Arrays.asList(parentCommentResponse)));
    when(service.getComments("1")).thenReturn(deferred1);

    CompletableDeferred<Response<List<CommentResponse>>> deferred2 = new CompletableDeferred<>();
    deferred2.complete(Response.error(400, errorResponseBody));
    when(service.getComments("11,12")).thenReturn(deferred2);

    CompletableDeferred<Response<List<User>>> deferred3 = new CompletableDeferred<>();
    deferred3.complete(Response.success(Arrays.asList(user2)));
    when(service.getUsers("222")).thenReturn(deferred3);

    Result<List<Comment>> result = repository
      .getComments(Arrays.asList(1L))
      .get();

    verify(service).getComments("1");
    verify(service).getComments("11,12");
    verify(service).getUsers("222");
    Assert.assertEquals(
      Result.Success(Arrays.asList(parentCommentWithoutReplies)),
      result
    );
  }

  @Test
  public void getComments_whenUserRequestFailed()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> deferred1 = new CompletableDeferred<>();
    deferred1.complete(Response.success(Arrays.asList(replyResponse1)));
    when(service.getComments("11")).thenReturn(deferred1);

    CompletableDeferred<Response<List<User>>> deferred2 = new CompletableDeferred<>();
    deferred2.complete(Response.error(400, errorResponseBody));
    when(service.getUsers("111")).thenReturn(deferred2);

    Result<List<Comment>> result = repository
      .getComments(Arrays.asList(11L))
      .get();

    verify(service).getComments("11");
    verify(service).getUsers("111");
    Assert.assertEquals(Result.Success(Arrays.asList(reply1NoUser)), result);
  }

  private void withUsers(List<User> users, String ids)
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<User>>> deferred = new CompletableDeferred<>();
    deferred.complete(Response.success(users));
    when(service.getUsers(ids)).thenReturn(deferred);
  }

  private void withComments(CommentResponse commentResponse, String ids) {
    CompletableDeferred<Response<List<CommentResponse>>> deferred = new CompletableDeferred<>();
    deferred.complete(Response.success(Arrays.asList(commentResponse)));
    when(service.getComments(ids)).thenReturn(deferred);
  }

  private void withComments(List<CommentResponse> commentResponse, String ids) {
    CompletableDeferred<Response<List<CommentResponse>>> deferred = new CompletableDeferred<>();
    deferred.complete(Response.success(commentResponse));
    when(service.getComments(ids)).thenReturn(deferred);
  }
}
