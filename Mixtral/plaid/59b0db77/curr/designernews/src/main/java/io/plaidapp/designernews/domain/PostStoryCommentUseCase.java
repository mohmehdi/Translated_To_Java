package io.plaidapp.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.login.LoginRepository;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.core.util.Exhaustive;
import io.plaidapp.designernews.data.comments.CommentsRepository;
import io.plaidapp.designernews.data.comments.model.CommentWithNoReplies;
import javax.inject.Inject;

public class PostStoryCommentUseCase {

  private final CommentsRepository commentsRepository;
  private final LoginRepository loginRepository;

  @Inject
  public PostStoryCommentUseCase(
    CommentsRepository commentsRepository,
    LoginRepository loginRepository
  ) {
    this.commentsRepository = commentsRepository;
    this.loginRepository = loginRepository;
  }

  public Result<Comment> invoke(String body, long storyId) {
    checkNotNull(
      loginRepository.getUser(),
      "User should be logged in, in order to post a comment"
    );
    User user = loginRepository.getUser();
    Result<CommentResponse> result = commentsRepository.postStoryComment(
      body,
      storyId,
      user.getId()
    );

    return switch (result) {
      case Result.Success success -> {
        CommentResponse commentResponse = success.getData();
        yield Result.Success(new CommentWithNoReplies(commentResponse, user));
      }
      case Result.Error error -> error;
    };
  }
}
