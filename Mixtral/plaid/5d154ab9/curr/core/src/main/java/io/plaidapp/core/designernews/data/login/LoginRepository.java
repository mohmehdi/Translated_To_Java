package io.plaidapp.core.designernews.data.login;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.login.LoginLocalDataSource;
import io.plaidapp.core.data.login.LoginRemoteDataSource;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.users.model.User;

public class LoginRepository {

  private LoginLocalDataSource localDataSource;
  private LoginRemoteDataSource remoteDataSource;
  private User user;

  public LoginRepository(
    LoginLocalDataSource localDataSource,
    LoginRemoteDataSource remoteDataSource
  ) {
    this.localDataSource = localDataSource;
    this.remoteDataSource = remoteDataSource;
    this.user = localDataSource.getUser();
  }

  public void logout() {
    this.user = null;
    localDataSource.logout();
    remoteDataSource.logout();
  }

  public Result<User> login(String username, String password) {
    Result<User> result = remoteDataSource.login(username, password);

    if (result instanceof Result.Success) {
      setLoggedInUser(((Result.Success<User>) result).getData());
    }

    return result;
  }

  private void setLoggedInUser(User loggedInUser) {
    this.user = loggedInUser;
    localDataSource.setUser(user);
  }

  public DesignerNewsService getService() {
    return remoteDataSource.getService();
  }

  public static LoginRepository getInstance(
    LoginLocalDataSource localDataSource,
    LoginRemoteDataSource remoteDataSource
  ) {
    if (INSTANCE == null) {
      synchronized (LoginRepository.class) {
        if (INSTANCE == null) {
          INSTANCE = new LoginRepository(localDataSource, remoteDataSource);
        }
      }
    }
    return INSTANCE;
  }

  private static volatile LoginRepository INSTANCE;
}
