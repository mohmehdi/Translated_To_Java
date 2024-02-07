package io.plaidapp.core.designernews.data.stories.model;

import android.os.Parcelable;
import com.google.gson.annotations.SerializedName;
import io.plaidapp.core.data.PlaidItem;
import java.util.Date;
import kotlinx.android.parcel.Parcelize;

@Parcelize
public class Story extends PlaidItem implements Parcelable {

  @SerializedName("id")
  private final long id;

  @SerializedName("title")
  private final String title;

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
  private final Date createdAt;

  @SerializedName("links")
  private StoryLinks links;

  @SerializedName("user_display_name")
  @Deprecated
  private String userDisplayName;

  @SerializedName("user_portrait_url")
  @Deprecated
  private String userPortraitUrl;

  @SerializedName("user_job")
  private String userJob;

  public Story(long id, String title, Date createdAt) {
    this.id = id;
    this.title = title;
    this.url = getDefaultUrl(id);
    this.createdAt = createdAt;
  }

  public static String getDefaultUrl(long id) {
    return "https://designernews.co/stories/" + id;
  }
  // getters and setters for all fields

  // implement PlaidItem interface

  // implement Parcelable interface
}
