package io.plaidapp.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.comments.CommentsRepository;
import io.plaidapp.core.data.comments.model.CommentWithNoReplies;
import io.plaidapp.core.data.login.LoginRepository;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.core.util.Exhaustive;
import javax.inject.Inject;

public class PostReplyUseCase {

  private final CommentsRepository commentsRepository;
  private final LoginRepository loginRepository;

  @Inject
  public PostReplyUseCase(
    CommentsRepository commentsRepository,
    LoginRepository loginRepository
  ) {
    this.commentsRepository = commentsRepository;
    this.loginRepository = loginRepository;
  }

  public Result<Comment> invoke(String body, long parentCommentId) {
    checkNotNull(
      loginRepository.getUser(),
      "User should be logged in, in order to post a comment"
    );
    User user = loginRepository.getUser();
    Result<CommentResponse> result = commentsRepository.postReply(
      body,
      parentCommentId,
      user.getId()
    );
    if (result instanceof Result.Success) {
      CommentResponse commentResponse = result.getData();
      return new Result.Success<>(
        new CommentWithNoReplies(commentResponse, user)
      );
    } else if (result instanceof Result.Error) {
      return (Result.Error) result;
    } else {
      throw new Exhaustive();
    }
  }
}
