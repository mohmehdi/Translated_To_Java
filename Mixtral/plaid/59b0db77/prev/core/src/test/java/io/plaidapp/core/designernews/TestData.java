package io.plaidapp.core.designernews;

import io.plaidapp.core.designernews.data.comments.model.CommentLinksResponse;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.stories.model.StoryLinks;
import io.plaidapp.core.designernews.data.users.model.User;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import okhttp3.MediaType;
import okhttp3.ResponseBody;

public class TestData {

  public static final Long parentId = 1L;

  public static User user = new User(
    111L,
    "Plaicent",
    "van Plaid",
    "Plaicent van Plaid",
    "www"
  );

  public static CommentLinksResponse links = new CommentLinksResponse(
    user.getId(),
    999L,
    parentId
  );

  public static CommentResponse replyResponse1 = new CommentResponse(
    11L,
    "commenty comment",
    GregorianCalendar(1988, 1, 1).getTime(),
    links
  );

  public static CommentResponse replyResponse2 = new CommentResponse(
    12L,
    "commenty comment",
    GregorianCalendar(1908, 2, 8).getTime(),
    links
  );

  public static List<CommentResponse> repliesResponses = Arrays.asList(
    replyResponse1,
    replyResponse2
  );

  public static ResponseBody errorResponseBody = ResponseBody.create(
    MediaType.parse(""),
    "Error"
  );

  public static Long userId = 123L;

  public static StoryLinks storyLinks = new StoryLinks(
    userId,
    Arrays.asList(1, 2, 3),
    Arrays.asList(11, 22, 33),
    Arrays.asList(111, 222, 333)
  );
}
