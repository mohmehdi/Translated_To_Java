package io.plaidapp.core.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.CommentsRepository;
import io.plaidapp.core.designernews.parentCommentResponse;
import io.plaidapp.core.designernews.parentCommentWithReplies;
import io.plaidapp.core.designernews.parentCommentWithRepliesWithoutReplies;
import io.plaidapp.core.designernews.reply1;
import io.plaidapp.core.designernews.replyResponse1;
import io.plaidapp.core.designernews.replyResponse2;
import io.plaidapp.core.designernews.replyWithReplies1;
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert.assertEquals;
import org.junit.Assert.assertNotNull;
import org.junit.Assert.assertTrue;
import org.junit.Test;
import org.mockito.Mockito;
import java.io.IOException;

public class CommentsWithRepliesUseCaseTest {

    private CommentsRepository repository = Mockito.mock(CommentsRepository.class);
    private CommentsWithRepliesUseCase useCase = new CommentsWithRepliesUseCase(repository);

    @Test
    public void getComments_noReplies_whenRequestSuccessful() throws Exception {
        
        List<Long> ids = Arrays.asList(reply1.getId());
        Result<List<ReplyResponse>> repositoryResult = Result.Success(Arrays.asList(replyResponse1));
        Mockito.when(repository.getComments(ids)).thenReturn(repositoryResult);

        
        Result<List<ReplyWithReplies>> result = useCase.getCommentsWithReplies(ids);

        
        Mockito.verify(repository).getComments(ids);
        
        assertEquals(Result.Success(Arrays.asList(replyWithReplies1)), result);
    }

    @Test
    public void getComments_noReplies_whenRequestFailed() throws Exception {
        
        List<Long> ids = Arrays.asList(11L);
        Result<List<ReplyResponse>> repositoryResult = Result.Error(new IOException("Unable to get comments"));
        Mockito.when(repository.getComments(ids)).thenReturn(repositoryResult);

        
        Result<List<ReplyWithReplies>> result = useCase.getCommentsWithReplies(ids);

        
        assertNotNull(result);
        assertTrue(result instanceof Result.Error);
    }

    @Test
    public void getComments_multipleReplies_whenRequestSuccessful() throws Exception {
        
        
        
        Result<List<ParentCommentResponse>> resultParent = Result.Success(Arrays.asList(parentCommentResponse));
        List<Long> parentIds = Arrays.asList(1L);
        Mockito.when(repository.getComments(parentIds)).thenReturn(resultParent);
        
        List<Long> childrenIds = Arrays.asList(11L, 12L);
        Result<List<ReplyResponse>> resultChildren = Result.Success(Arrays.asList(
            replyResponse1,
            replyResponse2
        ));
        Mockito.when(repository.getComments(childrenIds)).thenReturn(resultChildren);

        
        Result<List<ParentCommentWithReplies>> result = useCase.getCommentsWithReplies(Arrays.asList(1L));

        
        Mockito.verify(repository).getComments(parentIds);
        Mockito.verify(repository).getComments(childrenIds);
        
        assertEquals(Result.Success(new ArrayList<>(Arrays.asList(parentCommentWithReplies))), result);
    }

    @Test
    public void getComments_multipleReplies_whenRepliesRequestFailed() throws Exception {
        
        
        Result<List<ParentCommentResponse>> resultParent = Result.Success(Arrays.asList(parentCommentResponse));
        List<Long> parentIds = Arrays.asList(1L);
        Mockito.when(repository.getComments(parentIds)).thenReturn(resultParent);
        
        Result<List<ReplyResponse>> resultChildrenError = Result.Error(new IOException("Unable to get comments"));
        List<Long> childrenIds = Arrays.asList(11L, 12L);
        Mockito.when(repository.getComments(childrenIds)).thenReturn(resultChildrenError);

        
        Result<List<ParentCommentWithReplies>> result = useCase.getCommentsWithReplies(Arrays.asList(1L));

        
        Mockito.verify(repository).getComments(parentIds);
        Mockito.verify(repository).getComments(childrenIds);
        
        assertEquals(Result.Success(new ArrayList<>(Arrays.asList(parentCommentWithRepliesWithoutReplies))), result);
    }
}