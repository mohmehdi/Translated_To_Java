package io.plaidapp.core.designernews.data.users;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.core.designernews.user1;
import io.plaidapp.core.designernews.user2;
import io.plaidapp.core.designernews.users;
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserRepositoryTest {

    private UserRemoteDataSource dataSource = Mockito.mock(UserRemoteDataSource.class);
    private UserRepository repository = new UserRepository(dataSource);

    @Test
    public void getUsers_withNoCachedUsers_withSuccess() throws Exception {

        List<Long> ids = List.of(111L, 222L);
        withUsersSuccess(ids, users);

        Set<Long> idSet = new HashSet<>();
        idSet.add(111L);
        idSet.add(222L);

        Result<Set<User>> result = repository.getUsers(idSet);

        Mockito.verify(dataSource).getUsers(ids);

        Assert.assertEquals(Result.Success(users), result);
    }

    @Test
    public void getUsers_withNoCachedUsers_withError() throws Exception {

        List<Long> ids = List.of(111L, 222L);
        withUsersError(ids);

        Set<Long> idSet = new HashSet<>();
        idSet.add(111L);
        idSet.add(222L);

        Result<Set<User>> result = repository.getUsers(idSet);

        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void getUsers_withCachedUsers_withSuccess() throws Exception {

        withUsersSuccess(List.of(111L), List.of(user1));
        repository.getUsers(Set.of(111L));

        withUsersSuccess(List.of(222L), List.of(user2));

        Set<Long> idSet = new HashSet<>();
        idSet.add(111L);
        idSet.add(222L);

        Result<Set<User>> result = repository.getUsers(idSet);

        Mockito.verify(dataSource).getUsers(List.of(222L));

        Assert.assertEquals(Result.Success(users), result);
    }

    @Test
    public void getUsers_withCachedUsers_withError() throws Exception {

        withUsersSuccess(List.of(111L), List.of(user1));
        repository.getUsers(Set.of(111L));

        withUsersError(List.of(222L));

        Set<Long> idSet = new HashSet<>();
        idSet.add(111L);
        idSet.add(222L);

        Result<Set<User>> result = repository.getUsers(idSet);

        Assert.assertEquals(Result.Success(Set.of(user1)), result);
    }

    private void withUsersSuccess(List<Long> ids, List<User> users) throws Exception {
        Result<List<User>> result = new Result.Success<>(users);
        Mockito.when(dataSource.getUsers(ids)).thenReturn(result);
    }

    private void withUsersError(List<Long> ids) throws Exception {
        Result<List<User>> result = new Result.Error<>(new IOException("Users error"));
        Mockito.when(dataSource.getUsers(ids)).thenReturn(result);
    }
}