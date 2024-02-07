package io.plaidapp.core.designernews.data.api.model;

import android.os.Parcelable;
import com.google.gson.annotations.SerializedName;
import io.plaidapp.core.data.PlaidItem;
import java.util.Date;
import java.util.Objects;
import kotlinx.android.parcel.Parcelize;

@Parcelize
public class Story implements PlaidItem, Parcelable {

  @SerializedName("id")
  private final long id;

  @SerializedName("title")
  private final String title;

  @SerializedName("url")
  private String url;

  @SerializedName("comment")
  private final String comment;

  @SerializedName("comment_html")
  private final String commentHtml;

  @SerializedName("comment_count")
  private final int commentCount;

  @SerializedName("vote_count")
  private final int voteCount;

  @SerializedName("user_id")
  private final long userId;

  @SerializedName("created_at")
  private final Date createdAt;

  @SerializedName("links")
  private StoryLinks links;

  @Deprecated
  @SerializedName("user_display_name")
  private final String userDisplayName;

  @Deprecated
  @SerializedName("user_portrait_url")
  private final String userPortraitUrl;

  @SerializedName("user_job")
  private final String userJob;

  public Story(
    long id,
    String title,
    String comment,
    String commentHtml,
    int commentCount,
    int voteCount,
    long userId,
    Date createdAt,
    StoryLinks links,
    String userDisplayName,
    String userPortraitUrl,
    String userJob
  ) {
    this.id = id;
    this.title = title;
    this.url = getDefaultUrl(id);
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

  public Story(
    long id,
    String title,
    String url,
    String comment,
    String commentHtml,
    int commentCount,
    int voteCount,
    long userId,
    Date createdAt,
    StoryLinks links
  ) {
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
    this.userDisplayName = null;
    this.userPortraitUrl = null;
    this.userJob = null;
  }

  public static String getDefaultUrl(long id) {
    return "https://designernews.co/stories/" + id;
  }
}
