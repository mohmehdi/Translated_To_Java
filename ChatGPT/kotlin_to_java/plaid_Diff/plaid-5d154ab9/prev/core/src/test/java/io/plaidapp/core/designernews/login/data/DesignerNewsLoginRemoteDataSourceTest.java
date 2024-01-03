package io.plaidapp.core.designernews.login.data;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsAuthTokenLocalDataSource;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.api.errorResponseBody;
import io.plaidapp.core.designernews.data.api.model.AccessToken;
import io.plaidapp.core.designernews.data.api.model.User;
import kotlinx.coroutines.experimental.CompletableDeferred;
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import retrofit2.Response;

public class DesignerNewsLoginRemoteDataSourceTest {

    private User user = new User(
        3,
        "Plaidy",
        "Plaidinski",
        "Plaidy Plaidinski",
        "www"
    );
    private AccessToken accessToken = new AccessToken("token");

    private DesignerNewsService service = Mockito.mock(DesignerNewsService.class);
    private DesignerNewsAuthTokenLocalDataSource authTokenDataSource = Mockito.mock(DesignerNewsAuthTokenLocalDataSource.class);
    private LoginRemoteDataSource dataSource = new LoginRemoteDataSource(authTokenDataSource, service);

    @Test
    public void logout_clearsToken() {
        
        dataSource.logout();

        
        Assert.assertNull(authTokenDataSource.getAuthToken());
    }

    @Test
    public void login_successful_when_AccessTokenAndGetUserSuccessful() throws Exception {
        
        Response<AccessToken> accessTokenResponse = Response.success(accessToken);
        Mockito.when(service.login(Mockito.anyMap())).thenReturn(new CompletableDeferred<>(accessTokenResponse));
        Response<List<User>> authUserResponse = Response.success(Arrays.asList(user));
        Mockito.when(service.getAuthedUser()).thenReturn(new CompletableDeferred<>(authUserResponse));

        
        Result<User> result = runBlocking(() -> dataSource.login("test", "test"));

        
        Assert.assertEquals(Result.Success(user), result);
    }

    @Test
    public void login_failed_whenAccessTokenFailed() throws Exception {
        
        Response<AccessToken> failureResponse = Response.error(400, errorResponseBody);
        Mockito.when(service.login(Mockito.anyMap())).thenReturn(new CompletableDeferred<>(failureResponse));

        
        Result<User> result = runBlocking(() -> dataSource.login("test", "test"));

        
        Mockito.verify(service, Mockito.never()).getAuthedUser();
        
        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void login_failed_whenGetUserFailed() throws Exception {
        
        Response<AccessToken> accessTokenRespone = Response.success(accessToken);
        Mockito.when(service.login(Mockito.anyMap())).thenReturn(new CompletableDeferred<>(accessTokenRespone));
        
        Response<List<User>> failureResponse = Response.error(400, errorResponseBody);
        Mockito.when(service.getAuthedUser()).thenReturn(new CompletableDeferred<>(failureResponse));

        
        Result<User> result = runBlocking(() -> dataSource.login("test", "test"));

        
        Assert.assertTrue(result instanceof Result.Error);
    }
}