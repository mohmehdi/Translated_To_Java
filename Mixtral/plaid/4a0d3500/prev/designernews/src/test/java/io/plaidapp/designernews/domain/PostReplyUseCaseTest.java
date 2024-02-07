package io.plaidapp.designernews.domain;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nhaarman.mockitokotlin2.Mockito;
import com.nhaarman.mockitokotlin2.invocation.InvocationOnMock;
import com.nhaarman.mockitokotlin2.stub.Answer;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.comments.CommentsRepository;
import io.plaidapp.core.designernews.data.login.LoginRepository;
import io.plaidapp.core.designernews.data.login.User;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.designernews.loggedInUser;
import io.plaidapp.designernews.replyResponse1;
import io.plaidapp.designernews.user1;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Assert;
import org.junit.Test;

public class PostReplyUseCaseTest {

  private final String body = "Plaid is plaidy";

  private final CommentsRepository repository = mock(CommentsRepository.class);
  private final LoginRepository loginRepository = mock(LoginRepository.class);
  private final PostReplyUseCase postReply = new PostReplyUseCase(
    repository,
    loginRepository
  );

  @Test(expected = IllegalStateException.class)
  public void postReply_userNull()
    throws ExecutionException, InterruptedException {
    when(loginRepository.user).thenReturn(null);

    postReply.postReply("text", 123L);
  }

  @Test
  public void postReply_errorReturned()
    throws ExecutionException, InterruptedException {
    when(loginRepository.user).thenReturn(loggedInUser);

    Answer<Result<Comment>> errorAnswer = invocation -> {
      throw new IOException("Error");
    };

    Mockito
      .when(repository.postReply(body, 123L, 111L))
      .thenAnswer(errorAnswer);

    Result<Comment> result = postReply.postReply(body, 123L);

    Assert.assertTrue(result instanceof Result.Error);
  }

  @Test
  public void postReply_success()
    throws ExecutionException, InterruptedException {
    when(loginRepository.user).thenReturn(loggedInUser);

    Answer<Result<Comment>> successAnswer = invocation ->
      Result.success(replyResponse1.toBuilder().build());

    Mockito
      .when(repository.postReply(replyResponse1.getBody(), 123L, 111L))
      .thenAnswer(successAnswer);

    Result<Comment> result = postReply.postReply(
      replyResponse1.getBody(),
      123L
    );

    Comment expectedComment = new Comment.Builder()
      .id(replyResponse1.getId())
      .parentCommentId(replyResponse1.getLinks().getParentComment())
      .body(replyResponse1.getBody())
      .createdAt(replyResponse1.getCreated_at())
      .depth(replyResponse1.getDepth())
      .upvotesCount(replyResponse1.getLinks().getCommentUpvotes().size())
      .userId(user1.getId())
      .userDisplayName(user1.getDisplayName())
      .userPortraitUrl(user1.getPortraitUrl())
      .upvoted(false)
      .build();

    Assert.assertEquals(Result.success(expectedComment), result);
  }
}
