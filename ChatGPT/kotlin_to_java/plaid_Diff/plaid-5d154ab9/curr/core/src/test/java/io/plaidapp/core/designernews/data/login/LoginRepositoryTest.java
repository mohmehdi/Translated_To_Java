package io.plaidapp.core.designernews.data.login;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.users.model.User;
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

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

    private LoginLocalDataSource localDataSource = Mockito.mock(LoginLocalDataSource.class);
    private LoginRemoteDataSource remoteDataSource = Mockito.mock(LoginRemoteDataSource.class);
    private LoginRepository repository = new LoginRepository(localDataSource, remoteDataSource);

    @Test
    public void isNotLoggedIn_byDefault() {


        Assert.assertFalse(repository.isLoggedIn());
    }

    @Test
    public void isLoggedIn_afterSuccessfulLogin() throws Exception {


        withLoginSuccessful(username, pass);


        Result<User> result = runBlocking(() -> repository.login(username, pass));


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
        runBlocking(() -> repository.login(username, pass));


        repository.logout();


        Assert.assertFalse(repository.isLoggedIn());

        Assert.assertNull(repository.getUser());
    }

    @Test
    public void isNotLoggedIn_afterFailedLogin() throws Exception {


        withLoginFailed(username, pass);


        Result<User> result = runBlocking(() -> repository.login(username, pass));


        Assert.assertTrue(result instanceof Result.Error);

        Assert.assertFalse(repository.isLoggedIn());

        Assert.assertNull(repository.getUser());
    }

    private void withLoginSuccessful(String username, String pass) throws Exception {
        Result<User> result = Result.Success(user);
        Mockito.when(remoteDataSource.login(username, pass)).thenReturn(result);
    }

    private void withLoginFailed(String username, String pass) throws Exception {
        Mockito.when(remoteDataSource.login(username, pass))
                .thenReturn(Result.Error(new IOException("error")));
    }
}