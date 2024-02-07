package io.plaidapp.core.designernews.data.users.model;

import com.google.gson.annotations.SerializedName;

public class User {

  @SerializedName("id")
  public long id;

  @SerializedName("first_name")
  public String firstName;

  @SerializedName("last_name")
  public String lastName;

  @SerializedName("display_name")
  public String displayName;

  @SerializedName("portrait_url")
  public String portraitUrl;

  public User(
    long id,
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

  public User(long id, String firstName, String lastName, String displayName) {
    this(id, firstName, lastName, displayName, null);
  }
}
