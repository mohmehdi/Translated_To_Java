package io.plaidapp.core.designernews.login.data;

import io.plaidapp.core.BuildConfig;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsAuthTokenLocalDataSource;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.api.model.User;
import java.io.IOException;
import java.util.Map;

public class LoginRemoteDataSource {

    private DesignerNewsAuthTokenLocalDataSource tokenLocalDataSource;
    private DesignerNewsService service;

    public LoginRemoteDataSource(DesignerNewsAuthTokenLocalDataSource tokenLocalDataSource,
                                 DesignerNewsService service) {
        this.tokenLocalDataSource = tokenLocalDataSource;
        this.service = service;
    }

    public void logout() {
        tokenLocalDataSource.setAuthToken(null);
    }

    public Result<User> login(String username, String password) {
        try {
            retrofit2.Response response = service.login(buildLoginParams(username, password)).execute();
            if (response.isSuccessful()) {
                User body = (User) response.body();
                if (body != null) {
                    String token = body.getAccessToken();
                    tokenLocalDataSource.setAuthToken(token);
                    return requestUser();
                }
            }
            return new Result.Error(new IOException("Access token retrieval failed " + response.code() + " " + response.message()));
        } catch (IOException e) {
            return new Result.Error(e);
        }
    }

    private Result<User> requestUser() {
        try {
            retrofit2.Response response = service.getAuthedUser().execute();
            if (response.isSuccessful()) {
                List<User> users = (List<User>) response.body();
                if (users != null && !users.isEmpty()) {
                    return new Result.Success(users.get(0));
                }
            }
            return new Result.Error(new IOException("Failed to get authed user " + response.code() + " " + response.message()));
        } catch (IOException e) {
            return new Result.Error(e);
        }
    }

    private Map<String, String> buildLoginParams(String username, String password) {
        return Map.of(
                "client_id", BuildConfig.DESIGNER_NEWS_CLIENT_ID,
                "client_secret", BuildConfig.DESIGNER_NEWS_CLIENT_SECRET,
                "grant_type", "password",
                "username", username,
                "password", password
        );
    }
}