

package io.plaidapp.designernews.data.comments;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.comments.model.NewCommentRequest;
import io.plaidapp.core.util.SafeApiCall;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommentsRemoteDataSource {

    private final DesignerNewsService service;

    public CommentsRemoteDataSource(DesignerNewsService service) {
        this.service = service;
    }

    public Result<List<CommentResponse>> getComments(List<Long> ids) {
        return SafeApiCall.invoke(
                this::requestGetComments,
                "Error getting comments"
        );
    }

    private Result<List<CommentResponse>> requestGetComments(List<Long> ids) {
        StringBuilder requestIds = new StringBuilder();
        for (Long id : ids) {
            requestIds.append(id).append(",");
        }
        requestIds.deleteCharAt(requestIds.length() - 1); // remove the last comma

        retrofit2.Response<List<CommentResponse>> response = service.getComments(requestIds.toString()).execute();
        if (response.isSuccessful()) {
            List<CommentResponse> body = response.body();
            if (body != null) {
                return new Result.Success<>(body);
            }
        }
        return new Result.Error<>(
                new IOException("Error getting comments " + response.code() + " " + response.message())
        );
    }

    public Result<CommentResponse> comment(
            String commentBody,
            Long parentCommentId,
            Long storyId,
            Long userId
    ) {
        if (parentCommentId == null && storyId == null) {
            throw new IllegalArgumentException("Unable to post comment. Either parent comment or the story need to be present");
        }

        return SafeApiCall.invoke(
                () -> postComment(commentBody, parentCommentId, storyId, userId),
                "Unable to post comment"
        );
    }

    private Result<CommentResponse> postComment(
            String commentBody,
            Long parentCommentId,
            Long storyId,
            Long userId
    ) {
        NewCommentRequest request = new NewCommentRequest(
                commentBody,
                parentCommentId != null ? parentCommentId.toString() : null,
                storyId != null ? storyId.toString() : null,
                userId.toString()
        );

        retrofit2.Response<NewCommentResponse> response = service.comment(request).execute();
        if (response.isSuccessful()) {
            List<CommentResponse> body = response.body().getComments();
            if (!body.isEmpty()) {
                return new Result.Success<>(body.get(0));
            }
        }
        return new Result.Error<>(new IOException("Error posting comment " + response.code() + " " + response.message()));
    }

    public static CommentsRemoteDataSource getInstance(DesignerNewsService service) {
        CommentsRemoteDataSource instance = INSTANCE;
        if (instance == null) {
            synchronized (CommentsRemoteDataSource.class) {
                instance = INSTANCE;
                if (instance == null) {
                    instance = new CommentsRemoteDataSource(service);
                    INSTANCE = instance;
                }
            }
        }
        return instance;
    }

    private static volatile CommentsRemoteDataSource INSTANCE;
}