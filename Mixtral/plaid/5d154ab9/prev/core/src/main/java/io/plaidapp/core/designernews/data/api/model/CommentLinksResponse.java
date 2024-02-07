package io.plaidapp.designernews.data.comments.model;

import android.os.Parcelable;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import kotlin.jvm.JvmField;
import kotlinx.android.parcel.Parcelize;

@Parcelize
public final class CommentLinksResponse implements Parcelable {

  @SerializedName("user")
  @JvmField
  public final long userId;

  @SerializedName("story")
  @JvmField
  public final long story;

  @SerializedName("parent_comment")
  @JvmField
  public final Long parentComment;

  @SerializedName("comments")
  @JvmField
  public final List comments;

  @SerializedName("comment_upvotes")
  @JvmField
  public final List commentUpvotes;

  @SerializedName("comment_downvotes")
  @JvmField
  public final List commentDownvotes;

  public CommentLinksResponse(
    long userId,
    long story,
    Long parentComment,
    List comments,
    List commentUpvotes,
    List commentDownvotes
  ) {
    this.userId = userId;
    this.story = story;
    this.parentComment = parentComment;
    this.comments = comments != null ? comments : new ArrayList();
    this.commentUpvotes =
      commentUpvotes != null ? commentUpvotes : new ArrayList();
    this.commentDownvotes =
      commentDownvotes != null ? commentDownvotes : new ArrayList();
  }
}
