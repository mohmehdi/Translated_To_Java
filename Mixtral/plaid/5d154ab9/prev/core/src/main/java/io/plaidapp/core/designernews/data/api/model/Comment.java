package io.plaidapp.core.designernews.data.api.model;

import java.util.Date;
import java.util.List;

public class Comment {

  private final Long id;
  private final Long parentCommentId;
  private final String body;
  private final Date createdAt;
  private final int depth;
  private final int upvotesCount;
  private final List<Comment> replies;
  private final Long userId;
  private final String userDisplayName;
  private final String userPortraitUrl;
  private boolean upvoted;

  public Comment(
    Long id,
    Long parentCommentId,
    String body,
    Date createdAt,
    int depth,
    int upvotesCount,
    List<Comment> replies,
    Long userId,
    String userDisplayName,
    String userPortraitUrl,
    boolean upvoted
  ) {
    this.id = id;
    this.parentCommentId = parentCommentId;
    this.body = body;
    this.createdAt = createdAt;
    this.depth = depth;
    this.upvotesCount = upvotesCount;
    this.replies = replies;
    this.userId = userId;
    this.userDisplayName = userDisplayName;
    this.userPortraitUrl = userPortraitUrl;
    this.upvoted = upvoted;
  }
}
