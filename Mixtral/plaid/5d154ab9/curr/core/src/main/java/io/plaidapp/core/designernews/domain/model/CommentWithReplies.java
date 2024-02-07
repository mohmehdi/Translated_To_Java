package io.plaidapp.core.designernews.domain.model;

import io.plaidapp.core.designernews.data.users.model.User;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class CommentWithReplies {

  private final Long id;
  private final Long parentId;
  private final String body;
  private final Date createdAt;
  private final int depth;
  private final int upvotesCount;
  private final Long userId;
  private final Long storyId;
  private final List<CommentWithReplies> replies;

  public CommentWithReplies(
    Long id,
    Long parentId,
    String body,
    Date createdAt,
    int depth,
    int upvotesCount,
    Long userId,
    Long storyId,
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
    this.replies = (replies == null) ? Collections.emptyList() : replies;
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
