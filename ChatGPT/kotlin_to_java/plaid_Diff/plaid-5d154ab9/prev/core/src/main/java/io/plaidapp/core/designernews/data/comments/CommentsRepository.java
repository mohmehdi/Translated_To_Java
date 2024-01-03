package io.plaidapp.core.designernews.data.comments;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.model.CommentResponse;

public class CommentsRepository {

    private DesignerNewsCommentsRemoteDataSource remoteDataSource;

    public CommentsRepository(DesignerNewsCommentsRemoteDataSource remoteDataSource) {
        this.remoteDataSource = remoteDataSource;
    }

    public Result<List<CommentResponse>> getComments(List<Long> ids) {
        return remoteDataSource.getComments(ids);
    }

    private static volatile CommentsRepository INSTANCE;

    public static CommentsRepository getInstance(DesignerNewsCommentsRemoteDataSource remoteDataSource) {
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