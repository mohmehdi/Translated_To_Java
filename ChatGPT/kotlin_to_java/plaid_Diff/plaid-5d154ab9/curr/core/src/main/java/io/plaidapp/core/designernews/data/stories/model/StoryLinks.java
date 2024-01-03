package io.plaidapp.core.designernews.data.stories.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

public class StoryLinks implements Parcelable {
    private String user;
    private List<Long> comments;
    private List<String> upvotes;
    private List<String> downvotes;

    public StoryLinks(String user, List<Long> comments, List<String> upvotes, List<String> downvotes) {
        this.user = user;
        this.comments = comments;
        this.upvotes = upvotes;
        this.downvotes = downvotes;
    }

    protected StoryLinks(Parcel in) {
        user = in.readString();
        comments = in.createLongArrayList();
        upvotes = in.createStringArrayList();
        downvotes = in.createStringArrayList();
    }

    public static final Creator<StoryLinks> CREATOR = new Creator<StoryLinks>() {
        @Override
        public StoryLinks createFromParcel(Parcel in) {
            return new StoryLinks(in);
        }

        @Override
        public StoryLinks[] newArray(int size) {
            return new StoryLinks[size];
        }
    };

    public String getUser() {
        return user;
    }

    public List<Long> getComments() {
        return comments;
    }

    public List<String> getUpvotes() {
        return upvotes;
    }

    public List<String> getDownvotes() {
        return downvotes;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(user);
        dest.writeList(comments);
        dest.writeList(upvotes);
        dest.writeList(downvotes);
    }
}