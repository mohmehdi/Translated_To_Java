package io.plaidapp.core.designernews.data.login;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.login.model.AccessToken;
import io.plaidapp.core.designernews.data.users.UsersRemoteDataSource;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.core.designernews.errorResponseBody;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
  private UsersRemoteDataSource authTokenDataSource = Mockito.mock(
    UsersRemoteDataSource.class
  );
  private DesignerNewsLoginRemoteDataSource dataSource = new DesignerNewsLoginRemoteDataSource(
    authTokenDataSource,
    service
  );

  @Test
  public void logout_clearsToken()
    throws ExecutionException, InterruptedException {
    dataSource.logout();

    Assert.assertNull(authTokenDataSource.getAuthToken().get());
  }

  @Test
  public void login_successful_when_AccessTokenAndGetUserSuccessful()
    throws ExecutionException, InterruptedException {
    CompletableFuture<Response<AccessToken>> accessTokenResponse = new CompletableFuture<>();
    accessTokenResponse.complete(Response.success(accessToken));
    Mockito
      .when(service.login(Mockito.anyMap()))
      .thenReturn(accessTokenResponse);

    CompletableFuture<Response<List<User>>> authUserResponse = new CompletableFuture<>();
    authUserResponse.complete(Response.success(java.util.Arrays.asList(user)));
    Mockito.when(service.getAuthedUser()).thenReturn(authUserResponse);

    Result<User> result = dataSource.login("test", "test").get();

    Assert.assertEquals(Result.Success.create(user), result);
  }

  @Test
  public void login_failed_whenAccessTokenFailed()
    throws ExecutionException, InterruptedException {
    CompletableFuture<Response<AccessToken>> failureResponse = new CompletableFuture<>();
    failureResponse.completeExceptionally(new RuntimeException());
    Mockito.when(service.login(Mockito.anyMap())).thenReturn(failureResponse);

    Result<User> result = dataSource.login("test", "test").get();

    Mockito.verify(service, Mockito.never()).getAuthedUser();

    Assert.assertTrue(result instanceof Result.Error);
  }

  @Test
  public void login_failed_whenGetUserFailed()
    throws ExecutionException, InterruptedException {
    CompletableFuture<Response<AccessToken>> accessTokenRespone = new CompletableFuture<>();
    accessTokenRespone.complete(Response.success(accessToken));
    Mockito
      .when(service.login(Mockito.anyMap()))
      .thenReturn(accessTokenRespone);

    CompletableFuture<Response<List<User>>> failureResponse = new CompletableFuture<>();
    failureResponse.completeExceptionally(new RuntimeException());
    Mockito.when(service.getAuthedUser()).thenReturn(failureResponse);

    Result<User> result = dataSource.login("test", "test").get();

    Assert.assertTrue(result instanceof Result.Error);
  }
}
