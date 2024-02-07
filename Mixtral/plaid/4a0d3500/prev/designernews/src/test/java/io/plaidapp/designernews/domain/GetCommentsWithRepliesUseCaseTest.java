package io.plaidapp.designernews.domain;

import com.nhaarman.mockitokotlin2.Mockito;
import com.nhaarman.mockitokotlin2.verify;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.comments.CommentsRepository;
import io.plaidapp.designernews.parentCommentResponse;
import io.plaidapp.designernews.parentCommentWithReplies;
import io.plaidapp.designernews.parentCommentWithRepliesWithoutReplies;
import io.plaidapp.designernews.reply1;
import io.plaidapp.designernews.replyResponse1;
import io.plaidapp.designernews.replyResponse2;
import io.plaidapp.designernews.replyWithReplies1;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.mockito.Mockito.when;

public class GetCommentsWithRepliesUseCaseTest {

    private CommentsRepository repository = Mockito.mock(CommentsRepository.class);
    private GetCommentsWithRepliesUseCase useCase = new GetCommentsWithRepliesUseCase(repository);

    @Test
    public void getComments_noReplies_whenRequestSuccessful() throws Exception {
        List<Long> ids = Arrays.asList(reply1.id);
        Result<List<io.plaidapp.designernews.data.Reply>> repositoryResult = Result.success(Arrays.asList(replyResponse1));
        when(repository.getComments(ids)).thenReturn(repositoryResult);

        Result<List<io.plaidapp.designernews.data.ReplyWithReplies>> result = useCase.getComments(ids);

        verify(repository).getComments(ids);

        Assert.assertEquals(Result.success(Arrays.asList(replyWithReplies1)), result);
    }

    @Test
    public void getComments_noReplies_whenRequestFailed() throws Exception {
        List<Long> ids = Arrays.asList(11L);
        Result<List<io.plaidapp.designernews.data.Reply>> repositoryResult = Result.error(new IOException("Unable to get comments"));
        when(repository.getComments(ids)).thenReturn(repositoryResult);

        Result<List<io.plaidapp.designernews.data.ReplyWithReplies>> result = useCase.getComments(ids);

        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void getComments_multipleReplies_whenRequestSuccessful() throws Exception {
        List<Long> parentIds = Arrays.asList(1L);
        Result<List<io.plaidapp.designernews.data.ParentComment>> resultParent = Result.success(Arrays.asList(parentCommentResponse));
        when(repository.getComments(parentIds)).thenReturn(resultParent);

        List<Long> childrenIds = Arrays.asList(11L, 12L);
        Result<List<io.plaidapp.designernews.data.Reply>> resultChildren = Result.success(Arrays.asList(replyResponse1, replyResponse2));
        when(repository.getComments(childrenIds)).thenReturn(resultChildren);

        Result<List<io.plaidapp.designernews.data.ReplyWithReplies>> result = useCase.getComments(parentIds);

        verify(repository).getComments(parentIds);
        verify(repository).getComments(childrenIds);

        Assert.assertEquals(Result.success(new ArrayList<>(Arrays.asList(parentCommentWithReplies))), result);
    }

    @Test
    public void getComments_multipleReplies_whenRepliesRequestFailed() throws Exception {
        List<Long> parentIds = Arrays.asList(1L);
        Result<List<io.plaidapp.designernews.data.ParentComment>> resultParent = Result.success(Arrays.asList(parentCommentResponse));
        when(repository.getComments(parentIds)).thenReturn(resultParent);

        List<Long> childrenIds = Arrays.asList(11L, 12L);
        Result<List<io.plaidapp.designernews.data.Reply>> resultChildrenError = Result.error(new IOException("Unable to get comments"));
        when(repository.getComments(childrenIds)).thenReturn(resultChildrenError);

        Result<List<io.plaidapp.designernews.data.ReplyWithReplies>> result = useCase.getComments(parentIds);

        verify(repository).getComments(parentIds);
        verify(repository).getComments(childrenIds);

        Assert.assertEquals(Result.success(new ArrayList<>(Arrays.asList(parentCommentWithRepliesWithoutReplies))), result);
    }
}