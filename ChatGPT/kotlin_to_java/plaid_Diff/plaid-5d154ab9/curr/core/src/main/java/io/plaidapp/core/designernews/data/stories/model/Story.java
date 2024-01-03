package io.plaidapp.core.designernews.data.stories.model;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.gson.annotations.SerializedName;
import io.plaidapp.core.data.PlaidItem;
import java.util.Date;

public class Story extends PlaidItem implements Parcelable {
    @SerializedName("id")
    private long id;
    @SerializedName("title")
    private String title;
    @SerializedName("url")
    private String url;
    @SerializedName("comment")
    private String comment;
    @SerializedName("comment_html")
    private String commentHtml;
    @SerializedName("comment_count")
    private int commentCount;
    @SerializedName("vote_count")
    private int voteCount;
    @SerializedName("user_id")
    private long userId;
    @SerializedName("created_at")
    private Date createdAt;
    @SerializedName("links")
    private StoryLinks links;
    @Deprecated("Removed in DN API V2")
    @SerializedName("user_display_name")
    private String userDisplayName;
    @Deprecated("Removed in DN API V2")
    @SerializedName("user_portrait_url")
    private String userPortraitUrl;
    @SerializedName("user_job")
    private String userJob;

    public Story(long id, String title, String url, String comment, String commentHtml, int commentCount, int voteCount, long userId, Date createdAt, StoryLinks links, String userDisplayName, String userPortraitUrl, String userJob) {
        super(id, title, url);
        this.id = id;
        this.title = title;
        this.url = url;
        this.comment = comment;
        this.commentHtml = commentHtml;
        this.commentCount = commentCount;
        this.voteCount = voteCount;
        this.userId = userId;
        this.createdAt = createdAt;
        this.links = links;
        this.userDisplayName = userDisplayName;
        this.userPortraitUrl = userPortraitUrl;
        this.userJob = userJob;
    }

    protected Story(Parcel in) {
        super(in);
        id = in.readLong();
        title = in.readString();
        url = in.readString();
        comment = in.readString();
        commentHtml = in.readString();
        commentCount = in.readInt();
        voteCount = in.readInt();
        userId = in.readLong();
        createdAt = new Date(in.readLong());
        links = in.readParcelable(StoryLinks.class.getClassLoader());
        userDisplayName = in.readString();
        userPortraitUrl = in.readString();
        userJob = in.readString();
    }

    public static final Creator<Story> CREATOR = new Creator<Story>() {
        @Override
        public Story createFromParcel(Parcel in) {
            return new Story(in);
        }

        @Override
        public Story[] newArray(int size) {
            return new Story[size];
        }
    };

    public static String getDefaultUrl(long id) {
        return "https://";
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getComment() {
        return comment;
    }

    public String getCommentHtml() {
        return commentHtml;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public int getVoteCount() {
        return voteCount;
    }

    public long getUserId() {
        return userId;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public StoryLinks getLinks() {
        return links;
    }

    @Deprecated("Removed in DN API V2")
    public String getUserDisplayName() {
        return userDisplayName;
    }

    @Deprecated("Removed in DN API V2")
    public String getUserPortraitUrl() {
        return userPortraitUrl;
    }

    public String getUserJob() {
        return userJob;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeString(url);
        dest.writeString(comment);
        dest.writeString(commentHtml);
        dest.writeInt(commentCount);
        dest.writeInt(voteCount);
        dest.writeLong(userId);
        dest.writeLong(createdAt.getTime());
        dest.writeParcelable(links, flags);
        dest.writeString(userDisplayName);
        dest.writeString(userPortraitUrl);
        dest.writeString(userJob);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}