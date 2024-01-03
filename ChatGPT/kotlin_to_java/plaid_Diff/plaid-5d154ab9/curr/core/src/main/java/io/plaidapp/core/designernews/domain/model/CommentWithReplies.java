package io.plaidapp.core.designernews.domain.model;

import io.plaidapp.core.designernews.data.users.model.User;
import java.util.Date;
import java.util.List;

public class CommentWithReplies {
    public final long id;
    public final Long parentId;
    public final String body;
    public final Date createdAt;
    public final int depth;
    public final int upvotesCount;
    public final long userId;
    public final long storyId;
    public final List<CommentWithReplies> replies;

    public CommentWithReplies(long id, Long parentId, String body, Date createdAt, int depth, int upvotesCount, long userId, long storyId, List<CommentWithReplies> replies) {
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
                user != null ? user.displayName : null,
                user != null ? user.portraitUrl : null,
                false
        );
    }
}