package io.plaidapp.designernews.data.comments;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;

public class CommentsRepository {

    private CommentsRemoteDataSource remoteDataSource;

    public CommentsRepository(CommentsRemoteDataSource remoteDataSource) {
        this.remoteDataSource = remoteDataSource;
    }

    public Result<List<CommentResponse>> getComments(List<Long> ids) {
        return remoteDataSource.getComments(ids);
    }

    public Result<CommentResponse> postStoryComment(String body, long storyId, long userId) {
        return remoteDataSource.comment(body, null, storyId, userId);
    }

    public Result<CommentResponse> postReply(String body, long parentCommentId, long userId) {
        return remoteDataSource.comment(body, parentCommentId, null, userId);
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