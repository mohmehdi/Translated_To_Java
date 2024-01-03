package io.plaidapp.core.designernews.data.users;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.errorResponseBody;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.core.designernews.users;
import kotlinx.coroutines.experimental.CompletableDeferred;
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import retrofit2.Response;

public class UserRemoteDataSourceTest {
    private DesignerNewsService service = Mockito.mock(DesignerNewsService.class);
    private UserRemoteDataSource dataSource = new UserRemoteDataSource(service);

    @Test
    public void getUsers_withSuccess() throws Exception {
        
        withUsersSuccess("111,222", users);

        
        Result<List<User>> result = dataSource.getUsers(Arrays.asList(111L, 222L));

        
        Mockito.verify(service).getUsers("111,222");
        
        Assert.assertEquals(Result.Success(users), result);
    }

    @Test
    public void getUsers_withError() throws Exception {
        
        withUsersError("111,222");

        
        Result<List<User>> result = dataSource.getUsers(Arrays.asList(111L, 222L));

        
        Assert.assertTrue(result instanceof Result.Error);
    }

    private void withUsersSuccess(String ids, List<User> users) {
        Response<List<User>> result = Response.success(users);
        Mockito.when(service.getUsers(ids)).thenReturn(new CompletableDeferred<>(result));
    }

    private void withUsersError(String ids) {
        Response<List<User>> result = Response.error(400, errorResponseBody);
        Mockito.when(service.getUsers(ids)).thenReturn(new CompletableDeferred<>(result));
    }
}