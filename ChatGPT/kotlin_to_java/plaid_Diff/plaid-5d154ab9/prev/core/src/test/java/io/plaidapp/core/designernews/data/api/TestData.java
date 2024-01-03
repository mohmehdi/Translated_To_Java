package io.plaidapp.core.designernews.data.api;

import io.plaidapp.core.designernews.data.api.model.Comment;
import io.plaidapp.core.designernews.data.api.model.CommentLinksResponse;
import io.plaidapp.core.designernews.data.api.model.CommentResponse;
import io.plaidapp.core.designernews.data.api.model.User;
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

    public static final CommentLinksResponse links = new CommentLinksResponse(user1.id, 999L, parentId);

    public static final CommentResponse replyResponse1 = new CommentResponse(
            11L,
            "commenty comment",
            new GregorianCalendar(1988, 1, 1).getTime(),
            links
    );

    public static final CommentWithReplies replyWithReplies1 = new CommentWithReplies(
            replyResponse1.id,
            replyResponse1.links.parentComment,
            replyResponse1.body,
            replyResponse1.created_at,
            replyResponse1.links.userId,
            replyResponse1.links.story,
            new ArrayList<>()
    );

    public static final Comment reply1 = new Comment(
            replyResponse1.id,
            parentId,
            replyResponse1.body,
            replyResponse1.created_at,
            replyResponse1.depth,
            replyResponse1.vote_count,
            new ArrayList<>(),
            replyResponse1.links.userId,
            user1.displayName,
            user1.portraitUrl,
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
            replyResponse2.id,
            replyResponse2.links.parentComment,
            replyResponse2.body,
            replyResponse2.created_at,
            replyResponse2.links.userId,
            replyResponse2.links.story,
            new ArrayList<>()
    );

    public static final Comment reply2 = new Comment(
            replyResponse2.id,
            parentId,
            replyResponse2.body,
            replyResponse2.created_at,
            replyResponse2.depth,
            replyResponse2.vote_count,
            new ArrayList<>(),
            replyResponse2.links.userId,
            user1.displayName,
            user1.portraitUrl,
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
            user2.id,
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
            parentCommentResponse.id,
            parentCommentResponse.links.parentComment,
            parentCommentResponse.body,
            parentCommentResponse.created_at,
            parentCommentResponse.links.userId,
            parentCommentResponse.links.story,
            new ArrayList<CommentWithReplies>() {{
                add(replyWithReplies1);
                add(replyWithReplies2);
            }}
    );

    public static final CommentWithReplies parentCommentWithRepliesWithoutReplies = parentCommentWithReplies.copy(new ArrayList<>());

    public static final Comment parentComment = new Comment(
            parentCommentResponse.id,
            null,
            parentCommentResponse.body,
            parentCommentResponse.created_at,
            parentCommentResponse.depth,
            parentCommentResponse.vote_count,
            replies,
            user2.id,
            user2.displayName,
            user2.portraitUrl,
            false
    );

    public static final Comment parentCommentWithoutReplies = parentComment.copy(new ArrayList<>());

    public static final ResponseBody errorResponseBody = ResponseBody.create(MediaType.parse(""), "Error");
}