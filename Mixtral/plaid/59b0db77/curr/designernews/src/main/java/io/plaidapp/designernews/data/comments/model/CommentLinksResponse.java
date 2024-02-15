

package io.plaidapp.designernews.data.comments.model;

import android.os.Parcelable;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import kotlinx.android.parcel.Parcelize;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@Parcelize
public class CommentLinksResponse implements Parcelable {
    @SerializedName("user")
    public long userId;

    @SerializedName("story")
    public long story;

    @SerializedName("parent_comment")
    @Nullable
    public Long parentComment;

    @SerializedName("comments")
    @NonNull
    public List<Long> comments;

    @SerializedName("comment_upvotes")
    @NonNull
    public List<String> commentUpvotes;

    @SerializedName("comment_downvotes")
    @NonNull
    public List<String> commentDownvotes;

    public CommentLinksResponse() {
        this.comments = Collections.emptyList();
        this.commentUpvotes = Collections.emptyList();
        this.commentDownvotes = Collections.emptyList();
    }
}