package io.plaidapp.core.designernews.data.votes;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.api.DesignerNewsService;
import io.plaidapp.core.data.api.any;
import io.plaidapp.core.designernews.data.api.errorResponseBody;
import java.lang.reflect.Type;
import kotlinx.coroutines.experimental.CompletableDeferred;
import kotlinx.coroutines.experimental.Runnable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import retrofit2.Response;

public class VotesRemoteDataSourceTest {

  private long userId = 3;
  private long storyId = 1345;

  @Mock
  private DesignerNewsService service;

  private VotesRemoteDataSource dataSource;

  @Test
  public void upvoteStory_whenRequestSuccessful() throws Exception {
    Response<Unit> response = Response.success(null);
    Mockito
      .when(service.upvoteStoryV2(any()))
      .thenReturn(new CompletableDeferred<>(response));

    Result result = dataSource.upvoteStory(storyId, userId);

    Assert.assertEquals(Result.Success(null), result);
  }

  @Test
  public void upvoteStory_whenRequestFailed() throws Exception {
    Response<Unit> response = Response.error(404, errorResponseBody);
    Mockito
      .when(service.upvoteStoryV2(any()))
      .thenReturn(new CompletableDeferred<>(response));

    Result result = dataSource.upvoteStory(storyId, userId);

    Assert.assertTrue(result instanceof Result.Error);
  }

  @Test
  public void upvoteComment_whenRequestSuccessful() throws Exception {
    Response<Unit> response = Response.success(null);
    Mockito
      .when(service.upvoteComment(any()))
      .thenReturn(new CompletableDeferred<>(response));

    Result result = dataSource.upvoteComment(storyId, userId);

    Assert.assertEquals(Result.Success(null), result);
  }

  @Test
  public void upvoteComment_whenRequestFailed() throws Exception {
    Response<Unit> response = Response.error(404, errorResponseBody);
    Mockito
      .when(service.upvoteComment(any()))
      .thenReturn(new CompletableDeferred<>(response));

    Result result = dataSource.upvoteComment(storyId, userId);

    Assert.assertTrue(result instanceof Result.Error);
  }
}
