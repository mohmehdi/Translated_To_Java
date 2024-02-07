package io.plaidapp.core.designernews.data.comments;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.api.DesignerNewsCommentsRemoteDataSource;
import io.plaidapp.core.designernews.data.api.repliesResponses;
import java.io.IOException;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class CommentsRepositoryTest {

  private DesignerNewsCommentsRemoteDataSource dataSource = Mockito.mock(
    DesignerNewsCommentsRemoteDataSource.class
  );
  private CommentsRepository repository = new CommentsRepository(dataSource);

  @Test
  public void getComments_withSuccess() throws Exception {
    List<Long> ids = List.of(1L);
    Result<repliesResponses> result = new Result.Success<>(repliesResponses);
    Mockito.when(dataSource.getComments(ids)).thenReturn(result);

    Result<repliesResponses> data = repository.getComments(ids);

    Assert.assertEquals(result, data);
  }

  @Test
  public void getComments_withError() throws Exception {
    List<Long> ids = List.of(1L);
    Result<repliesResponses> result = new Result.Error<>(
      new IOException("error")
    );
    Mockito.when(dataSource.getComments(ids)).thenReturn(result);

    Result<repliesResponses> data = repository.getComments(ids);

    Assert.assertEquals(result, data);
  }
}
