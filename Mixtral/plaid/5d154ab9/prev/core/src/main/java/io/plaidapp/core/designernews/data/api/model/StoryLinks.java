package io.plaidapp.core.designernews.data.api.model;

import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.parcelable.Parcelables;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("WeakerAccess")
@Parcelizes
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
    this.comments = comments != null ? comments : new ArrayList<>();
    this.upvotes = upvotes != null ? upvotes : new ArrayList<>();
    this.downvotes = downvotes != null ? downvotes : new ArrayList<>();
  }
}
