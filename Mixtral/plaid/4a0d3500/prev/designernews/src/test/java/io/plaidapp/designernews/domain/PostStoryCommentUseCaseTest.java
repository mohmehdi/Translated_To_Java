package io.plaidapp.designernews.domain;

import com.nhaarman.mockitokotlin2.Mockito;
import com.nhaarman.mockitokotlin2.mock;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.comments.CommentsRepository;
import io.plaidapp.core.designernews.data.login.LoginRepository;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.designernews.loggedInUser;
import io.plaidapp.designernews.replyResponse1;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;

public class PostStoryCommentUseCaseTest {

  private final String body = "Plaid is plaidy";

  private final CommentsRepository repository = mock(CommentsRepository.class);
  private final LoginRepository loginRepository = mock(LoginRepository.class);
  private final PostStoryCommentUseCase postStoryComment = new PostStoryCommentUseCase(
    repository,
    loginRepository
  );

  @Test(expected = IllegalStateException.class)
  public void postStoryComment_userNull() throws Exception {
    Mockito.when(loginRepository.user).thenReturn(null);

    postStoryComment.postStoryComment("text", 123L);

    Unit();
  }

  @Test
  public void postStoryComment_errorReturned() throws Exception {
    Mockito.when(loginRepository.user).thenReturn(loggedInUser);

    Mockito
      .when(repository.postStoryComment(body, 123L, 111L))
      .thenReturn(new Result.Error<Comment>(new IOException("Error")));

    Result result = postStoryComment.postStoryComment(body, 123L);

    Assert.assertTrue(result instanceof Result.Error);
  }

  @Test
  public void postStoryComment_success() throws Exception {
    Mockito.when(loginRepository.user).thenReturn(loggedInUser);

    Mockito
      .when(repository.postStoryComment(replyResponse1.body, 123L, 111L))
      .thenReturn(new Result.Success<Comment>(replyResponse1));

    Result result = postStoryComment.postStoryComment(
      replyResponse1.body,
      123L
    );

    Comment expectedComment = new Comment(
      replyResponse1.id,
      replyResponse1.links.parentComment,
      replyResponse1.body,
      replyResponse1.created_at,
      replyResponse1.depth,
      replyResponse1.links.commentUpvotes.size(),
      loggedInUser.id,
      loggedInUser.displayName,
      loggedInUser.portraitUrl,
      false
    );

    Assert.assertEquals(new Result.Success<Comment>(expectedComment), result);
  }
}
