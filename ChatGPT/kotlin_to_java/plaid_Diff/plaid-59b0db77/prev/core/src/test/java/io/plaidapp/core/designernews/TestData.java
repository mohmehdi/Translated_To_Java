package io.plaidapp.core.designernews;

import io.plaidapp.core.designernews.data.comments.model.CommentLinksResponse;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.stories.model.StoryLinks;
import io.plaidapp.core.designernews.data.users.model.User;
import okhttp3.MediaType;
import okhttp3.ResponseBody;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

public class TestData {

    public static final long parentId = 1L;

    public static User user = new User(
            111L,
            "Plaicent",
            "van Plaid",
            "Plaicent van Plaid",
            "www"
    );

    public static CommentLinksResponse links = new CommentLinksResponse(
            user.id,
            999L,
            parentId
    );

    public static CommentResponse replyResponse1 = new CommentResponse(
            11L,
            "commenty comment",
            new GregorianCalendar(1988, 1, 1).getTime(),
            links
    );

    public static CommentResponse replyResponse2 = new CommentResponse(
            12L,
            "commenty comment",
            new GregorianCalendar(1908, 2, 8).getTime(),
            links
    );

    public static List<CommentResponse> repliesResponses = new ArrayList<CommentResponse>() {{
        add(replyResponse1);
        add(replyResponse2);
    }};

    public static ResponseBody errorResponseBody = ResponseBody.create(MediaType.parse(""), "Error");

    public static long userId = 123L;

    public static StoryLinks storyLinks = new StoryLinks(
            userId,
            new ArrayList<Integer>() {{
                add(1);
                add(2);
                add(3);
            }},
            new ArrayList<Integer>() {{
                add(11);
                add(22);
                add(33);
            }},
            new ArrayList<Integer>() {{
                add(111);
                add(222);
                add(333);
            }}
    );
}