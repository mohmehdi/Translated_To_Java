package io.plaidapp.core.designernews.data.comments;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Response;

public class DesignerNewsCommentsRemoteDataSource {

  private final DesignerNewsService service;
  private static final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

  public DesignerNewsCommentsRemoteDataSource(DesignerNewsService service) {
    this.service = service;
  }

  public Result<List<CommentResponse>> getComments(final List<Long> ids) {
    networkExecutor.execute(() -> {
      final String requestIds = String.join(",", ids);
      try {
        final Response<List<CommentResponse>> response = service
          .getComments(requestIds)
          .execute();
        if (response.isSuccessful()) {
          final List<CommentResponse> body = response.body();
          if (body != null) {
            Result.Success<List<CommentResponse>> successResult = new Result.Success<>(
              body
            );
          }
        } else {
          Result.Error<List<CommentResponse>> errorResult = new Result.Error<>(
            new IOException(
              "Error getting comments " +
              response.code() +
              " " +
              response.message()
            )
          );
        }
      } catch (IOException e) {
        Result.Error<List<CommentResponse>> errorResult = new Result.Error<>(e);
      }
    });

    // You need to implement a way to get the result from the networkExecutor
    // This is just a skeleton and you need to fill in the gaps
    // You can use libraries like RxJava, LiveData, or your own solution
    // to handle the background task and get the result back to the UI thread

    // HINT: You can use a Callback, Listener, or other Java approaches
    // to achieve this

    return null;
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
