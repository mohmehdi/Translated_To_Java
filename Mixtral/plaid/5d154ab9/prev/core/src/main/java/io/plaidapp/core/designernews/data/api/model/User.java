package io.plaidapp.core.designernews.data.api.model;

import com.google.gson.annotations.SerializedName;

public class User {

  @SerializedName("id")
  private final Long id;

  @SerializedName("first_name")
  private final String firstName;

  @SerializedName("last_name")
  private final String lastName;

  @SerializedName("display_name")
  private final String displayName;

  @SerializedName("portrait_url")
  private final String portraitUrl;

  public User(
    Long id,
    String firstName,
    String lastName,
    String displayName,
    String portraitUrl
  ) {
    this.id = id;
    this.firstName = firstName;
    this.lastName = lastName;
    this.displayName = displayName;
    this.portraitUrl = portraitUrl;
  }
}
