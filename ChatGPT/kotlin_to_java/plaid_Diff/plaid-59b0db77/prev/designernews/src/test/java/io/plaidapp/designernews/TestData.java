package io.plaidapp.designernews;

import io.plaidapp.core.designernews.data.comments.model.CommentLinksResponse;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.stories.model.StoryLinks;
import io.plaidapp.core.designernews.data.login.model.LoggedInUser;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.core.designernews.domain.model.CommentWithReplies;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

public class TestData {

    public static final Date createdDate = new GregorianCalendar(1997, 12, 28).getTime();

    public static final LoggedInUser loggedInUser = new LoggedInUser(
            111L,
            "Plaicent",
            "van Plaid",
            "Plaicent van Plaid",
            "www",
            List.of(123L, 234L, 345L)
    );

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
            replyResponse2.getLinks().getUserId(),
            user1.getDisplayName(),
            user1.getPortraitUrl(),
            false
    );

    public static final List<CommentResponse> repliesResponses = List.of(
            replyResponse1,
            replyResponse2
    );

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
            List.of(
                    replyWithReplies1,
                    replyWithReplies2
            )
    );

    public static final CommentWithReplies parentCommentWithRepliesWithoutReplies = parentCommentWithReplies.copy(
            new ArrayList<>()
    );

    public static final Comment parentComment = new Comment(
            parentCommentResponse.getId(),
            null,
            parentCommentResponse.getBody(),
            parentCommentResponse.getCreatedAt(),
            parentCommentResponse.getDepth(),
            parentCommentResponse.getVoteCount(),
            user2.getId(),
            user2.getDisplayName(),
            user2.getPortraitUrl(),
            false
    );

    public static final List<Comment> flattendCommentsWithReplies = List.of(parentComment, reply1, reply2);

    public static final List<Comment> flattenedCommentsWithoutReplies = List.of(parentComment);

    public static final ResponseBody errorResponseBody = ResponseBody.create(MediaType.parse(""), "Error");

    public static final StoryLinks storyLinks = new StoryLinks(
            123L,
            List.of(1, 2, 3),
            List.of(11, 22, 33),
            List.of(111, 222, 333)
    );
}