package io.plaidapp.core.designernews;

import io.plaidapp.core.designernews.data.stories.model.StoryLinks;
import io.plaidapp.core.designernews.data.users.model.User;
import okhttp3.MediaType;
import okhttp3.ResponseBody;

public class TestData {

  User user = new User(
    111L,
    "Plaicent",
    "van Plaid",
    "Plaicent van Plaid",
    "www"
  );

  ResponseBody errorResponseBody = ResponseBody.create(
    MediaType.parse(""),
    "Error"
  );

  static final long userId = 123L;

  StoryLinks storyLinks = new StoryLinks(
    userId,
    Arrays.asList(1, 2, 3),
    Arrays.asList(11, 22, 33),
    Arrays.asList(111, 222, 333)
  );
}
