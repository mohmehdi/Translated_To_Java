package io.plaidapp.core.designernews.domain.model;

import io.plaidapp.core.designernews.data.api.model.Comment;
import io.plaidapp.core.designernews.data.api.model.User;
import java.util.Date;
import java.util.List;

public class CommentWithReplies {

  private long id;
  private Long parentId;
  private String body;
  private Date createdAt;
  private int depth;
  private int upvotesCount;
  private long userId;
  private long storyId;
  private List<CommentWithReplies> replies;

  public CommentWithReplies(
    long id,
    Long parentId,
    String body,
    Date createdAt,
    int depth,
    int upvotesCount,
    long userId,
    long storyId,
    List<CommentWithReplies> replies
  ) {
    this.id = id;
    this.parentId = parentId;
    this.body = body;
    this.createdAt = createdAt;
    this.depth = depth;
    this.upvotesCount = upvotesCount;
    this.userId = userId;
    this.storyId = storyId;
    this.replies = replies;
  }

  public Comment toComment(List<Comment> replies, User user) {
    return new Comment(
      id,
      parentId,
      body,
      createdAt,
      depth,
      upvotesCount,
      replies,
      userId,
      user != null ? user.getDisplayName() : null,
      user != null ? user.getPortraitUrl() : null,
      false
    );
  }
}
