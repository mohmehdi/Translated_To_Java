package io.plaidapp.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.comments.CommentsRepository;
import io.plaidapp.core.designernews.data.comments.model.CommentWithNoRepliesKt;
import io.plaidapp.core.designernews.data.login.LoginRepository;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.core.util.exhaustive;

import javax.inject.Inject;

public class PostStoryCommentUseCase {

    private CommentsRepository commentsRepository;
    private LoginRepository loginRepository;

    @Inject
    public PostStoryCommentUseCase(CommentsRepository commentsRepository, LoginRepository loginRepository) {
        this.commentsRepository = commentsRepository;
        this.loginRepository = loginRepository;
    }

    public Result<Comment> invoke(String body, long storyId) {
        if (loginRepository.getUser() == null) {
            throw new IllegalStateException("User should be logged in, in order to post a comment");
        }
        Comment user = loginRepository.getUser();
        Result<Comment> result = commentsRepository.postStoryComment(body, storyId, user.getId());
        if (result instanceof Result.Success) {
            Comment commentResponse = ((Result.Success<Comment>) result).getData();
            return new Result.Success<>(CommentWithNoRepliesKt.toCommentWithNoReplies(commentResponse, user));
        } else if (result instanceof Result.Error) {
            return result;
        }
        throw new IllegalStateException("Result should be either Success or Error");
    }
}