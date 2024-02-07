package io.plaidapp.core.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.api.errorResponseBody;
import io.plaidapp.core.designernews.data.api.model.Comment;
import io.plaidapp.core.designernews.data.api.model.CommentResponse;
import io.plaidapp.core.designernews.data.api.model.User;
import io.plaidapp.core.designernews.data.api.parentComment;
import io.plaidapp.core.designernews.data.api.parentCommentResponse;
import io.plaidapp.core.designernews.data.api.parentCommentWithoutReplies;
import io.plaidapp.core.designernews.data.api.repliesResponses;
import io.plaidapp.core.designernews.data.api.reply1;
import io.plaidapp.core.designernews.data.api.reply1NoUser;
import io.plaidapp.core.designernews.data.api.replyResponse1;
import io.plaidapp.core.designernews.data.api.user1;
import io.plaidapp.core.designernews.data.api.user2;
import io.plaidapp.core.designernews.data.comments.CommentsRepository;
import io.plaidapp.core.designernews.data.comments.DesignerNewsCommentsRemoteDataSource;
import io.plaidapp.core.designernews.data.users.UserRemoteDataSource;
import io.plaidapp.core.designernews.data.users.UserRepository;
import io.plaidapp.core.designernews.provideCommentsUseCase;
import io.plaidapp.core.designernews.provideCommentsWithRepliesUseCase;
import io.plaidapp.core.designernews.provideFakeCoroutinesContextProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableDeferred;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
  private final provideCommentsUseCase repository = provideCommentsUseCase.provideCommentsUseCase(
    provideCommentsWithRepliesUseCase.provideCommentsWithRepliesUseCase(
      commentsRepository
    ),
    userRepository,
    provideFakeCoroutinesContextProvider.provideFakeCoroutinesContextProvider()
  );

  @Test
  public void getComments_noReplies_whenCommentsAnUserRequestsSuccessful()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> replyResponseDeferred = new CompletableDeferred<>();
    replyResponseDeferred.complete(
      Response.success(Arrays.asList(replyResponse1))
    );
    Mockito.when(service.getComments("11")).thenReturn(replyResponseDeferred);

    CompletableDeferred<Response<List<User>>> userDeferred = new CompletableDeferred<>();
    userDeferred.complete(Response.success(Arrays.asList(user1)));
    Mockito.when(service.getUsers("111")).thenReturn(userDeferred);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Result<List<Comment>> result = repository.getComments(
      Arrays.asList(11L),
      new CommentsUseCase.GetCommentsCallback() {
        @Override
        public void onResult(Result<List<Comment>> result) {
          executor.execute(() -> {
            try {
              Assert.assertEquals(
                Result.Success(Arrays.asList(reply1)),
                result
              );
            } catch (AssertionError e) {
              executor.shutdownNow();
              throw e;
            }
          });
        }
      }
    );

    Mockito.verify(service).getComments("11");
  }

  @Test
  public void getComments_noReplies_whenCommentsRequestFailed() {
    Mockito
      .when(service.getComments("11"))
      .thenReturn(
        CompletableDeferred.completed(Response.error(400, errorResponseBody))
      );

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Result<List<Comment>> result = null;
    repository.getComments(
      Arrays.asList(11L),
      new CommentsUseCase.GetCommentsCallback() {
        @Override
        public void onResult(Result<List<Comment>> result) {
          executor.execute(() -> {
            try {
              Assert.assertNotNull(result);
              Assert.assertTrue(result instanceof Result.Error);
            } catch (AssertionError e) {
              executor.shutdownNow();
              throw e;
            }
          });
        }
      }
    );

    Mockito.verify(service).getComments("11");
  }

  @Test
  public void getComments_multipleReplies_whenCommentsAndUsersRequestsSuccessful()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> parentCommentResponseDeferred = new CompletableDeferred<>();
    parentCommentResponseDeferred.complete(
      Response.success(Arrays.asList(parentCommentResponse))
    );
    Mockito
      .when(service.getComments("1"))
      .thenReturn(parentCommentResponseDeferred);

    CompletableDeferred<Response<List<CommentResponse>>> repliesResponsesDeferred = new CompletableDeferred<>();
    repliesResponsesDeferred.complete(
      Response.success(
        Arrays.asList(new CommentResponse(), new CommentResponse())
      )
    );
    Mockito
      .when(service.getComments("11,12"))
      .thenReturn(repliesResponsesDeferred);

    CompletableDeferred<Response<List<User>>> userDeferred = new CompletableDeferred<>();
    userDeferred.complete(Response.success(Arrays.asList(user1, user2)));
    Mockito.when(service.getUsers("222,111")).thenReturn(userDeferred);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Result<List<Comment>> result = repository.getComments(
      Arrays.asList(1L),
      new CommentsUseCase.GetCommentsCallback() {
        @Override
        public void onResult(Result<List<Comment>> result) {
          executor.execute(() -> {
            try {
              Assert.assertEquals(
                Result.Success(Arrays.asList(parentComment)),
                result
              );
            } catch (AssertionError e) {
              executor.shutdownNow();
              throw e;
            }
          });
        }
      }
    );

    Mockito.verify(service).getComments("1");
    Mockito.verify(service).getComments("11,12");
    Mockito.verify(service).getUsers("222,111");
  }

  @Test
  public void getComments_multipleReplies_whenRepliesRequestFailed()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> parentCommentResponseDeferred = new CompletableDeferred<>();
    parentCommentResponseDeferred.complete(
      Response.success(Arrays.asList(parentCommentResponse))
    );
    Mockito
      .when(service.getComments("1"))
      .thenReturn(parentCommentResponseDeferred);

    CompletableDeferred<Response<List<CommentResponse>>> repliesResponsesDeferred = new CompletableDeferred<>();
    repliesResponsesDeferred.complete(Response.error(400, errorResponseBody));
    Mockito
      .when(service.getComments("11,12"))
      .thenReturn(repliesResponsesDeferred);

    CompletableDeferred<Response<List<User>>> userDeferred = new CompletableDeferred<>();
    userDeferred.complete(Response.success(Arrays.asList(user2)));
    Mockito.when(service.getUsers("222")).thenReturn(userDeferred);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Result<List<Comment>> result = repository.getComments(
      Arrays.asList(1L),
      new CommentsUseCase.GetCommentsCallback() {
        @Override
        public void onResult(Result<List<Comment>> result) {
          executor.execute(() -> {
            try {
              Assert.assertEquals(
                Result.Success(Arrays.asList(parentCommentWithoutReplies)),
                result
              );
            } catch (AssertionError e) {
              executor.shutdownNow();
              throw e;
            }
          });
        }
      }
    );

    Mockito.verify(service).getComments("1");
    Mockito.verify(service).getComments("11,12");
    Mockito.verify(service).getUsers("222");
  }

  @Test
  public void getComments_whenUserRequestFailed()
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> replyResponseDeferred = new CompletableDeferred<>();
    replyResponseDeferred.complete(
      Response.success(Arrays.asList(replyResponse1))
    );
    Mockito.when(service.getComments("11")).thenReturn(replyResponseDeferred);

    CompletableDeferred<Response<List<User>>> userDeferred = new CompletableDeferred<>();
    userDeferred.complete(Response.error(400, errorResponseBody));
    Mockito.when(service.getUsers("111")).thenReturn(userDeferred);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Result<List<Comment>> result = repository.getComments(
      Arrays.asList(11L),
      new CommentsUseCase.GetCommentsCallback() {
        @Override
        public void onResult(Result<List<Comment>> result) {
          executor.execute(() -> {
            try {
              Assert.assertEquals(
                Result.Success(Arrays.asList(reply1NoUser)),
                result
              );
            } catch (AssertionError e) {
              executor.shutdownNow();
              throw e;
            }
          });
        }
      }
    );

    Mockito.verify(service).getComments("11");
    Mockito.verify(service).getUsers("111");
  }

  private void withUsers(List<User> users, String ids)
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<User>>> userDeferred = new CompletableDeferred<>();
    userDeferred.complete(Response.success(users));
    Mockito.when(service.getUsers(ids)).thenReturn(userDeferred);
  }

  private void withComments(CommentResponse commentResponse, String ids)
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> commentDeferred = new CompletableDeferred<>();
    commentDeferred.complete(Response.success(Arrays.asList(commentResponse)));
    Mockito.when(service.getComments(ids)).thenReturn(commentDeferred);
  }

  private void withComments(List<CommentResponse> commentResponse, String ids)
    throws ExecutionException, InterruptedException {
    CompletableDeferred<Response<List<CommentResponse>>> commentDeferred = new CompletableDeferred<>();
    commentDeferred.complete(Response.success(commentResponse));
    Mockito.when(service.getComments(ids)).thenReturn(commentDeferred);
  }
}
