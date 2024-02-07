package io.plaidapp.designernews.data.comments.model;

import com.google.gson.annotations.SerializedName;

public class NewCommentRequest {

  private final String body;
  private final String parent_comment;
  private final String story;
  private final String user;

  @SerializedName("comments")
  private final PostCommentRequest comment;

  public NewCommentRequest(
    String body,
    String parent_comment,
    String story,
    String user
  ) {
    this.body = body;
    this.parent_comment = parent_comment;
    this.story = story;
    this.user = user;
    this.comment =
      new PostCommentRequest(
        new CommentData(body),
        new CommentLinks(parent_comment, story, user)
      );
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof NewCommentRequest)) return false;

    NewCommentRequest otherRequest = (NewCommentRequest) other;

    return comment.equals(otherRequest.comment);
  }

  @Override
  public int hashCode() {
    return comment.hashCode();
  }

  public static class PostCommentRequest {

    @SerializedName("comment")
    private final CommentData commentData;

    @SerializedName("links")
    private final CommentLinks commentLinks;

    public PostCommentRequest(
      CommentData commentData,
      CommentLinks commentLinks
    ) {
      this.commentData = commentData;
      this.commentLinks = commentLinks;
    }
  }

  public static class CommentData {

    @SerializedName("body")
    private final String body;

    public CommentData(String body) {
      this.body = body;
    }
  }

  public static class CommentLinks {

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
  }
}
