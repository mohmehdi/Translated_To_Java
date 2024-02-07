package io.plaidapp.core.designernews.data.users;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.api.model.User;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Response;

public class UserRemoteDataSource {

  private final DesignerNewsService service;

  public UserRemoteDataSource(DesignerNewsService service) {
    this.service = service;
  }

  public Result<List<User>> getUsers(List<Long> userIds) {
    StringBuilder requestIds = new StringBuilder();
    for (Long id : userIds) {
      requestIds.append(id).append(",");
    }
    String idsString = requestIds.substring(0, requestIds.length() - 1);

    try {
      Response<List<User>> response = service.getUsers(idsString).execute();
      if (response.isSuccessful()) {
        List<User> body = response.body();
        if (body != null) {
          return new Result.Success<>(body);
        }
      }
    } catch (IOException e) {
      return new Result.Error<>(e);
    }

    return new Result.Error<>(
      new IOException(
        "Error getting users " + response.code() + " " + response.message()
      )
    );
  }
}
