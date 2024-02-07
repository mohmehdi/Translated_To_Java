package io.plaidapp.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.designernews.data.comments.CommentsRepository;
import io.plaidapp.designernews.data.comments.model.Comment;
import io.plaidapp.core.designernews.data.login.LoginRepository;
import io.plaidapp.core.designernews.domain.model.Comment as CommentModel;
import io.plaidapp.core.util.Exhaustive;
import javax.inject.Inject;

public class PostReplyUseCase {

    private final CommentsRepository commentsRepository;
    private final LoginRepository loginRepository;

    @Inject
    public PostReplyUseCase(CommentsRepository commentsRepository,
                             LoginRepository loginRepository) {
        this.commentsRepository = commentsRepository;
        this.loginRepository = loginRepository;
    }

    public Result<CommentModel> invoke(String body, long parentCommentId) {
        CommentModel user = loginRepository.getUser().orElse(null);
        if (user == null) {
            throw new IllegalStateException(
                    "User should be logged in, in order to post a comment");
        }

        Result<Comment> result = commentsRepository.postReply(body, parentCommentId,
                user.getId());

        return result. (!(result instanceof Result.Success)) ? result :
                Result.Success.create(
                        ((Comment) result.getData()).toCommentWithNoReplies(user));
    }
}