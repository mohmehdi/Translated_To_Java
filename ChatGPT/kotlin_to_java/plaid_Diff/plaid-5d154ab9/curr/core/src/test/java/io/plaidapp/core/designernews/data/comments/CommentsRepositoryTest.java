package io.plaidapp.core.designernews.data.comments;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.repliesResponses;
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;

public class CommentsRepositoryTest {
    private DesignerNewsCommentsRemoteDataSource dataSource = Mockito.mock(DesignerNewsCommentsRemoteDataSource.class);
    private CommentsRepository repository = new CommentsRepository(dataSource);

    @Test
    public void getComments_withSuccess() throws Exception {
        List<Long> ids = List.of(1L);
        Result<List<Reply>> result = Result.Success(repliesResponses);
        Mockito.when(dataSource.getComments(ids)).thenReturn(result);

        Result<List<Reply>> data = repository.getComments(ids);

        Assert.assertEquals(result, data);
    }

    @Test
    public void getComments_withError() throws Exception {
        List<Long> ids = List.of(1L);
        Result<List<Reply>> result = Result.Error(new IOException("error"));
        Mockito.when(dataSource.getComments(ids)).thenReturn(result);

        Result<List<Reply>> data = repository.getComments(ids);

        Assert.assertEquals(result, data);
    }
}