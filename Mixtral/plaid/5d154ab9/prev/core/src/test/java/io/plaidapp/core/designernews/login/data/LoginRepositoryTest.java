package io.plaidapp.core.designernews.login.data;

import io.plaidapp.core.data.LoginLocalDataSource;
import io.plaidapp.core.data.LoginRemoteDataSource;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.model.User;
import java.io.IOException;
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class LoginRepositoryTest {

  private final String username = "user";
  private final String pass = "pass";
  private User user = new User(
    3,
    "Plaida",
    "Plaidich",
    "Plaida Plaidich",
    "www"
  );

  private LoginLocalDataSource localDataSource;
  private LoginRemoteDataSource remoteDataSource;
  private LoginRepository repository;

  @Test
  public void isNotLoggedIn_byDefault() {
    Assert.assertFalse(repository.isLoggedIn());
  }

  @Test
  public void isLoggedIn_afterSuccessfulLogin() throws Exception {
    withLoginSuccessful(username, pass);

    Result<User> result = repository.login(username, pass);

    Assert.assertEquals(Result.Success(user), result);
    Assert.assertTrue(repository.isLoggedIn());
    Assert.assertEquals(repository.getUser(), user);
  }

  @Test
  public void userNull_byDefault() {
    Assert.assertNull(repository.getUser());
  }

  @Test
  public void logout() {
    repository.logout();

    Assert.assertFalse(repository.isLoggedIn());
    Assert.assertNull(repository.getUser());
  }

  @Test
  public void logout_afterLogin() throws Exception {
    withLoginSuccessful(username, pass);
    repository.login(username, pass);

    repository.logout();

    Assert.assertFalse(repository.isLoggedIn());
    Assert.assertNull(repository.getUser());
  }

  @Test
  public void isNotLoggedIn_afterFailedLogin() throws Exception {
    withLoginFailed(username, pass);

    Result<User> result = repository.login(username, pass);

    Assert.assertTrue(result instanceof Result.Error);
    Assert.assertFalse(repository.isLoggedIn());
    Assert.assertNull(repository.getUser());
  }

  private void withLoginSuccessful(String username, String pass)
    throws Exception {
    Result<User> result = Result.Success(user);
    Mockito.when(remoteDataSource.login(username, pass)).thenReturn(result);
  }

  private void withLoginFailed(String username, String pass) throws Exception {
    Mockito
      .when(remoteDataSource.login(username, pass))
      .thenReturn(new Result.Error<User>(new IOException("error")));
  }
}
