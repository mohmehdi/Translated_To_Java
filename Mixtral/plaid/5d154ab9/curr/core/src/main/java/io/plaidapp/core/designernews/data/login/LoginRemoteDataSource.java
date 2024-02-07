package io.plaidapp.core.designernews.data.login;

import io.plaidapp.core.BuildConfig;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.users.model.User;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LoginRemoteDataSource {

  private final DesignerNewsAuthTokenLocalDataSource tokenLocalDataSource;
  private final DesignerNewsService service;

  public LoginRemoteDataSource(
    DesignerNewsAuthTokenLocalDataSource tokenLocalDataSource,
    DesignerNewsService service
  ) {
    this.tokenLocalDataSource = tokenLocalDataSource;
    this.service = service;
  }

  public void logout() {
    tokenLocalDataSource.authToken = null;
  }

  public Result<User> login(String username, String password) {
    try {
      retrofit2.Response<LoginResponseBody> response = service
        .login(buildLoginParams(username, password))
        .execute();
      if (response.isSuccessful()) {
        LoginResponseBody body = response.body();
        if (body != null) {
          String token = body.accessToken;
          tokenLocalDataSource.authToken = token;
          return requestUser();
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return new Result.Error<>(
      new IOException(
        "Access token retrieval failed " +
        response.code() +
        " " +
        response.message()
      )
    );
  }

  private Result<User> requestUser() {
    try {
      retrofit2.Response<UsersResponseBody> response = service
        .getAuthedUser()
        .execute();
      if (response.isSuccessful()) {
        UsersResponseBody users = response.body();
        if (users != null && !users.isEmpty()) {
          return new Result.Success<>(users.get(0));
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return new Result.Error<>(
      new IOException(
        "Failed to get authed user " +
        response.code() +
        " " +
        response.message()
      )
    );
  }

  private Map<String, String> buildLoginParams(
    String username,
    String password
  ) {
    Map<String, String> params = new HashMap<>();
    params.put("client_id", BuildConfig.DESIGNER_NEWS_CLIENT_ID);
    params.put("client_secret", BuildConfig.DESIGNER_NEWS_CLIENT_SECRET);
    params.put("grant_type", "password");
    params.put("username", username);
    params.put("password", password);
    return params;
  }
}
