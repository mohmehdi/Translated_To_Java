package io.plaidapp.core.designernews.domain.model;

import java.util.Date;
import java.util.List;

public class Comment {

  public long id;
  public Long parentCommentId;
  public String body;
  public Date createdAt;
  public int depth;
  public int upvotesCount;
  public List<Comment> replies;
  public long userId;
  public String userDisplayName;
  public String userPortraitUrl;
  public boolean upvoted;

  public Comment(
    long id,
    Long parentCommentId,
    String body,
    Date createdAt,
    int depth,
    int upvotesCount,
    List<Comment> replies,
    long userId,
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
