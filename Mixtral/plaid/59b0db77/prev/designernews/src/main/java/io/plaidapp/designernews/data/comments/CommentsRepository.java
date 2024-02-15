

package io.plaidapp.designernews.data.comments;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.designernews.data.comments.CommentsRemoteDataSource;

import java.util.List;

public class CommentsRepository {

    private final CommentsRemoteDataSource remoteDataSource;

    public CommentsRepository(CommentsRemoteDataSource remoteDataSource) {
        this.remoteDataSource = remoteDataSource;
    }

    public Result<List<CommentResponse>> getComments(List<Long> ids) {
        return remoteDataSource.getComments(ids);
    }

    public Result<CommentResponse> postStoryComment(
            String body,
            Long storyId,
            Long userId) {
        return remoteDataSource.comment(body, null, storyId, userId);
    }

    public Result<CommentResponse> postReply(
            String body,
            Long parentCommentId,
            Long userId) {
        return remoteDataSource.comment(body, parentCommentId, null, userId);
    }


}