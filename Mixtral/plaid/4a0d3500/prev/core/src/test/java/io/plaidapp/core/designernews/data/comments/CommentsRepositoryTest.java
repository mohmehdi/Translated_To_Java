package io.plaidapp.core.designernews.data.comments;

import com.nhaarman.mockitokotlin2.Mockito;
import com.nhaarman.mockitokotlin2.mock;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.repliesResponses;
import io.plaidapp.core.designernews.replyResponse1;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import kotlin.coroutines.experimental.Builders;
import kotlin.coroutines.experimental.Runnable;
import kotlin.coroutines.experimental.intrinsics.MainDispatcher;
import org.junit.Assert;
import org.junit.Test;

class CommentsRepositoryTest {

  private final String body = "Plaid 2.0 is awesome";

  private final CommentsRemoteDataSource dataSource = mock();
  private final CommentsRepository repository = new CommentsRepository(
    dataSource
  );

  @Test
  public void getComments_withSuccess() throws Exception {
    List<Long> ids = Arrays.asList(1L);
    Result result = Result.Success(repliesResponses);
    Mockito.when(dataSource.getComments(ids)).thenReturn(result);

    Result data = repository.getComments(ids);

    Assert.assertEquals(result, data);
  }

  @Test
  public void getComments_withError() throws Exception {
    List<Long> ids = Arrays.asList(1L);
    Result result = Result.Error(new IOException("error"));
    Mockito.when(dataSource.getComments(ids)).thenReturn(result);

    Result data = repository.getComments(ids);

    Assert.assertEquals(result, data);
  }

  @Test
  public void postStoryComment_withSuccess() throws Exception {
    Result result = Result.Success(replyResponse1);
    Mockito.when(dataSource.comment(body, null, 11L, 111L)).thenReturn(result);

    Result data = repository.postStoryComment(body, 11L, 111L);

    Assert.assertEquals(result, data);
  }

  @Test
  public void postStoryComment_withError() throws Exception {
    Result result = Result.Error(new IOException("error"));
    Mockito.when(dataSource.comment(body, null, 11L, 111L)).thenReturn(result);

    Result data = repository.postStoryComment(body, 11L, 111L);

    Assert.assertEquals(result, data);
  }

  @Test
  public void postReply_withSuccess() throws Exception {
    Result result = Result.Success(replyResponse1);
    Mockito.when(dataSource.comment(body, 11L, null, 111L)).thenReturn(result);

    Result data = repository.postReply(body, 11L, 111L);

    Assert.assertEquals(result, data);
  }

  @Test
  public void postReply_withError() throws Exception {
    Result result = Result.Error(new IOException("error"));
    Mockito.when(dataSource.comment(body, 11L, null, 111L)).thenReturn(result);

    Result data = repository.postReply(body, 11L, 111L);

    Assert.assertEquals(result, data);
  }
}
