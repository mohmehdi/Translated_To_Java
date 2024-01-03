package io.plaidapp.core.designernews.data.users;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.model.User;
import io.plaidapp.core.designernews.data.api.UserRemoteDataSource;
import io.plaidapp.core.designernews.data.api.user1;
import io.plaidapp.core.designernews.data.api.user2;
import io.plaidapp.core.designernews.data.api.users;
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import java.io.IOException;

public class UserRepositoryTest {

    private UserRemoteDataSource dataSource = Mockito.mock(UserRemoteDataSource.class);
    private UserRepository repository = new UserRepository(dataSource);

    @Test
    public void getUsers_withNoCachedUsers_withSuccess() throws Exception {
        
        List<Long> ids = Arrays.asList(111L, 222L);
        withUsersSuccess(ids, users);

        
        Result<Set<User>> result = repository.getUsers(new HashSet<>(Arrays.asList(111L, 222L)));

        
        Mockito.verify(dataSource).getUsers(ids);
        
        Assert.assertEquals(Result.Success(users), result);
    }

    @Test
    public void getUsers_withNoCachedUsers_withError() throws Exception {
        
        List<Long> ids = Arrays.asList(111L, 222L);
        withUsersError(ids);

        
        Result<Set<User>> result = repository.getUsers(new HashSet<>(ids));

        
        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void getUsers_withCachedUsers_withSuccess() throws Exception {
        
        withUsersSuccess(Arrays.asList(111L), Arrays.asList(user1));
        repository.getUsers(new HashSet<>(Arrays.asList(111L)));
        
        withUsersSuccess(Arrays.asList(222L), Arrays.asList(user2));

        
        Result<Set<User>> result = repository.getUsers(new HashSet<>(Arrays.asList(111L, 222L)));
        
        Mockito.verify(dataSource).getUsers(Arrays.asList(222L));
        
        Assert.assertEquals(Result.Success(users), result);
    }

    @Test
    public void getUsers_withCachedUsers_withError() throws Exception {
        
        withUsersSuccess(Arrays.asList(111L), Arrays.asList(user1));
        repository.getUsers(new HashSet<>(Arrays.asList(111L)));
        
        withUsersError(Arrays.asList(222L));

        
        Result<Set<User>> result = repository.getUsers(new HashSet<>(Arrays.asList(111L, 222L)));

        
        Assert.assertEquals(Result.Success(new HashSet<>(Arrays.asList(user1))), result);
    }

    private void withUsersSuccess(List<Long> ids, List<User> users) throws Exception {
        Result<List<User>> result = Result.Success(users);
        Mockito.when(dataSource.getUsers(ids)).thenReturn(result);
    }

    private void withUsersError(List<Long> ids) throws Exception {
        Result<List<User>> result = Result.Error(new IOException("Users error"));
        Mockito.when(dataSource.getUsers(ids)).thenReturn(result);
    }
}