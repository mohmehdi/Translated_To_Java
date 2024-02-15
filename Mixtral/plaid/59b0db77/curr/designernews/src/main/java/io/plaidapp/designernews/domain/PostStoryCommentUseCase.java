

package io.plaidapp.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.designernews.data.comments.CommentsRepository;
import io.plaidapp.designernews.data.comments.model.CommentWithNoReplies;
import io.plaidapp.core.designernews.data.login.LoginRepository;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.core.util.Exhaustive;
import javax.inject.Inject;

public class PostStoryCommentUseCase {

    private final CommentsRepository commentsRepository;
    private final LoginRepository loginRepository;

    @Inject
    public PostStoryCommentUseCase(CommentsRepository commentsRepository,
                                   LoginRepository loginRepository) {
        this.commentsRepository = commentsRepository;
        this.loginRepository = loginRepository;
    }

    public Result invoke(String body, long storyId) {
        checkNotNull(loginRepository.getUser(), "User should be logged in, in order to post a comment");
        User user = loginRepository.getUser();
        Result result = commentsRepository.postStoryComment(body, storyId, user.getId());
        return result.exhaustive().flatMap(commentResponse -> {
        Comment comment = commentResponse.toCommentWithNoReplies(user);
        return Result.success(comment);
        }).onErrorReturn(throwable -> {
        if (throwable instanceof ErrorType) {
            return Result.error((ErrorType) throwable);
        } else {
            throw new RuntimeException(throwable);
        }
        });
    }
}