package io.plaidapp.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.designernews.data.comments.CommentsRepository;
import io.plaidapp.core.designernews.data.comments.model.Comment;
import io.plaidapp.core.designernews.data.login.LoginRepository;
import io.plaidapp.core.designernews.domain.model.Comment as CommentDomainModel;
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

    public Result<CommentDomainModel> invoke(String body,
                                             long parentCommentId) {
        io.plaidapp.core.designernews.data.login.User user =
                loginRepository.getUser();
        if (user == null) {
            throw new IllegalStateException(
                    "User should be logged in, in order to post a comment");
        }

        Result<Comment> result = commentsRepository.postReply(body,
                parentCommentId, user.getId());

        return result.exhaustive()
                .map(comment -> {
                    CommentDomainModel commentWithNoReplies =
                            comment.toCommentWithNoReplies(user);
                    return Result.success(commentWithNoReplies);
                })
                .orElse(result);
    }
}