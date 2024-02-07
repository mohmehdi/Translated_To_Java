package io.plaidapp.designernews.domain;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import com.nhaarman.mockitokotlin2.MockitoAnnotations;
import com.nhaarman.mockitokotlin2.verification.VerificationMode;
import com.nhaarman.mockitokotlin2.verify;
import com.nhaarman.mockitokotlin2.when;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.designernews.data.comments.CommentsRemoteDataSource;
import io.plaidapp.designernews.data.comments.CommentsRepository;
import io.plaidapp.designernews.data.comments.model.CommentResponse;
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
import kotlinx.coroutines.CompletableDeferred;
import kotlinx.coroutines.RunBlocking;
import okhttp3.ResponseBody;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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
    throws IOException {
    CompletableDeferred<Response<List<CommentResponse>>> apiResult1 = new CompletableDeferred<>();
    apiResult1.complete(Response.success(Arrays.asList(replyResponse1)));
    when(service.getComments("11")).thenReturn(apiResult1);

    CompletableDeferred<Response<List<User>>> apiResult2 = new CompletableDeferred<>();
    apiResult2.complete(Response.success(Arrays.asList(user1)));
    when(service.getUsers("111")).thenReturn(apiResult2);

    Result<List<CommentResponse>> result = repository.getComments(
      Arrays.asList(11L)
    );

    VerificationMode verificationMode = verify(service).getComments("11");
    verificationMode.times(1);

    Assert.assertEquals(Result.Success(Arrays.asList(reply1)), result);
  }

  @Test
  public void getComments_noReplies_whenCommentsRequestFailed()
    throws IOException {
    CompletableDeferred<Response<List<CommentResponse>>> apiResult = new CompletableDeferred<>();
    apiResult.complete(Response.error(400, errorResponseBody));
    when(service.getComments("11")).thenReturn(apiResult);

    Result<List<CommentResponse>> result = repository.getComments(
      Arrays.asList(11L)
    );

    Assert.assertNotNull(result);
    Assert.assertTrue(result instanceof Result.Error);
  }

  @Test
  public void getComments_multipleReplies_whenCommentsAndUsersRequestsSuccessful()
    throws IOException {
    CompletableDeferred<Response<List<CommentResponse>>> apiResult1 = new CompletableDeferred<>();
    apiResult1.complete(Response.success(Arrays.asList(parentCommentResponse)));
    when(service.getComments("1")).thenReturn(apiResult1);

    CompletableDeferred<Response<List<CommentResponse>>> apiResult2 = new CompletableDeferred<>();
    apiResult2.complete(Response.success(repliesResponses));
    when(service.getComments("11,12")).thenReturn(apiResult2);

    CompletableDeferred<Response<List<User>>> apiResult3 = new CompletableDeferred<>();
    apiResult3.complete(Response.success(Arrays.asList(user1, user2)));
    when(service.getUsers("222,111")).thenReturn(apiResult3);

    Result<List<CommentResponse>> result = repository.getComments(
      Arrays.asList(1L)
    );

    VerificationMode verificationMode1 = verify(service).getComments("1");
    verificationMode1.times(1);

    VerificationMode verificationMode2 = verify(service).getComments("11,12");
    verificationMode2.times(1);

    VerificationMode verificationMode3 = verify(service).getUsers("222,111");
    verificationMode3.times(1);

    Assert.assertEquals(Result.Success(flattendCommentsWithReplies), result);
  }

  @Test
  public void getComments_multipleReplies_whenRepliesRequestFailed()
    throws IOException {
    CompletableDeferred<Response<List<CommentResponse>>> apiResult1 = new CompletableDeferred<>();
    apiResult1.complete(Response.success(Arrays.asList(parentCommentResponse)));
    when(service.getComments("1")).thenReturn(apiResult1);

    CompletableDeferred<Response<List<CommentResponse>>> apiResult2 = new CompletableDeferred<>();
    apiResult2.complete(Response.error(400, errorResponseBody));
    when(service.getComments("11,12")).thenReturn(apiResult2);

    CompletableDeferred<Response<List<User>>> apiResult3 = new CompletableDeferred<>();
    apiResult3.complete(Response.success(Arrays.asList(user2)));
    when(service.getUsers("222")).thenReturn(apiResult3);

    Result<List<CommentResponse>> result = repository.getComments(
      Arrays.asList(1L)
    );

    VerificationMode verificationMode1 = verify(service).getComments("1");
    verificationMode1.times(1);

    VerificationMode verificationMode2 = verify(service).getComments("11,12");
    verificationMode2.times(1);

    VerificationMode verificationMode3 = verify(service).getUsers("222");
    verificationMode3.times(1);

    Assert.assertEquals(
      Result.Success(flattenedCommentsWithoutReplies),
      result
    );
  }

  @Test
  public void getComments_whenUserRequestFailed() throws IOException {
    CompletableDeferred<Response<List<CommentResponse>>> apiResult1 = new CompletableDeferred<>();
    apiResult1.complete(Response.success(Arrays.asList(replyResponse1)));
    when(service.getComments("11")).thenReturn(apiResult1);

    CompletableDeferred<Response<List<User>>> apiResult2 = new CompletableDeferred<>();
    apiResult2.complete(Response.error(400, errorResponseBody));
    when(service.getUsers("111")).thenReturn(apiResult2);

    Result<List<CommentResponse>> result = repository.getComments(
      Arrays.asList(11L)
    );

    VerificationMode verificationMode1 = verify(service).getComments("11");
    verificationMode1.times(1);

    VerificationMode verificationMode2 = verify(service).getUsers("111");
    verificationMode2.times(1);

    Assert.assertEquals(Result.Success(Arrays.asList(reply1NoUser)), result);
  }

  private void withUsers(List<User> users, String ids) {
    Response<List<User>> userResult = Response.<List<User>>success(users);
    whenever(service.getUsers(ids))
      .thenReturn(CompletableDeferred.completedFuture(userResult));
  }

  private void withComments(CommentResponse commentResponse, String ids) {
    Response<List<CommentResponse>> resultParent = Response.success(
      List.of(commentResponse)
    );
    whenever(service.getComments(ids))
      .thenReturn(new CompletableDeferred<>(resultParent));
  }

  private void withComments(
    List<CommentResponse> commentResponses,
    String ids
  ) {
    Response<List<CommentResponse>> resultParent = Response.success(
      commentResponses
    );
    whenever(service.getComments(ids))
      .thenReturn(new CompletableDeferred<>(resultParent));
  }
}
