package io.plaidapp.designernews.data.comments.model;

import com.google.gson.annotations.SerializedName;

public class NewCommentRequest {
    @SerializedName("comments")
    private final PostCommentRequest comment;

    public NewCommentRequest(String body, String parent_comment, String story, String user) {
        this.comment = new PostCommentRequest(
                new CommentData(body),
                new CommentLinks(parent_comment, story, user)
        );
    }

    public PostCommentRequest getComment() {
        return comment;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;

        NewCommentRequest that = (NewCommentRequest) other;

        return comment.equals(that.comment);
    }

    @Override
    public int hashCode() {
        return comment.hashCode();
    }
}

class PostCommentRequest {
    @SerializedName("comment")
    private final CommentData commentData;
    @SerializedName("links")
    private final CommentLinks commentLinks;

    public PostCommentRequest(CommentData commentData, CommentLinks commentLinks) {
        this.commentData = commentData;
        this.commentLinks = commentLinks;
    }

    public CommentData getCommentData() {
        return commentData;
    }

    public CommentLinks getCommentLinks() {
        return commentLinks;
    }
}

class CommentData {
    @SerializedName("body")
    private final String body;

    public CommentData(String body) {
        this.body = body;
    }

    public String getBody() {
        return body;
    }
}

class CommentLinks {
    @SerializedName("parent_comment")
    private final String parent_comment;
    @SerializedName("story")
    private final String story;
    @SerializedName("user")
    private final String user;

    public CommentLinks(String parent_comment, String story, String user) {
        this.parent_comment = parent_comment;
        this.story = story;
        this.user = user;
    }

    public String getParent_comment() {
        return parent_comment;
    }

    public String getStory() {
        return story;
    }

    public String getUser() {
        return user;
    }
}