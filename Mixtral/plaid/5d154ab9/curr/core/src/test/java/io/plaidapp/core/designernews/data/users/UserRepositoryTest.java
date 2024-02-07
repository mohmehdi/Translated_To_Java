package io.plaidapp.core.designernews.data.users;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.core.designernews.user1;
import io.plaidapp.core.designernews.user2;
import io.plaidapp.core.designernews.users;
import io.plaidapp.core.schedulers.TestSchedulerProvider;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

public class UserRepositoryTest {

  @Mock
  UserRemoteDataSource dataSource;

  UserRepository repository;

  @Test
  public void getUsers_withNoCachedUsers_withSuccess() throws Exception {
    List<Long> ids = Arrays.asList(111L, 222L);
    withUsersSuccess(ids, users);

    Single<Result<Set<User>>> result = repository.getUsers(
      Collections.singleton(111L),
      Collections.singleton(222L)
    );

    Mockito.verify(dataSource).getUsers(ids);

    Result<Set<User>> successResult = new Result.Success<>(users);
    Assert.assertEquals(successResult, result.blockingGet());
  }

  @Test
  public void getUsers_withNoCachedUsers_withError() throws Exception {
    List<Long> ids = Arrays.asList(111L, 222L);
    withUsersError(ids);

    Single<Result<Set<User>>> result = repository.getUsers(
      Collections.singleton(111L),
      Collections.singleton(222L)
    );

    Result<Set<User>> errorResult = new Result.Error<>(
      new IOException("Users error")
    );
    Assert.assertEquals(errorResult, result.blockingGet());
  }

  @Test
  public void getUsers_withCachedUsers_withSuccess() throws Exception {
    withUsersSuccess(Arrays.asList(111L), Arrays.asList(user1));
    repository.getUsers(Collections.singleton(111L), Schedulers.trampoline());

    withUsersSuccess(Arrays.asList(222L), Arrays.asList(user2));

    Single<Result<Set<User>>> result = repository.getUsers(
      Collections.singleton(111L),
      Collections.singleton(222L)
    );

    Mockito.verify(dataSource).getUsers(Arrays.asList(222L));

    Result<Set<User>> successResult = new Result.Success<>(users);
    Assert.assertEquals(successResult, result.blockingGet());
  }

  @Test
  public void getUsers_withCachedUsers_withError() throws Exception {
    withUsersSuccess(Arrays.asList(111L), Arrays.asList(user1));
    repository.getUsers(Collections.singleton(111L), Schedulers.trampoline());

    withUsersError(Arrays.asList(222L));

    Single<Result<Set<User>>> result = repository.getUsers(
      Collections.singleton(111L),
      Collections.singleton(222L)
    );

    Result<Set<User>> successResult = new Result.Success<>(
      Collections.singleton(user1)
    );
    Assert.assertEquals(successResult, result.blockingGet());
  }

  private void withUsersSuccess(List<Long> ids, List<User> users)
    throws Exception {
    Result<Set<User>> result = new Result.Success<>(users);
    Mockito.when(dataSource.getUsers(ids)).thenReturn(Single.just(result));
  }

  private void withUsersError(List<Long> ids) throws Exception {
    Result<Set<User>> result = new Result.Error<>(
      new IOException("Users error")
    );
    Mockito.when(dataSource.getUsers(ids)).thenReturn(Single.just(result));
  }
}
