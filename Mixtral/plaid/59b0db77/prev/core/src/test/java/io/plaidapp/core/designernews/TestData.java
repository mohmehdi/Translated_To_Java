
package io.plaidapp.core.designernews;

import io.plaidapp.core.designernews.data.comments.model.CommentLinksResponse;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.stories.model.StoryLinks;
import io.plaidapp.core.designernews.data.users.model.User;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import java.util.GregorianCalendar;
import java.util.Arrays;
import java.util.List;

public class TestData {
    public static final long parentId = 1L;

    public static final User user = new User(
            111L,
            "Plaicent",
            "van Plaid",
            "Plaicent van Plaid",
            "www"
    );

    public static final CommentLinksResponse links = new CommentLinksResponse(
            user.getId(),
            999L,
            parentId
    );

    public static final CommentResponse replyResponse1 = new CommentResponse(
            11L,
            "commenty comment",
            new GregorianCalendar(1988, 1, 1).getTime(),
            links
    );

    public static final CommentResponse replyResponse2 = new CommentResponse(
            12L,
            "commenty comment",
            new GregorianCalendar(1908, 2, 8).getTime(),
            links
    );

    public static final List<CommentResponse> repliesResponses = Arrays.asList(
            replyResponse1,
            replyResponse2
    );

    public static final ResponseBody errorResponseBody = ResponseBody.create(
            MediaType.parse(""),
            "Error"
    );

    public static final long userId = 123L;

    public static final StoryLinks storyLinks = new StoryLinks(
            userId,
            Arrays.asList(1, 2, 3),
            Arrays.asList(11, 22, 33),
            Arrays.asList(111, 222, 333)
    );
}