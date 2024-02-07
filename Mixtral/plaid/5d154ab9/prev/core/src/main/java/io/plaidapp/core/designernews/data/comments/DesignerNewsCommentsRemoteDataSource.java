package io.plaidapp.core.designernews.data.comments;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.api.model.CommentResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DesignerNewsCommentsRemoteDataSource {

  private final DesignerNewsService service;

  public DesignerNewsCommentsRemoteDataSource(DesignerNewsService service) {
    this.service = service;
  }

  public Result<List<CommentResponse>> getComments(List<Long> ids) {
    StringBuilder requestIds = new StringBuilder();
    for (long id : ids) {
      requestIds.append(id).append(",");
    }
    requestIds.deleteCharAt(requestIds.length() - 1); // remove the last comma

    retrofit2.Response<List<CommentResponse>> response = service
      .getComments(requestIds.toString())
      .execute();
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

  public static DesignerNewsCommentsRemoteDataSource getInstance(
    DesignerNewsService service
  ) {
    if (INSTANCE == null) {
      synchronized (DesignerNewsCommentsRemoteDataSource.class) {
        if (INSTANCE == null) {
          INSTANCE = new DesignerNewsCommentsRemoteDataSource(service);
        }
      }
    }
    return INSTANCE;
  }

  private static volatile DesignerNewsCommentsRemoteDataSource INSTANCE;
}
