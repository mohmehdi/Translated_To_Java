package io.plaidapp.core.designernews.data.users;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.api.model.User;
import java.io.IOException;
import java.util.List;

public class UserRemoteDataSource {

    private DesignerNewsService service;

    public UserRemoteDataSource(DesignerNewsService service) {
        this.service = service;
    }

    public Result<List<User>> getUsers(List<Long> userIds) throws IOException {
        String requestIds = userIds.stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));

        retrofit2.Response<List<User>> response = service.getUsers(requestIds).execute();
        if (response.isSuccessful()) {
            List<User> body = response.body();
            if (body != null) {
                return new Result.Success<>(body);
            }
        }

        throw new IOException("Error getting users " + response.code() + " " + response.message());
    }
}