package io.plaidapp.core.designernews.data.users;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.api.errorResponseBody;
import io.plaidapp.core.designernews.data.api.model.User;
import io.plaidapp.core.designernews.data.api.users;
import kotlinx.coroutines.experimental.CompletableDeferred;
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import retrofit2.Response;

public class UserRemoteDataSourceTest {

  @Mock
  private DesignerNewsService service;

  private UserRemoteDataSource dataSource;

  @Test
  public void getUsers_withSuccess() {
    runBlocking(new CallbackToFutureAdapter.EmptyCallback());

    withUsersSuccess("111,222", users);

    Result<List<User>> result = dataSource.getUsers(Arrays.asList(111L, 222L));

    verify(service).getUsers("111,222");

    Assert.assertEquals(Result.Success(users), result);
  }

  @Test
  public void getUsers_withError() {
    runBlocking(new CallbackToFutureAdapter.EmptyCallback());

    withUsersError("111,222");

    Result<List<User>> result = dataSource.getUsers(Arrays.asList(111L, 222L));

    Assert.assertTrue(result instanceof Result.Error);
  }

  private void withUsersSuccess(String ids, List<User> users) {
    Response<List<User>> result = Response.success(users);
    when(service.getUsers(ids)).thenReturn(new CompletableDeferred<>(result));
  }

  private void withUsersError(String ids) {
    Response<List<User>> result = Response.error(400, errorResponseBody);
    when(service.getUsers(ids)).thenReturn(new CompletableDeferred<>(result));
  }
}
