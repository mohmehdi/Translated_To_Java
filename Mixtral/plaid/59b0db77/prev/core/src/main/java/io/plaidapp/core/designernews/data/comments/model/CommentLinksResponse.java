

package io.plaidapp.core.designernews.data.comments.model;

import android.os.Parcelable;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import kotlinx.android.parcel.Parcelize;

@Parcelize
public class CommentLinksResponse implements Parcelable {
    @SerializedName("user")
    public long userId;
    
    @SerializedName("story")
    public long story;
    
    @SerializedName("parent_comment")
    public Long parentComment;
    
    @SerializedName("comments")
    public List<Long> comments = new ArrayList<>();
    
    @SerializedName("comment_upvotes")
    public List<String> commentUpvotes = new ArrayList<>();
    
    @SerializedName("comment_downvotes")
    public List<String> commentDownvotes = new ArrayList<>();

    public CommentLinksResponse() {}

    // You need to implement the Parcelable methods: describeContents() and writeToParcel()
    // Please refer to the Kotlin code for the logic of these methods
}