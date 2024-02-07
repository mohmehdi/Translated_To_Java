package io.plaidapp.core.designernews.data.comments;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.remote.PlaidRemoteDataSource;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import java.util.List;

public class CommentsRepository {

  private final CommentsRemoteDataSource remoteDataSource;

  public CommentsRepository(CommentsRemoteDataSource remoteDataSource) {
    this.remoteDataSource = remoteDataSource;
  }

  public Result<List<CommentResponse>> getComments(List<Long> ids) {
    return remoteDataSource.getComments(ids);
  }

  public Result<CommentResponse> postStoryComment(
    String body,
    Long storyId,
    Long userId
  ) {
    return remoteDataSource.comment(body, null, storyId, userId);
  }

  public Result<CommentResponse> postReply(
    String body,
    Long parentCommentId,
    Long userId
  ) {
    return remoteDataSource.comment(body, parentCommentId, null, userId);
  }

  public static class SingletonHolder {

    private static final CommentsRepository INSTANCE = new CommentsRepository(
      null
    );
  }

  public static CommentsRepository getInstance(
    CommentsRemoteDataSource remoteDataSource
  ) {
    if (SingletonHolder.INSTANCE.remoteDataSource == null) {
      SingletonHolder.INSTANCE.remoteDataSource = remoteDataSource;
    }
    return SingletonHolder.INSTANCE;
  }
}
