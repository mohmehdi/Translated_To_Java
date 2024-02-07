package io.plaidapp.core.designernews.data.api.model;

import java.util.Objects;

public final class NewStoryRequest {

  private final String title;
  private final String url;
  private final String comment;

  private NewStoryRequest(String title, String url, String comment) {
    this.title = title;
    this.url = url;
    this.comment = comment;
  }

  public static NewStoryRequest createWithUrl(String title, String url) {
    return new NewStoryRequest(title, url, null);
  }

  public static NewStoryRequest createWithComment(
    String title,
    String comment
  ) {
    return new NewStoryRequest(title, null, comment);
  }
}
