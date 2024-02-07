package io.plaidapp.designernews.domain;

import com.nhaarman.mockitokotlin2.Mockito;
import com.nhaarman.mockitokotlin2.invocation.InvocationOnMock;
import com.nhaarman.mockitokotlin2.stubbing.Answer;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.login.LoggedInUser;
import io.plaidapp.core.designernews.data.login.LoginRepository;
import io.plaidapp.core.designernews.data.login.User;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.designernews.data.comments.CommentsRepository;
import io.plaidapp.designernews.loggedInUser;
import io.plaidapp.designernews.replyResponse1;
import io.plaidapp.designernews.user1;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Assert;
import org.junit.Test;

public class PostReplyUseCaseTest {

  private final String body = "Plaid is plaidy";

  private final CommentsRepository repository = Mockito.mock(
    CommentsRepository.class
  );
  private final LoginRepository loginRepository = Mockito.mock(
    LoginRepository.class
  );
  private final PostReplyUseCase postReply = new PostReplyUseCase(
    repository,
    loginRepository
  );

  @Test(expected = IllegalStateException.class)
  public void postReply_userNull()
    throws ExecutionException, InterruptedException {
    Mockito.when(loginRepository.user()).thenReturn(null);

    postReply.postReply(body, 123L);

    Mockito.verify(repository).postReply(body, 123L, 111L);
  }

  @Test
  public void postReply_errorReturned()
    throws ExecutionException, InterruptedException {
    Mockito.when(loginRepository.user()).thenReturn(loggedInUser);

    Mockito
      .when(repository.postReply(body, 123L, 111L))
      .thenReturn(new Result.Error<>(new IOException("Error")));

    Future<Result<Comment>> resultFuture = Executors
      .newSingleThreadExecutor()
      .submit(() -> postReply.postReply(body, 123L));
    Result<Comment> result = resultFuture.get();

    Assert.assertTrue(result instanceof Result.Error);
  }

  @Test
  public void postReply_success()
    throws ExecutionException, InterruptedException {
    Mockito.when(loginRepository.getUser()).thenReturn(loggedInUser);

    Mockito
      .when(repository.postReply(replyResponse1.getBody(), 123L, 111L))
      .thenReturn(new Result.Success<>(replyResponse1));

    Future<Result<Comment>> resultFuture = Executors
      .newSingleThreadExecutor()
      .submit(() -> postReply.postReply(replyResponse1.body, 123L));
    Result<Comment> result = resultFuture.get();

    Comment expectedComment = new Comment(
      replyResponse1.id,
      replyResponse1.links.parentComment,
      replyResponse1.body,
      replyResponse1.created_at,
      replyResponse1.depth,
      replyResponse1.links.commentUpvotes.size(),
      user1.id,
      user1.displayName,
      user1.portraitUrl,
      false
    );

    Assert.assertEquals(new Result.Success<>(expectedComment), result);
  }
}
