package io.plaidapp.core.designernews.data.votes;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.api.any;
import io.plaidapp.core.designernews.errorResponseBody;
import kotlinx.coroutines.experimental.CompletableDeferred;
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import retrofit2.Response;

public class VotesRemoteDataSourceTest {

    private final long userId = 3L;
    private final long storyId = 1345L;

    private final DesignerNewsService service = Mockito.mock(DesignerNewsService.class);
    private final VotesRemoteDataSource dataSource = new VotesRemoteDataSource(service);

    @Test
    public void upvoteStory_whenRequestSuccessful() throws Exception {

        Response<Unit> response = Response.success(Unit.INSTANCE);
        Mockito.when(service.upvoteStoryV2(any())).thenReturn(CompletableDeferred(response));


        Result<Unit> result = runBlocking { dataSource.upvoteStory(storyId, userId); }


        Assert.assertEquals(Result.Success(Unit.INSTANCE), result);
    }

    @Test
    public void upvoteStory_whenRequestFailed() throws Exception {

        Response<Unit> response = Response.error(404, errorResponseBody);
        Mockito.when(service.upvoteStoryV2(any())).thenReturn(CompletableDeferred(response));


        Result<Unit> result = runBlocking { dataSource.upvoteStory(storyId, userId); }


        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void upvoteComment_whenRequestSuccessful() throws Exception {

        Response<Unit> response = Response.success(Unit.INSTANCE);
        Mockito.when(service.upvoteComment(any())).thenReturn(CompletableDeferred(response));


        Result<Unit> result = runBlocking { dataSource.upvoteComment(storyId, userId); }


        Assert.assertEquals(Result.Success(Unit.INSTANCE), result);
    }

    @Test
    public void upvoteComment_whenRequestFailed() throws Exception {

        Response<Unit> response = Response.error(404, errorResponseBody);
        Mockito.when(service.upvoteComment(any())).thenReturn(CompletableDeferred(response));


        Result<Unit> result = runBlocking { dataSource.upvoteComment(storyId, userId); }


        Assert.assertTrue(result instanceof Result.Error);
    }
}