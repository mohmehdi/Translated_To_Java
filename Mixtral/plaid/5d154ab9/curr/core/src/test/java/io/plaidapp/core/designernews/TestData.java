package io.plaidapp.core.designernews;

import io.plaidapp.core.designernews.data.comments.model.CommentLinksResponse;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.core.designernews.domain.model.CommentWithReplies;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.ResponseBody;

public class TestData {

  public static final Date createdDate = new GregorianCalendar(1997, 12 - 1, 28)
    .getTime();

  public static final User user1 = new User(
    111L,
    "Plaicent",
    "van Plaid",
    "Plaicent van Plaid",
    "www"
  );

  public static final User user2 = new User(
    222L,
    "Plaude",
    "Pladon",
    "Plaude Pladon",
    "www"
  );

  public static final List<User> users = List.of(user1, user2);

  public static final long parentId = 1;

  public static final CommentLinksResponse links = new CommentLinksResponse(
    user1.getId(),
    999L,
    parentId
  );

  public static final CommentResponse replyResponse1 = new CommentResponse(
    11L,
    "commenty comment",
    new GregorianCalendar(1988, 1 - 1, 1).getTime(),
    links
  );

  public static final CommentWithReplies replyWithReplies1 = new CommentWithReplies(
    replyResponse1.getId(),
    replyResponse1.getLinks().getParentComment(),
    replyResponse1.getBody(),
    replyResponse1.getCreated_at(),
    replyResponse1.getLinks().getUserId(),
    replyResponse1.getLinks().getStory(),
    List.of()
  );

  public static final Comment reply1 = new Comment(
    replyResponse1.getId(),
    parentId,
    replyResponse1.getBody(),
    replyResponse1.getCreated_at(),
    replyResponse1.getDepth(),
    replyResponse1.getVote_count(),
    List.of(),
    replyResponse1.getLinks().getUserId(),
    user1.getDisplayName(),
    user1.getPortraitUrl(),
    false
  );

  public static final Comment reply1NoUser = new Comment(
    reply1.getId(),
    reply1.getParentCommentId(),
    reply1.getBody(),
    reply1.getCreatedAt(),
    reply1.getDepth(),
    reply1.getUpvotesCount(),
    List.of(),
    null,
    null,
    null,
    false
  );

  public static final CommentResponse replyResponse2 = new CommentResponse(
    12L,
    "commenty comment",
    new GregorianCalendar(1908, 2 - 1, 8).getTime(),
    links
  );

  public static final CommentWithReplies replyWithReplies2 = new CommentWithReplies(
    replyResponse2.getId(),
    replyResponse2.getLinks().getParentComment(),
    replyResponse2.getBody(),
    replyResponse2.getCreated_at(),
    replyResponse2.getLinks().getUserId(),
    replyResponse2.getLinks().getStory(),
    List.of()
  );

  public static final Comment reply2 = new Comment(
    replyResponse2.getId(),
    parentId,
    replyResponse2.getBody(),
    replyResponse2.getCreated_at(),
    replyResponse2.getDepth(),
    replyResponse2.getVote_count(),
    List.of(),
    replyResponse2.getLinks().getUserId(),
    user1.getDisplayName(),
    user1.getPortraitUrl(),
    false
  );

  public static final List<CommentResponse> repliesResponses = List.of(
    replyResponse1,
    replyResponse2
  );

  public static final List<Comment> replies = List.of(reply1, reply2);

  public static final CommentLinksResponse parentLinks = new CommentLinksResponse(
    user2.getId(),
    987L,
    null,
    new ArrayList<Long>() {
      {
        add(11L);
        add(12L);
      }
    }
  );

  public static final CommentResponse parentCommentResponse = new CommentResponse(
    parentId,
    "commenty comment",
    createdDate,
    parentLinks
  );

  public static final CommentWithReplies parentCommentWithReplies = new CommentWithReplies(
    parentCommentResponse.getId(),
    parentCommentResponse.getLinks().getParentComment(),
    parentCommentResponse.getBody(),
    parentCommentResponse.getCreated_at(),
    parentCommentResponse.getLinks().getUserId(),
    parentCommentResponse.getLinks().getStory(),
    List.of(replyWithReplies1, replyWithReplies2)
  );

  public static final CommentWithReplies parentCommentWithRepliesWithoutReplies = new CommentWithReplies(
    parentCommentWithReplies.getId(),
    parentCommentWithReplies.getParentId(),
    parentCommentWithReplies.getBody(),
    parentCommentWithReplies.getCreatedAt(),
    parentCommentWithReplies.getUserId(),
    parentCommentWithReplies.getStoryId(),
    List.of()
  );

  public static final Comment parentComment = new Comment(
    parentCommentResponse.getId(),
    parentCommentResponse.getLinks().getParentComment(),
    parentCommentResponse.getBody(),
    parentCommentResponse.getCreated_at(),
    parentCommentResponse.getDepth(),
    parentCommentResponse.getVote_count(),
    replies,
    user2.getId(),
    user2.getDisplayName(),
    user2.getPortraitUrl(),
    false
  );

  public static final Comment parentCommentWithoutReplies = new Comment(
    parentComment.getId(),
    parentComment.getParentCommentId(),
    parentComment.getBody(),
    parentComment.getCreatedAt(),
    parentComment.getDepth(),
    parentComment.getUpvotesCount(),
    List.of(),
    user2.getId(),
    user2.getDisplayName(),
    user2.getPortraitUrl(),
    false
  );

  public static final ResponseBody errorResponseBody = ResponseBody.create(
    MediaType.parse(""),
    "Error"
  );
}
