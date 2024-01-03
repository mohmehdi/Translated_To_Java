package io.plaidapp.core.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.parentCommentResponse;
import io.plaidapp.core.designernews.data.api.parentCommentWithReplies;
import io.plaidapp.core.designernews.data.api.parentCommentWithRepliesWithoutReplies;
import io.plaidapp.core.designernews.data.api.reply1;
import io.plaidapp.core.designernews.data.api.replyResponse1;
import io.plaidapp.core.designernews.data.api.replyResponse2;
import io.plaidapp.core.designernews.data.api.replyWithReplies1;
import io.plaidapp.core.designernews.data.comments.CommentsRepository;
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CommentsWithRepliesUseCaseTest {

    private CommentsRepository repository = Mockito.mock(CommentsRepository.class);
    private CommentsWithRepliesUseCase useCase = new CommentsWithRepliesUseCase(repository);

    @Test
    public void getComments_noReplies_whenRequestSuccessful() throws Exception {

        List<Long> ids = new ArrayList<>();
        ids.add(reply1.id);
        Result<List<replyResponse1>> repositoryResult = new Result.Success<>(new ArrayList<>(replyResponse1));
        Mockito.when(repository.getComments(ids)).thenReturn(repositoryResult);

        Result<List<replyWithReplies1>> result = useCase.getCommentsWithReplies(ids);

        Mockito.verify(repository).getComments(ids);

        Assert.assertEquals(new Result.Success<>(new ArrayList<>(replyWithReplies1)), result);
    }

    @Test
    public void getComments_noReplies_whenRequestFailed() throws Exception {

        List<Long> ids = new ArrayList<>();
        ids.add(11L);
        Result<List<replyResponse1>> repositoryResult = new Result.Error<>(new IOException("Unable to get comments"));
        Mockito.when(repository.getComments(ids)).thenReturn(repositoryResult);

        Result<List<replyWithReplies1>> result = useCase.getCommentsWithReplies(ids);

        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void getComments_multipleReplies_whenRequestSuccessful() throws Exception {

        Result<List<parentCommentResponse>> resultParent = new Result.Success<>(new ArrayList<>(parentCommentResponse));
        List<Long> parentIds = new ArrayList<>();
        parentIds.add(1L);
        Mockito.when(repository.getComments(parentIds)).thenReturn(resultParent);

        List<Long> childrenIds = new ArrayList<>();
        childrenIds.add(11L);
        childrenIds.add(12L);
        Result<List<replyResponse1>> resultChildren = new Result.Success<>(new ArrayList<>(replyResponse1, replyResponse2));
        Mockito.when(repository.getComments(childrenIds)).thenReturn(resultChildren);

        Result<List<parentCommentWithReplies>> result = useCase.getCommentsWithReplies(new ArrayList<>(1L));

        Mockito.verify(repository).getComments(parentIds);
        Mockito.verify(repository).getComments(childrenIds);

        Assert.assertEquals(new Result.Success<>(new ArrayList<>(parentCommentWithReplies)), result);
    }

    @Test
    public void getComments_multipleReplies_whenRepliesRequestFailed() throws Exception {

        Result<List<parentCommentResponse>> resultParent = new Result.Success<>(new ArrayList<>(parentCommentResponse));
        List<Long> parentIds = new ArrayList<>();
        parentIds.add(1L);
        Mockito.when(repository.getComments(parentIds)).thenReturn(resultParent);

        Result<List<replyResponse1>> resultChildrenError = new Result.Error<>(new IOException("Unable to get comments"));
        List<Long> childrenIds = new ArrayList<>();
        childrenIds.add(11L);
        childrenIds.add(12L);
        Mockito.when(repository.getComments(childrenIds)).thenReturn(resultChildrenError);

        Result<List<parentCommentWithRepliesWithoutReplies>> result = useCase.getCommentsWithReplies(new ArrayList<>(1L));

        Mockito.verify(repository).getComments(parentIds);
        Mockito.verify(repository).getComments(childrenIds);

        Assert.assertEquals(new Result.Success<>(new ArrayList<>(parentCommentWithRepliesWithoutReplies)), result);
    }
}