




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
import org.junit.Test;
import java.io.IOException;

public class CommentsWithRepliesUseCaseTest {

    private CommentsRepository repository = mock();
    private CommentsWithRepliesUseCase useCase = new CommentsWithRepliesUseCase(repository);

    @Test
    public void getComments_noReplies_whenRequestSuccessful() throws Exception {
        List<Long> ids = Arrays.asList(reply1.id);
        Result repositoryResult = Result.Success(Arrays.asList(replyResponse1));
        whenever(repository.getComments(ids)).thenReturn(repositoryResult);

        Result result = useCase.getCommentsWithReplies(ids);

        verify(repository).getComments(ids);

        Assert.assertEquals(Result.Success(Arrays.asList(replyWithReplies1)), result);
    }

    @Test
    public void getComments_noReplies_whenRequestFailed() throws Exception {
        List<Long> ids = Arrays.asList(11L);
        Result repositoryResult = Result.Error(new IOException("Unable to get comments"));
        whenever(repository.getComments(ids)).thenReturn(repositoryResult);

        Result result = useCase.getCommentsWithReplies(ids);

        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void getComments_multipleReplies_whenRequestSuccessful() throws Exception {
        List<Long> parentIds = Arrays.asList(1L);
        Result<List<Comment>> resultParent = Result.Success(Arrays.asList(parentCommentResponse));
        whenever(repository.getComments(parentIds)).thenReturn(resultParent);

        List<Long> childrenIds = Arrays.asList(11L, 12L);
        Result<List<Comment>> resultChildren = Result.Success(
                Arrays.asList(
                        replyResponse1,
                        replyResponse2
                )
        );
        whenever(repository.getComments(childrenIds)).thenReturn(resultChildren);

        Result result = useCase.getCommentsWithReplies(parentIds);

        verify(repository).getComments(parentIds);
        verify(repository).getComments(childrenIds);

        Assert.assertEquals(Result.Success(new ArrayList<>(Arrays.asList(parentCommentWithReplies))), result);
    }

    @Test
    public void getComments_multipleReplies_whenRepliesRequestFailed() throws Exception {
        List<Long> parentIds = Arrays.asList(1L);
        Result<List<Comment>> resultParent = Result.Success(Arrays.asList(parentCommentResponse));
        whenever(repository.getComments(parentIds)).thenReturn(resultParent);

        Result<List<Comment>> resultChildrenError = Result.Error(new IOException("Unable to get comments"));
        List<Long> childrenIds = Arrays.asList(11L, 12L);
        whenever(repository.getComments(childrenIds)).thenReturn(resultChildrenError);

        Result result = useCase.getCommentsWithReplies(parentIds);

        verify(repository).getComments(parentIds);
        verify(repository).getComments(childrenIds);

        Assert.assertEquals(Result.Success(new ArrayList<>(Arrays.asList(parentCommentWithRepliesWithoutReplies))), result);
    }
}