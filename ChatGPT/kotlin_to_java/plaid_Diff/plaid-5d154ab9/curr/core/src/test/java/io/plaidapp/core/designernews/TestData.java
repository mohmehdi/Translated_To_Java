package io.plaidapp.core.designernews;

import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.core.designernews.data.comments.model.CommentLinksResponse;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.core.designernews.domain.model.CommentWithReplies;
import okhttp3.MediaType;
import okhttp3.ResponseBody;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class TestData {

    public static final Date createdDate = new GregorianCalendar(1997, 12, 28).getTime();

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

    public static final List<User> users = new ArrayList<User>() {{
        add(user1);
        add(user2);
    }};

    public static final long parentId = 1L;

    public static final CommentLinksResponse links = new CommentLinksResponse(
            user1.getId(),
            999L,
            parentId
    );

    public static final CommentResponse replyResponse1 = new CommentResponse(
            11L,
            "commenty comment",
            new GregorianCalendar(1988, 1, 1).getTime(),
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

    public static final Comment reply1NoUser = reply1.copy(null, null);

    public static final CommentResponse replyResponse2 = new CommentResponse(
            12L,
            "commenty comment",
            new GregorianCalendar(1908, 2, 8).getTime(),
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

    public static final List<CommentResponse> repliesResponses = new ArrayList<CommentResponse>() {{
        add(replyResponse1);
        add(replyResponse2);
    }};

    public static final List<Comment> replies = new ArrayList<Comment>() {{
        add(reply1);
        add(reply2);
    }};

    public static final CommentLinksResponse parentLinks = new CommentLinksResponse(
            user2.getId(),
            987L,
            null,
            new ArrayList<Long>() {{
                add(11L);
                add(12L);
            }}
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
            new ArrayList<CommentWithReplies>() {{
                add(replyWithReplies1);
                add(replyWithReplies2);
            }}
    );

    public static final CommentWithReplies parentCommentWithRepliesWithoutReplies = parentCommentWithReplies.copy(new ArrayList<>());

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

    public static final Comment parentCommentWithoutReplies = parentComment.copy(new ArrayList<>());

    public static final ResponseBody errorResponseBody = ResponseBody.create(MediaType.parse(""), "Error");
}