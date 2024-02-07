package io.plaidapp.core.designernews.data.api.model;

import com.google.gson.annotations.SerializedName;

public class AccessToken {

  @SerializedName("access_token")
  public String accessToken;

  public AccessToken(String accessToken) {
    this.accessToken = accessToken;
  }
}
