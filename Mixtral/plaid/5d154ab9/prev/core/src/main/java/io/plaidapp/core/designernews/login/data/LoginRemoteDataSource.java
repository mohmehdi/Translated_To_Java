package io.plaidapp.core.designernews.login.data;

import io.plaidapp.core.BuildConfig;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsAuthTokenLocalDataSource;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.api.model.User;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Response;

class LoginRemoteDataSource {

  private final DesignerNewsAuthTokenLocalDataSource tokenLocalDataSource;
  private final DesignerNewsService service;

  LoginRemoteDataSource(
    DesignerNewsAuthTokenLocalDataSource tokenLocalDataSource,
    DesignerNewsService service
  ) {
    this.tokenLocalDataSource = tokenLocalDataSource;
    this.service = service;
  }

  void logout() {
    tokenLocalDataSource.authToken = null;
  }

  Result<User> login(String username, String password) {
    Map<String, String> loginParams = buildLoginParams(username, password);
    Response<User> response;
    try {
      response = service.login(loginParams).execute();
    } catch (IOException e) {
      return Result.Error(new IOException("Access token retrieval failed"));
    }
    if (response.isSuccessful()) {
      User body = response.body();
      if (body != null) {
        String token = body.accessToken;
        tokenLocalDataSource.authToken = token;
        return requestUser();
      }
    }
    return Result.Error(
      new IOException(
        "Access token retrieval failed " +
        response.code() +
        " " +
        response.message()
      )
    );
  }

  private Result<User> requestUser() {
    Response<List<User>> response;
    try {
      response = service.getAuthedUser().execute();
    } catch (IOException e) {
      return Result.Error(new IOException("Failed to get authed user"));
    }
    if (response.isSuccessful()) {
      List<User> users = response.body();
      if (users != null && !users.isEmpty()) {
        return Result.Success(users.get(0));
      }
    }
    return Result.Error(
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
    Map<String, String> loginParams = new HashMap<>();
    loginParams.put("client_id", BuildConfig.DESIGNER_NEWS_CLIENT_ID);
    loginParams.put("client_secret", BuildConfig.DESIGNER_NEWS_CLIENT_SECRET);
    loginParams.put("grant_type", "password");
    loginParams.put("username", username);
    loginParams.put("password", password);
    return Collections.unmodifiableMap(loginParams);
  }
}
