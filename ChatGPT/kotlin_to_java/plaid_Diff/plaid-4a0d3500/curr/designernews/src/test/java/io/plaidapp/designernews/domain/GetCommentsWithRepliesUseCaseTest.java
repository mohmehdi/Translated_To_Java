package io.plaidapp.designernews.domain;

import com.nhaarman.mockitokotlin2.mock;
import com.nhaarman.mockitokotlin2.verify;
import com.nhaarman.mockitokotlin2.whenever;
import io.plaidapp.core.data.Result;
import io.plaidapp.designernews.data.comments.CommentsRepository;
import io.plaidapp.designernews.parentCommentResponse;
import io.plaidapp.designernews.parentCommentWithReplies;
import io.plaidapp.designernews.parentCommentWithRepliesWithoutReplies;
import io.plaidapp.designernews.reply1;
import io.plaidapp.designernews.replyResponse1;
import io.plaidapp.designernews.replyResponse2;
import io.plaidapp.designernews.replyWithReplies1;
import kotlinx.coroutines.runBlocking;
import org.junit.Assert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GetCommentsWithRepliesUseCaseTest {

    private CommentsRepository repository = mock(CommentsRepository.class);
    private GetCommentsWithRepliesUseCase useCase = new GetCommentsWithRepliesUseCase(repository);

    @Test
    public void getComments_noReplies_whenRequestSuccessful() throws Exception {

        List<Long> ids = Arrays.asList(reply1.getId());
        Result<List<ReplyResponse>> repositoryResult = Result.Success(Arrays.asList(replyResponse1));
        whenever(repository.getComments(ids)).thenReturn(repositoryResult);

        Result<List<ReplyWithReplies>> result = runBlocking(() -> useCase.invoke(ids));

        verify(repository).getComments(ids);

        Assert.assertEquals(Result.Success(Arrays.asList(replyWithReplies1)), result);
    }

    @Test
    public void getComments_noReplies_whenRequestFailed() throws Exception {

        List<Long> ids = Arrays.asList(11L);
        Result<List<ReplyResponse>> repositoryResult = Result.Error(new IOException("Unable to get comments"));
        whenever(repository.getComments(ids)).thenReturn(repositoryResult);

        Result<List<ReplyWithReplies>> result = runBlocking(() -> useCase.invoke(ids));

        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void getComments_multipleReplies_whenRequestSuccessful() throws Exception {

        Result<List<ParentCommentResponse>> resultParent = Result.Success(Arrays.asList(parentCommentResponse));
        List<Long> parentIds = Arrays.asList(1L);
        whenever(repository.getComments(parentIds)).thenReturn(resultParent);

        List<Long> childrenIds = Arrays.asList(11L, 12L);
        Result<List<ReplyResponse>> resultChildren = Result.Success(
                Arrays.asList(
                        replyResponse1,
                        replyResponse2
                )
        );
        whenever(repository.getComments(childrenIds)).thenReturn(resultChildren);

        Result<List<ParentCommentWithReplies>> result = runBlocking(() -> useCase.invoke(Arrays.asList(1L)));

        verify(repository).getComments(parentIds);
        verify(repository).getComments(childrenIds);

        Assert.assertEquals(Result.Success(new ArrayList<>(Arrays.asList(parentCommentWithReplies))), result);
    }

    @Test
    public void getComments_multipleReplies_whenRepliesRequestFailed() throws Exception {

        Result<List<ParentCommentResponse>> resultParent = Result.Success(Arrays.asList(parentCommentResponse));
        List<Long> parentIds = Arrays.asList(1L);
        whenever(repository.getComments(parentIds)).thenReturn(resultParent);

        Result<List<ReplyResponse>> resultChildrenError = Result.Error(new IOException("Unable to get comments"));
        List<Long> childrenIds = Arrays.asList(11L, 12L);
        whenever(repository.getComments(childrenIds)).thenReturn(resultChildrenError);

        Result<List<ParentCommentWithReplies>> result = runBlocking(() -> useCase.invoke(Arrays.asList(1L)));

        verify(repository).getComments(parentIds);
        verify(repository).getComments(childrenIds);

        Assert.assertEquals(Result.Success(new ArrayList<>(Arrays.asList(parentCommentWithRepliesWithoutReplies))), result);
    }
}