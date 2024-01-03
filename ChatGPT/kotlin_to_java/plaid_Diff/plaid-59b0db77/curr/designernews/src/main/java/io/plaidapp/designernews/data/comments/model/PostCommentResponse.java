package io.plaidapp.designernews.data.comments.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PostCommentResponse {
    @SerializedName("comments")
    private List<CommentResponse> comments;

    public PostCommentResponse(List<CommentResponse> comments) {
        this.comments = comments;
    }

    public List<CommentResponse> getComments() {
        return comments;
    }
}