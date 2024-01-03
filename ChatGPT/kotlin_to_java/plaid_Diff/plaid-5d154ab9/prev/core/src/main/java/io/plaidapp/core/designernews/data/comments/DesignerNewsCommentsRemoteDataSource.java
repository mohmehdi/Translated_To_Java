package io.plaidapp.core.designernews.data.comments;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.api.model.CommentResponse;
import java.io.IOException;
import java.util.List;

public class DesignerNewsCommentsRemoteDataSource {

    private DesignerNewsService service;

    public DesignerNewsCommentsRemoteDataSource(DesignerNewsService service) {
        this.service = service;
    }

    public Result<List<CommentResponse>> getComments(List<Long> ids) {
        String requestIds = ids.stream().map(Object::toString).collect(Collectors.joining(","));
        try {
            Response<List<CommentResponse>> response = service.getComments(requestIds).execute();
            if (response.isSuccessful()) {
                List<CommentResponse> body = response.body();
                if (body != null) {
                    return new Result.Success<>(body);
                }
            }
            return new Result.Error(new IOException("Error getting comments " + response.code() + " " + response.message()));
        } catch (IOException e) {
            return new Result.Error(e);
        }
    }

    private static volatile DesignerNewsCommentsRemoteDataSource INSTANCE;

    public static DesignerNewsCommentsRemoteDataSource getInstance(DesignerNewsService service) {
        if (INSTANCE == null) {
            synchronized (DesignerNewsCommentsRemoteDataSource.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DesignerNewsCommentsRemoteDataSource(service);
                }
            }
        }
        return INSTANCE;
    }
}