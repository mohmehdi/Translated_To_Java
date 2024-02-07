package io.plaidapp.core.designernews.data.api;

import io.plaidapp.core.designernews.data.api.model.Comment;
import io.plaidapp.core.designernews.data.api.model.CommentLinksResponse;
import io.plaidapp.core.designernews.data.api.model.CommentResponse;
import io.plaidapp.core.designernews.data.api.model.User;
import io.plaidapp.core.designernews.domain.model.CommentWithReplies;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

public class TestData {

    public static final Date createdDate = new GregorianCalendar(1997, 11, 28).getTime();

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

    public static final long parentId = 1L;

    public static final CommentLinksResponse links = new CommentLinksResponse(
            user1.getId(),
            999L,
            parentId
    );

    public static final CommentResponse replyResponse1 = new CommentResponse(
            11L,
            "commenty comment",
            new GregorianCalendar(1988, 0, 1).getTime(),
            links
    );

    public static final CommentWithReplies replyWithReplies1 = new CommentWithReplies(
            replyResponse1.getId(),
            replyResponse1.getLinks().getParentComment(),
            replyResponse1.getBody(),
            replyResponse1.getCreatedAt(),
            replyResponse1.getLinks().getUserId(),
            replyResponse1.getLinks().getStory(),
            new ArrayList<>()
    );

    public static final Comment reply1 = new Comment(
            replyResponse1.getId(),
            parentId,
            replyResponse1.getBody(),
            replyResponse1.getCreatedAt(),
            replyResponse1.getDepth(),
            replyResponse1.getVoteCount(),
            new ArrayList<>(),
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
            new ArrayList<>(),
            null,
            null,
            null,
            false
    );

    public static final CommentResponse replyResponse2 = new CommentResponse(
            12L,
            "commenty comment",
            new GregorianCalendar(1908, 1, 8).getTime(),
            links
    );

    public static final CommentWithReplies replyWithReplies2 = new CommentWithReplies(
            replyResponse2.getId(),
            replyResponse2.getLinks().getParentComment(),
            replyResponse2.getBody(),
            replyResponse2.getCreatedAt(),
            replyResponse2.getLinks().getUserId(),
            replyResponse2.getLinks().getStory(),
            new ArrayList<>()
    );

    public static final Comment reply2 = new Comment(
            replyResponse2.getId(),
            parentId,
            replyResponse2.getBody(),
            replyResponse2.getCreatedAt(),
            replyResponse2.getDepth(),
            replyResponse2.getVoteCount(),
            new ArrayList<>(),
            replyResponse2.getLinks().getUserId(),
            user1.getDisplayName(),
            user1.getPortraitUrl(),
            false
    );

    public static final List<CommentResponse> repliesResponses = List.of(replyResponse1, replyResponse2);
    public static final List<Comment> replies = List.of(reply1, reply2);

    public static final CommentLinksResponse parentLinks = new CommentLinksResponse(
            user2.getId(),
            987L,
            null,
            new ArrayList<>(List.of(11L, 12L))
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
            parentCommentResponse.getCreatedAt(),
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
            new ArrayList<>()
    );

    public static final Comment parentComment = new Comment(
            parentCommentResponse.getId(),
            null,
            parentCommentResponse.getBody(),
            parentCommentResponse.getCreatedAt(),
            parentCommentResponse.getDepth(),
            parentCommentResponse.getVoteCount(),
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
            new ArrayList<>(),
            parentComment.getUserId(),
            parentComment.getUserDisplayName(),
            parentComment.getUserPortraitUrl(),
            false
    );

    public static final ResponseBody errorResponseBody = ResponseBody.create(
            MediaType.parse(""),
            ""
    );
}