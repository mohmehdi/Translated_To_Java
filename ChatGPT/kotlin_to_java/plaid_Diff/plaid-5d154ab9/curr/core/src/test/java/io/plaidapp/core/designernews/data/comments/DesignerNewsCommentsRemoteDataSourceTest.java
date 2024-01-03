package io.plaidapp.core.designernews.data.comments;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.errorResponseBody;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.repliesResponses;
import kotlinx.coroutines.experimental.CompletableDeferred;
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import retrofit2.Response;

public class DesignerNewsCommentsRemoteDataSourceTest {

    private DesignerNewsService service = Mockito.mock(DesignerNewsService.class);
    private DesignerNewsCommentsRemoteDataSource dataSource = new DesignerNewsCommentsRemoteDataSource(service);

    @Test
    public void getComments_whenRequestSuccessful() throws Exception {
        
        Response<List<CommentResponse>> result = Response.success(repliesResponses);
        Mockito.when(service.getComments("1")).thenReturn(new CompletableDeferred<>(result));

        
        Result<List<CommentResponse>> response = dataSource.getComments(Arrays.asList(1L));

        
        Assert.assertNotNull(response);
        Assert.assertEquals(Result.Success(repliesResponses), response);
    }

    @Test
    public void getComments_forMultipleComments() throws Exception {
        
        Response<List<CommentResponse>> result = Response.success(repliesResponses);
        Mockito.when(service.getComments("11,12")).thenReturn(new CompletableDeferred<>(result));

        
        Result<List<CommentResponse>> response = dataSource.getComments(Arrays.asList(11L, 12L));

        
        Assert.assertNotNull(response);
        Assert.assertEquals(Result.Success(repliesResponses), response);
    }

    @Test
    public void getComments_whenRequestFailed() throws Exception {
        
        Response<List<CommentResponse>> result = Response.error(400, errorResponseBody);
        Mockito.when(service.getComments("1")).thenReturn(new CompletableDeferred<>(result));

        
        Result<List<CommentResponse>> response = dataSource.getComments(Arrays.asList(1L));

        
        Assert.assertTrue(response instanceof Result.Error);
    }

    @Test
    public void getComments_whenResponseEmpty() throws Exception {
        
        Response<List<CommentResponse>> result = Response.success(null);
        Mockito.when(service.getComments("1")).thenReturn(new CompletableDeferred<>(result));

        
        Result<List<CommentResponse>> response = dataSource.getComments(Arrays.asList(1L));

        
        Assert.assertTrue(response instanceof Result.Error);
    }
}