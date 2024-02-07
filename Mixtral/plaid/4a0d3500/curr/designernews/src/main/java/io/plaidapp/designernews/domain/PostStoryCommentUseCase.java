package io.plaidapp.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.comments.model.Comment;
import io.plaidapp.core.designernews.data.comments.model.CommentWithNoReplies;
import io.plaidapp.core.designernews.data.login.LoginRepository;
import io.plaidapp.core.designernews.domain.model.User;
import io.plaidapp.core.util.Exhaustive;
import io.plaidapp.designernews.data.comments.CommentsRepository;
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
    User user = loginRepository.getUser();
    if (user == null) {
      throw new IllegalStateException(
        "User should be logged in, in order to post a comment"
      );
    }

    Result<CommentWithNoReplies> result = commentsRepository.postStoryComment(
      body,
      storyId,
      user.getId()
    );

    return result
      .exhaustive()
      .map(commentResponse ->
        new Result.Success<>(new Comment(commentResponse, user))
      );
  }
}
