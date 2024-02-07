package io.plaidapp.core.designernews.data.comments;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.model.CommentResponse;
import io.plaidapp.core.data.remote.DesignerNewsCommentsRemoteDataSource;

public class CommentsRepository {

    private final DesignerNewsCommentsRemoteDataSource remoteDataSource;

    public CommentsRepository(DesignerNewsCommentsRemoteDataSource remoteDataSource) {
        this.remoteDataSource = remoteDataSource;
    }

    public suspend Result<List<CommentResponse>> getComments(List<Long> ids) {
        return remoteDataSource.getComments(ids);
    }

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

    private static volatile CommentsRepository INSTANCE;
}