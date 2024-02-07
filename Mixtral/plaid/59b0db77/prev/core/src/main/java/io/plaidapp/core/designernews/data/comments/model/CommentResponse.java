package io.plaidapp.designernews.data.comments.model;

import com.google.gson.annotations.SerializedName;
import io.plaidapp.core.designernews.data.login.model.LoggedInUser;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.core.designernews.domain.model.CommentWithReplies;
import java.util.Date;
import java.util.List;

public class CommentResponse {

  @SerializedName("id")
  private Long id;

  @SerializedName("body")
  private String body;

  @SerializedName("created_at")
  private Date created_at;

  @SerializedName("depth")
  private int depth;

  @SerializedName("vote_count")
  private int vote_count;

  @SerializedName("links")
  private CommentLinksResponse links;

  public Comment toCommentWithNoReplies(LoggedInUser user) {
    return new Comment(
      id,
      links.parentComment,
      body,
      created_at,
      depth,
      links.commentUpvotes.size,
      user.getId(),
      user.getDisplayName(),
      user.getPortraitUrl(),
      false
    );
  }

  public CommentWithReplies toCommentsWithReplies(
    List<CommentWithReplies> replies
  ) {
    return new CommentWithReplies(
      id,
      links.parentComment,
      body,
      created_at,
      depth,
      links.commentUpvotes.size,
      links.userId,
      links.story,
      replies
    );
  }
}
