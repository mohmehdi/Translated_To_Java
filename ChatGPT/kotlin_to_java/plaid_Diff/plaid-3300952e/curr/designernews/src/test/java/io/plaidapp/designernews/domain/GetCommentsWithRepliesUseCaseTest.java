package io.plaidapp.designernews.domain;

import com.nhaarman.mockito_kotlin.mock;
import com.nhaarman.mockito_kotlin.verify;
import com.nhaarman.mockito_kotlin.whenever;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.comments.CommentsRepository;
import io.plaidapp.designernews.parentCommentResponse;
import io.plaidapp.designernews.parentCommentWithReplies;
import io.plaidapp.designernews.parentCommentWithRepliesWithoutReplies;
import io.plaidapp.designernews.reply1;
import io.plaidapp.designernews.replyResponse1;
import io.plaidapp.designernews.replyResponse2;
import io.plaidapp.designernews.replyWithReplies1;
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GetCommentsWithRepliesUseCaseTest {

    private CommentsRepository repository = mock(CommentsRepository.class);
    private GetCommentsWithRepliesUseCase useCase = new GetCommentsWithRepliesUseCase(repository);

    @Test
    public void getComments_noReplies_whenRequestSuccessful() throws Exception {

        List<Long> ids = new ArrayList<>();
        ids.add(reply1.getId());
        Result<List<ReplyResponse>> repositoryResult = Result.Success(listOf(replyResponse1));
        whenever(repository.getComments(ids)).thenReturn(repositoryResult);

        Result<List<ReplyWithReplies>> result = useCase.invoke(ids);

        verify(repository).getComments(ids);

        Assert.assertEquals(Result.Success(listOf(replyWithReplies1)), result);
    }

    @Test
    public void getComments_noReplies_whenRequestFailed() throws Exception {

        List<Long> ids = new ArrayList<>();
        ids.add(11L);
        Result<List<ReplyResponse>> repositoryResult = Result.Error(new IOException("Unable to get comments"));
        whenever(repository.getComments(ids)).thenReturn(repositoryResult);

        Result<List<ReplyWithReplies>> result = useCase.invoke(ids);

        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void getComments_multipleReplies_whenRequestSuccessful() throws Exception {

        Result<List<ParentCommentResponse>> resultParent = Result.Success(listOf(parentCommentResponse));
        List<Long> parentIds = new ArrayList<>();
        parentIds.add(1L);
        whenever(repository.getComments(parentIds)).thenReturn(resultParent);

        List<Long> childrenIds = new ArrayList<>();
        childrenIds.add(11L);
        childrenIds.add(12L);
        Result<List<ReplyResponse>> resultChildren = Result.Success(
            listOf(
                replyResponse1,
                replyResponse2
            )
        );
        whenever(repository.getComments(childrenIds)).thenReturn(resultChildren);

        Result<List<ParentCommentWithReplies>> result = useCase.invoke(listOf(1L));

        verify(repository).getComments(parentIds);
        verify(repository).getComments(childrenIds);

        Assert.assertEquals(Result.Success(arrayListOf(parentCommentWithReplies)), result);
    }

    @Test
    public void getComments_multipleReplies_whenRepliesRequestFailed() throws Exception {

        Result<List<ParentCommentResponse>> resultParent = Result.Success(listOf(parentCommentResponse));
        List<Long> parentIds = new ArrayList<>();
        parentIds.add(1L);
        whenever(repository.getComments(parentIds)).thenReturn(resultParent);

        Result<List<ReplyResponse>> resultChildrenError = Result.Error(new IOException("Unable to get comments"));
        List<Long> childrenIds = new ArrayList<>();
        childrenIds.add(11L);
        childrenIds.add(12L);
        whenever(repository.getComments(childrenIds)).thenReturn(resultChildrenError);

        Result<List<ParentCommentWithReplies>> result = useCase.invoke(listOf(1L));

        verify(repository).getComments(parentIds);
        verify(repository).getComments(childrenIds);

        Assert.assertEquals(Result.Success(arrayListOf(parentCommentWithRepliesWithoutReplies)), result);
    }
}