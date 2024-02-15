

package io.plaidapp.designernews.data.comments;

import io.plaidapp.core.data.Result;
import io.plaidapp.designernews.data.comments.model.CommentResponse;
import io.plaidapp.designernews.data.comments.remote.CommentsRemoteDataSource;

import java.util.List;

public class CommentsRepository {

    private final CommentsRemoteDataSource remoteDataSource;

    public CommentsRepository(CommentsRemoteDataSource remoteDataSource) {
        this.remoteDataSource = remoteDataSource;
    }

    public suspendResult<List<CommentResponse>> getComments(List<Long> ids) {
        return remoteDataSource.getComments(ids);
    }

    public suspendResult<CommentResponse> postStoryComment(
            String body,
            Long storyId,
            Long userId
    ) {
        return remoteDataSource.comment(
                commentBody = body,
                parentCommentId = null,
                storyId = storyId,
                userId = userId
        );
    }

    public suspendResult<CommentResponse> postReply(
            String body,
            Long parentCommentId,
            Long userId
    ) {
        return remoteDataSource.comment(
                commentBody = body,
                parentCommentId = parentCommentId,
                storyId = null,
                userId = userId
        );
    }

    private static volatile CommentsRepository INSTANCE;

    public static CommentsRepository getInstance(CommentsRemoteDataSource remoteDataSource) {
        if (INSTANCE == null) {
            synchronized (CommentsRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CommentsRepository(remoteDataSource);
                }
            }
        }
        return INSTANCE;
    }
}