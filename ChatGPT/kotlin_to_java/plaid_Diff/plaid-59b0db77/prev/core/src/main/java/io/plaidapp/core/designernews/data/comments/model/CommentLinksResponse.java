package io.plaidapp.core.designernews.data.comments.model;

import android.os.Parcelable;
import com.google.gson.annotations.SerializedName;
import kotlinx.android.parcel.Parcelize;

import java.util.List;

@Parcelize
public class CommentLinksResponse implements Parcelable {
    @SerializedName("user")
    private long userId;
    @SerializedName("story")
    private long story;
    @SerializedName("parent_comment")
    private Long parentComment;
    @SerializedName("comments")
    private List<Long> comments;
    @SerializedName("comment_upvotes")
    private List<String> commentUpvotes;
    @SerializedName("comment_downvotes")
    private List<String> commentDownvotes;

    public CommentLinksResponse(long userId, long story, Long parentComment, List<Long> comments,
                                List<String> commentUpvotes, List<String> commentDownvotes) {
        this.userId = userId;
        this.story = story;
        this.parentComment = parentComment;
        this.comments = comments;
        this.commentUpvotes = commentUpvotes;
        this.commentDownvotes = commentDownvotes;
    }

    public long getUserId() {
        return userId;
    }

    public long getStory() {
        return story;
    }

    public Long getParentComment() {
        return parentComment;
    }

    public List<Long> getComments() {
        return comments;
    }

    public List<String> getCommentUpvotes() {
        return commentUpvotes;
    }

    public List<String> getCommentDownvotes() {
        return commentDownvotes;
    }
}