package io.plaidapp.core.designernews.data.stories.model;

import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import kotlinx.android.parcel.Parcelize;

@Parcelize
public class StoryLinks implements Parcelable {

  @NonNull
  public final String user;

  @NonNull
  public final List<Long> comments;

  @NonNull
  public final List<String> upvotes;

  @NonNull
  public final List<String> downvotes;

  public StoryLinks(
    @NonNull String user,
    @NonNull List<Long> comments,
    @NonNull List<String> upvotes,
    @NonNull List<String> downvotes
  ) {
    this.user = user;
    this.comments = comments;
    this.upvotes = upvotes;
    this.downvotes = downvotes;
  }
}
