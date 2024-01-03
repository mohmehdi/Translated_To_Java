package io.plaidapp.core.designernews.data.api.model;

import android.os.Parcelable;
import com.google.gson.annotations.SerializedName;
import kotlinx.android.parcel.Parcelize;

import java.util.List;

@Parcelize
public class CommentLinksResponse implements Parcelable {
    @SerializedName("user")
    public long userId;
    @SerializedName("story")
    public long story;
    @SerializedName("parent_comment")
    public Long parentComment;
    @SerializedName("comments")
    public List<Long> comments;
    @SerializedName("comment_upvotes")
    public List<String> commentUpvotes;
    @SerializedName("comment_downvotes")
    public List<String> commentDownvotes;
}