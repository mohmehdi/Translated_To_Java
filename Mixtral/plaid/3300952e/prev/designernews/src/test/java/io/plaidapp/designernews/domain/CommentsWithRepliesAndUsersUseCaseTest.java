



package io.plaidapp.designernews.domain;

import com.nhaarman.mockito_kotlin.mock;
import com.nhaarman.mockito_kotlin.verify;
import com.nhaarman.mockito_kotlin.whenever;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.core.designernews.domain.model.CommentWithReplies;
import io.plaidapp.designernews.data.users.UserRepository;
import io.plaidapp.designernews.flattendCommentsWithReplies;
import io.plaidapp.designernews.flattenedCommentsWithoutReplies;
import io.plaidapp.designernews.parentCommentWithReplies;
import io.plaidapp.designernews.parentCommentWithRepliesWithoutReplies;
import io.plaidapp.designernews.reply1;
import io.plaidapp.designernews.reply1NoUser;
import io.plaidapp.designernews.replyWithReplies1;
import io.plaidapp.designernews.user1;
import io.plaidapp.designernews.user2;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CommentsWithRepliesAndUsersUseCaseTest {

    @Mock
    private CommentsWithRepliesUseCase commentsWithRepliesUseCase;

    @Mock
    private UserRepository userRepository;

    private CommentsWithRepliesAndUsersUseCase repository;



    @Test
    public void getComments_noReplies_whenCommentsAnUserRequestsSuccessful() throws Exception {
        List<Long> ids = Arrays.asList(11L);
        withComment(replyWithReplies1, ids);

        withUsers(setOf(user1), setOf(111L));

        Result result = repository.getComments(ids);

        assertEquals(Result.Success(Arrays.asList(reply1)), result);
    }

    @Test
    public void getComments_noReplies_whenCommentsRequestFailed() throws Exception {
        Result<List<CommentWithReplies>> resultError = Result.Error(new IOException("Comment error"));
        List<Long> ids = Arrays.asList(11L);
        whenever(commentsWithRepliesUseCase.getCommentsWithReplies(ids)).thenReturn(resultError);

        Result result = repository.getComments(ids);

        assertNotNull(result);
        assertTrue(result instanceof Result.Error);
    }

    @Test
    public void getComments_multipleReplies_whenCommentsAndUsersRequestsSuccessful() throws Exception {
        List<Long> parentIds = Arrays.asList(1L);
        withComment(parentCommentWithReplies, parentIds);
        withUsers(setOf(user1, user2), setOf(111L, 222L));

        Result result = repository.getComments(Arrays.asList(1L));

        verify(commentsWithRepliesUseCase).getCommentsWithReplies(parentIds);

        assertEquals(Result.Success(flattendCommentsWithReplies), result);
    }

    @Test
    public void getComments_multipleReplies_whenRepliesRequestFailed() throws Exception {
        List<Long> parentIds = Arrays.asList(1L);
        withComment(parentCommentWithRepliesWithoutReplies, parentIds);

        withUsers(setOf(user2), setOf(222));

        Result result = repository.getComments(Arrays.asList(1L));

        verify(commentsWithRepliesUseCase).getCommentsWithReplies(parentIds);

        assertEquals(Result.Success(flattenedCommentsWithoutReplies), result);
    }

    @Test
    public void getComments_whenUserRequestFailed() throws Exception {
        List<Long> ids = Arrays.asList(11L);
        withComment(replyWithReplies1, ids);

        Result<Set<User>> userError = Result.Error(new IOException("User error"));
        whenever(userRepository.getUsers(setOf(11L))).thenReturn(userError);

        Result result = repository.getComments(Arrays.asList(11L));

        verify(commentsWithRepliesUseCase).getCommentsWithReplies(ids);

        assertEquals(Result.Success(Arrays.asList(reply1NoUser)), result);
    }

    private void withUsers(Set<User> users, Set<Long> ids) throws Exception {
        Result<Set<User>> userResult = Result.Success(users);
        whenever(userRepository.getUsers(ids)).thenReturn(userResult);
    }

    private void withComment(CommentWithReplies comment, List<Long> ids) throws Exception {
        List<CommentWithReplies> resultParent = Arrays.asList(comment);
        whenever(commentsWithRepliesUseCase.getCommentsWithReplies(ids)).thenReturn(Result.Success(resultParent));
    }
}