package io.plaidapp.designernews.data.comments;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.comments.model.NewCommentRequest;
import io.plaidapp.core.util.safeApiCall;
import java.io.IOException;

public class CommentsRemoteDataSource {

    private DesignerNewsService service;

    public CommentsRemoteDataSource(DesignerNewsService service) {
        this.service = service;
    }

    public Result<List<CommentResponse>> getComments(List<Long> ids) {
        return safeApiCall(
                () -> requestGetComments(ids),
                "Error getting comments"
        );
    }

    private Result<List<CommentResponse>> requestGetComments(List<Long> ids) {
        String requestIds = ids.stream().map(Object::toString).collect(Collectors.joining(","));
        Response<List<CommentResponse>> response;
        try {
            response = service.getComments(requestIds).execute();
            if (response.isSuccessful()) {
                List<CommentResponse> body = response.body();
                if (body != null) {
                    return new Result.Success<>(body);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new Result.Error<>(new IOException("Error getting comments " + response.code() + " " + response.message()));
    }

    public Result<CommentResponse> comment(String commentBody, Long parentCommentId, Long storyId, Long userId) {
        if (parentCommentId == null && storyId == null) {
            throw new IllegalArgumentException("Unable to post comment. Either parent comment or the story need to be present");
        }
        return safeApiCall(
                () -> postComment(commentBody, parentCommentId, storyId, userId),
                "Unable to post comment"
        );
    }

    private Result<CommentResponse> postComment(String commentBody, Long parentCommentId, Long storyId, Long userId) {
        NewCommentRequest request = new NewCommentRequest(
                commentBody,
                parentCommentId != null ? parentCommentId.toString() : null,
                storyId != null ? storyId.toString() : null,
                userId.toString()
        );
        Response<CommentResponse> response;
        try {
            response = service.comment(request).execute();
            if (response.isSuccessful()) {
                CommentResponse body = response.body();
                if (body != null && !body.getComments().isEmpty()) {
                    return new Result.Success<>(body.getComments().get(0));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new Result.Error<>(new IOException("Error posting comment " + response.code() + " " + response.message()));
    }

    private static volatile CommentsRemoteDataSource INSTANCE;

    public static CommentsRemoteDataSource getInstance(DesignerNewsService service) {
        if (INSTANCE == null) {
            synchronized (CommentsRemoteDataSource.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CommentsRemoteDataSource(service);
                }
            }
        }
        return INSTANCE;
    }
}