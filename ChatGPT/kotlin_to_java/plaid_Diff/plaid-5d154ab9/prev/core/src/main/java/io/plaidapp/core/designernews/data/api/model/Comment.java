package io.plaidapp.core.designernews.data.api.model;

import java.util.Date;
import java.util.List;

public class Comment {
    private long id;
    private Long parentCommentId;
    private String body;
    private Date createdAt;
    private int depth;
    private int upvotesCount;
    private List<Comment> replies;
    private long userId;
    private String userDisplayName;
    private String userPortraitUrl;
    private boolean upvoted;

    public Comment(long id, Long parentCommentId, String body, Date createdAt, int depth, int upvotesCount,
                   List<Comment> replies, long userId, String userDisplayName, String userPortraitUrl, boolean upvoted) {
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

    public long getId() {
        return id;
    }

    public Long getParentCommentId() {
        return parentCommentId;
    }

    public String getBody() {
        return body;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public int getDepth() {
        return depth;
    }

    public int getUpvotesCount() {
        return upvotesCount;
    }

    public List<Comment> getReplies() {
        return replies;
    }

    public long getUserId() {
        return userId;
    }

    public String getUserDisplayName() {
        return userDisplayName;
    }

    public String getUserPortraitUrl() {
        return userPortraitUrl;
    }

    public boolean isUpvoted() {
        return upvoted;
    }
}