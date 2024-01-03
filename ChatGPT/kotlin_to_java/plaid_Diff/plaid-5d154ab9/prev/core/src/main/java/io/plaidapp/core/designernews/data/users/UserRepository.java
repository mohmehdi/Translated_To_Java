package io.plaidapp.core.designernews.data.users;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.model.User;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UserRepository {

    private UserRemoteDataSource dataSource;
    private Map<Long, User> cachedUsers;

    public UserRepository(UserRemoteDataSource dataSource) {
        this.dataSource = dataSource;
        this.cachedUsers = new HashMap<>();
    }

    public Result<Set<User>> getUsers(Set<Long> ids) {
        Set<Long> notCachedUsers = new HashSet<>();
        for (Long id : ids) {
            if (!cachedUsers.containsKey(id)) {
                notCachedUsers.add(id);
            }
        }
        if (!notCachedUsers.isEmpty()) {
            getAndCacheUsers(notCachedUsers);
        }

        Set<User> users = new HashSet<>();
        for (Long id : ids) {
            User user = cachedUsers.get(id);
            if (user != null) {
                users.add(user);
            }
        }
        if (!users.isEmpty()) {
            return new Result.Success<>(users);
        }
        return new Result.Error<>(new IOException("Unable to get users"));
    }

    private void getAndCacheUsers(List<Long> userIds) {
        Result<List<User>> result = dataSource.getUsers(userIds);

        if (result instanceof Result.Success) {
            List<User> data = ((Result.Success<List<User>>) result).getData();
            for (User user : data) {
                cachedUsers.put(user.getId(), user);
            }
        }
    }

    private static volatile UserRepository INSTANCE;

    public static UserRepository getInstance(UserRemoteDataSource dataSource) {
        if (INSTANCE == null) {
            synchronized (UserRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new UserRepository(dataSource);
                }
            }
        }
        return INSTANCE;
    }
}