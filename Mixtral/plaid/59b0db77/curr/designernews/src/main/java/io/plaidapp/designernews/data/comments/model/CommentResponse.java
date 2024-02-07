import com.google.gson.annotations.SerializedName;
import io.plaidapp.core.designernews.data.login.model.LoggedInUser;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.core.designernews.domain.model.CommentWithReplies;
import java.util.Date;

public class CommentResponse {

  @SerializedName("id")
  public long id;

  @SerializedName("body")
  public String body;

  @SerializedName("created_at")
  public Date created_at;

  @SerializedName("depth")
  public int depth;

  @SerializedName("vote_count")
  public int vote_count;

  @SerializedName("links")
  public CommentLinksResponse links;

  public Comment toCommentWithNoReplies(LoggedInUser user) {
    return new Comment(
      id,
      links.parentComment,
      body,
      created_at,
      depth,
      links.commentUpvotes.size,
      user.id,
      user.displayName,
      user.portraitUrl,
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
