package io.plaidapp.core.designernews.data.comments;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.api.DesignerNewsService;
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
    return SafeApiCall.call(
      () -> requestGetComments(ids),
      errorMessage -> new Result.Error<>(new IOException(errorMessage))
    );
  }

  private Result<List<CommentResponse>> requestGetComments(List<Long> ids) {
    StringBuilder requestIds = new StringBuilder();
    for (long id : ids) {
      requestIds.append(id).append(",");
    }
    requestIds.deleteCharAt(requestIds.length() - 1); // remove last comma

    retrofit2.Response<CommentResponseList> response = service
      .getComments(requestIds.toString())
      .execute();
    if (response.isSuccessful()) {
      CommentResponseList body = response.body();
      if (body != null) {
        return new Result.Success<>(body.getComments());
      }
    }
    return new Result.Error<>(
      new IOException(
        "Error getting comments " + response.code() + " " + response.message()
      )
    );
  }

  public Result<CommentResponse> comment(
    String commentBody,
    Long parentCommentId,
    Long storyId,
    Long userId
  ) {
    check(parentCommentId != null || storyId != null);

    return SafeApiCall.call(
      () -> postComment(commentBody, parentCommentId, storyId, userId),
      errorMessage -> new Result.Error<>(new IOException(errorMessage))
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

    retrofit2.Response<CommentResponseList> response = service
      .comment(request)
      .execute();
    if (response.isSuccessful()) {
      CommentResponseList body = response.body();
      if (body != null && !body.getComments().isEmpty()) {
        return new Result.Success<>(body.getComments().get(0));
      }
    }
    return new Result.Error<>(
      new IOException(
        "Error posting comment " + response.code() + " " + response.message()
      )
    );
  }

  public static CommentsRemoteDataSource getInstance(
    DesignerNewsService service
  ) {
    CommentsRemoteDataSource INSTANCE = INSTANCE_;
    if (INSTANCE == null) {
      synchronized (CommentsRemoteDataSource.class) {
        INSTANCE = INSTANCE_ = new CommentsRemoteDataSource(service);
      }
    }
    return INSTANCE;
  }

  private static volatile CommentsRemoteDataSource INSTANCE_;
}
