package io.plaidapp.designernews.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.nhaarman.mockitokotlin2.Mockito;
import com.nhaarman.mockitokotlin2.mock;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.login.LoggedInUser;
import io.plaidapp.core.designernews.data.login.LoginRepository;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.designernews.data.comments.CommentsRepository;
import io.plaidapp.designernews.loggedInUser;
import io.plaidapp.designernews.network.ApiResponse;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PostStoryCommentUseCaseTest {

  private String body = "Plaid is plaidy";

  private CommentsRepository repository = mock(CommentsRepository.class);
  private LoginRepository loginRepository = mock(LoginRepository.class);
  private PostStoryCommentUseCase postStoryComment = new PostStoryCommentUseCase(
    repository,
    loginRepository
  );

  @Test(expected = IllegalStateException.class)
  public void postStoryComment_userNull() throws Exception {
    Mockito.when(loginRepository.getUser()).thenReturn(null);

    postStoryComment.postStoryComment("text", 123);

    Unit();
  }

  @Test
  public void postStoryComment_errorReturned() throws Exception {
    Mockito.when(loginRepository.getUser()).thenReturn(loggedInUser);

    Mockito
      .when(repository.postStoryComment(body, 123, 111))
      .thenReturn(new Result.Error<Comment>(new IOException("Error")));

    Result<Comment> result = postStoryComment.postStoryComment(body, 123);

    assertTrue(result instanceof Result.Error);
  }

  @Test
  public void postStoryComment_success() throws Exception {
    Mockito.when(loginRepository.getUser()).thenReturn(loggedInUser);

    ApiResponse replyResponse1 = new ApiResponse();
    Mockito
      .when(repository.postStoryComment(body, 123, 111))
      .thenReturn(new Result.Success<Comment>(replyResponse1));

    Result<Comment> result = postStoryComment.postStoryComment(body, 123);

    Comment expectedComment = new Comment(
      replyResponse1.getId(),
      replyResponse1.getLinks().getParentComment(),
      replyResponse1.getBody(),
      replyResponse1.getCreated_at(),
      replyResponse1.getDepth(),
      replyResponse1.getLinks().getCommentUpvotes().size(),
      loggedInUser.getId(),
      loggedInUser.getDisplayName(),
      loggedInUser.getPortraitUrl(),
      false
    );

    assertEquals(new Result.Success<Comment>(expectedComment), result);
  }
}
