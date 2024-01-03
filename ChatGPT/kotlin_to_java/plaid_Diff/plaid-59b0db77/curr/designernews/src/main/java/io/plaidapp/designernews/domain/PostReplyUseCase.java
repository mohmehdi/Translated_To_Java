package io.plaidapp.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.designernews.data.comments.CommentsRepository;
import io.plaidapp.designernews.data.comments.model.CommentWithNoRepliesKt;
import io.plaidapp.core.designernews.data.login.LoginRepository;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.core.util.exhaustive;

import javax.inject.Inject;

public class PostReplyUseCase {

    private CommentsRepository commentsRepository;
    private LoginRepository loginRepository;

    @Inject
    public PostReplyUseCase(CommentsRepository commentsRepository, LoginRepository loginRepository) {
        this.commentsRepository = commentsRepository;
        this.loginRepository = loginRepository;
    }

    public Result<Comment> invoke(String body, long parentCommentId) {
        if (loginRepository.getUser() == null) {
            throw new IllegalStateException("User should be logged in, in order to post a comment");
        }
        Comment user = loginRepository.getUser();
        Result<Comment> result = commentsRepository.postReply(body, parentCommentId, user.getId());
        if (result instanceof Result.Success) {
            Comment commentResponse = ((Result.Success<Comment>) result).getData();
            return new Result.Success<>(CommentWithNoRepliesKt.toCommentWithNoReplies(commentResponse, user));
        } else if (result instanceof Result.Error) {
            return result;
        }
        throw new IllegalStateException("exhaustive");
    }
}