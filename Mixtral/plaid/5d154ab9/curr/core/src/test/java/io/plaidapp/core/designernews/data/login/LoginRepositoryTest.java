package io.plaidapp.core.designernews.data.login;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.login.LoginLocalDataSource;
import io.plaidapp.core.data.login.LoginRemoteDataSource;
import io.plaidapp.core.data.login.LoginRepository;
import io.plaidapp.core.designernews.data.users.model.User;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class LoginRepositoryTest {

  private String username = "user";
  private String pass = "pass";
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
    assertFalse(repository.isLoggedIn());
  }

  public void isLoggedIn_afterSuccessfulLogin() throws Exception {
    withLoginSuccessful(username, pass);

    Result result = repository.login(username, pass);

    assertEquals(Result.Success(user), result);
    assertTrue(repository.isLoggedIn());
    assertEquals(repository.getUser(), user);
  }

  @Test
  public void userNull_byDefault() {
    assertNull(repository.getUser());
  }

  @Test
  public void logout() {
    repository.logout();

    assertFalse(repository.isLoggedIn());
    assertNull(repository.getUser());
  }

  @Test
  public void logout_afterLogin() throws Exception {
    withLoginSuccessful(username, pass);
    repository.login(username, pass);

    repository.logout();

    assertFalse(repository.isLoggedIn());
    assertNull(repository.getUser());
  }

  @Test
  public void isNotLoggedIn_afterFailedLogin() throws Exception {
    withLoginFailed(username, pass);

    Result result = repository.login(username, pass);

    assertTrue(result instanceof Result.Error);
    assertFalse(repository.isLoggedIn());
    assertNull(repository.getUser());
  }

  private void withLoginSuccessful(String username, String pass)
    throws Exception {
    Result result = Result.Success(user);
    Mockito.when(remoteDataSource.login(username, pass)).thenReturn(result);
  }

  private void withLoginFailed(String username, String pass) throws Exception {
    Mockito
      .when(remoteDataSource.login(username, pass))
      .thenReturn(new Result.Error(new IOException("error")));
  }
}
