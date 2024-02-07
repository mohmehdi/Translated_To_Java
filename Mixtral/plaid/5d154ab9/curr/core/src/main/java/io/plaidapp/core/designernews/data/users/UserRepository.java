package io.plaidapp.core.designernews.data.users;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.users.model.User;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UserRepository {

    private final UserRemoteDataSource dataSource;
    private final Map<Long, User> cachedUsers = new HashMap<>();

    public UserRepository(UserRemoteDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Result<Set<User>> getUsers(Set<Long> ids) {
        Set<Long> notCachedUsers = new HashSet<>(ids);
        notCachedUsers.removeIf(cachedUsers::containsKey);

        if (!notCachedUsers.isEmpty()) {
            getAndCacheUsers(new ArrayList<>(notCachedUsers));
        }

        List<User> users = new ArrayList<>();
        for (Long id : ids) {
            User user = cachedUsers.get(id);
            if (user != null) {
                users.add(user);
            }
        }
        if (!users.isEmpty()) {
            return new Result.Success<>(new HashSet<>(users));
        }
        return new Result.Error<>(new IOException("Unable to get users"));
    }

    private void getAndCacheUsers(List<Long> userIds) {
        Result<List<User>> result = dataSource.getUsers(userIds);

        if (result instanceof Result.Success) {
            List<User> users = ((Result.Success<List<User>>) result).getData();
            for (User user : users) {
                cachedUsers.put(user.getId(), user);
            }
        }
    }

    public static UserRepository getInstance(UserRemoteDataSource dataSource) {
        UserRepository instance = INSTANCE;
        if (instance == null) {
            synchronized (UserRepository.class) {
                instance = INSTANCE;
                if (instance == null) {
                    instance = new UserRepository(dataSource);
                    INSTANCE = instance;
                }
            }
        }
        return instance;
    }

    private static volatile UserRepository INSTANCE;
}