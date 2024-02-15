

package io.plaidapp.designernews.data.comments.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PostCommentResponse {
    @SerializedName("comments")
    public List<CommentResponse> comments;

    public PostCommentResponse(List<CommentResponse> comments) {
        this.comments = comments;
    }
}