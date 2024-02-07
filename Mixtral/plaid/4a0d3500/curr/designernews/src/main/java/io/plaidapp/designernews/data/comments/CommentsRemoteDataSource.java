package io.plaidapp.designernews.data.comments;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.comments.model.NewCommentRequest;
import io.plaidapp.core.util.SafeApiCall;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Response;

public class CommentsRemoteDataSource {

  private final DesignerNewsService service;

  public CommentsRemoteDataSource(DesignerNewsService service) {
    this.service = service;
  }

  public Result<List<CommentResponse>> getComments(List<Long> ids) {
    return SafeApiCall.execute(
      () -> requestGetComments(ids),
      errorMessage -> "Error getting comments"
    );
  }

  private Result<List<CommentResponse>> requestGetComments(List<Long> ids) {
    StringBuilder requestIds = new StringBuilder();
    for (long id : ids) {
      requestIds.append(id).append(",");
    }
    requestIds.deleteCharAt(requestIds.length() - 1); // remove the last comma

    Response<List<CommentResponse>> response = service
      .getComments(requestIds.toString())
      .await();
    if (response.isSuccessful()) {
      List<CommentResponse> body = response.body();
      if (body != null) {
        return new Result.Success<>(body);
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

    return SafeApiCall.execute(
      () -> postComment(commentBody, parentCommentId, storyId, userId),
      errorMessage -> "Unable to post comment"
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
    Response<NewCommentRequest.Response> response = service
      .comment(request)
      .await();
    if (response.isSuccessful()) {
      List<CommentResponse> body = response.body().getComments();
      if (!body.isEmpty()) {
        return new Result.Success<>(body.get(0));
      }
    }
    return new Result.Error<>(
      new IOException(
        "Error posting comment " + response.code() + " " + response.message()
      )
    );
  }

  private static class SafeApiCall<T> {

    public static <T> Result<T> execute(
      Callable<Result<T>> callable,
      Function<String, String> errorMessageSupplier
    ) {
      try {
        return callable.call();
      } catch (Exception e) {
        return new Result.Error<>(
          new IOException(errorMessageSupplier.apply(e.getMessage()))
        );
      }
    }
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
