package io.plaidapp.core.designernews.data.comments.model;

import com.google.gson.annotations.SerializedName;

public class NewCommentRequest {
    @SerializedName("comments")
    private PostCommentRequest comment;

    public NewCommentRequest(String body, String parent_comment, String story, String user) {
        this.comment = new PostCommentRequest(new CommentData(body), new CommentLinks(parent_comment, story, user));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (getClass() != other.getClass()) return false;

        NewCommentRequest newCommentRequest = (NewCommentRequest) other;

        return comment.equals(newCommentRequest.comment);
    }

    @Override
    public int hashCode() {
        return comment.hashCode();
    }

    private static class PostCommentRequest {
        @SerializedName("comment")
        private CommentData commentData;
        @SerializedName("links")
        private CommentLinks commentLinks;

        public PostCommentRequest(CommentData commentData, CommentLinks commentLinks) {
            this.commentData = commentData;
            this.commentLinks = commentLinks;
        }
    }

    private static class CommentData {
        @SerializedName("body")
        private String body;

        public CommentData(String body) {
            this.body = body;
        }
    }

    private static class CommentLinks {
        @SerializedName("parent_comment")
        private String parent_comment;
        @SerializedName("story")
        private String story;
        @SerializedName("user")
        private String user;

        public CommentLinks(String parent_comment, String story, String user) {
            this.parent_comment = parent_comment;
            this.story = story;
            this.user = user;
        }
    }
}