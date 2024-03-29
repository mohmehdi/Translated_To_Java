




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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GetCommentsWithRepliesAndUsersUseCaseTest {
    private GetCommentsWithRepliesUseCase getCommentsWithReplies = mock(GetCommentsWithRepliesUseCase.class);
    private UserRepository userRepository = mock(UserRepository.class);
    private GetCommentsWithRepliesAndUsersUseCase repository = new GetCommentsWithRepliesAndUsersUseCase(
            getCommentsWithReplies,
            userRepository
    );

    @Test
    public void getComments_noReplies_whenCommentsAnUserRequestsSuccessful() throws Exception {
        List<Long> ids = Arrays.asList(11L);
        withComment(replyWithReplies1, ids);

        withUsers(Arrays.asList(user1), Arrays.asList(111L));

        Result<List<CommentWithReplies>> result = repository.invoke(ids);

        Assert.assertEquals(Result.Success(Arrays.asList(reply1)), result);
    }

    @Test
    public void getComments_noReplies_whenCommentsRequestFailed() throws Exception {
        Result<List<CommentWithReplies>> resultError = Result.Error(new IOException("Comment error"));
        List<Long> ids = Arrays.asList(11L);
        whenever(getCommentsWithReplies.invoke(ids)).thenReturn(resultError);

        Result<List<CommentWithReplies>> result = repository.invoke(ids);

        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void getComments_multipleReplies_whenCommentsAndUsersRequestsSuccessful() throws Exception {
        List<Long> parentIds = Arrays.asList(1L);
        withComment(parentCommentWithReplies, parentIds);
        withUsers(Arrays.asList(user1, user2), Arrays.asList(111L, 222L));

        Result<List<CommentWithReplies>> result = repository.invoke(Arrays.asList(1L));

        verify(getCommentsWithReplies).invoke(parentIds);

        Assert.assertEquals(Result.Success(flattendCommentsWithReplies), result);
    }

    @Test
    public void getComments_multipleReplies_whenRepliesRequestFailed() throws Exception {
        List<Long> parentIds = Arrays.asList(1L);
        withComment(parentCommentWithRepliesWithoutReplies, parentIds);

        withUsers(Arrays.asList(user2), Arrays.asList(222));

        Result<List<CommentWithReplies>> result = repository.invoke(Arrays.asList(1L));

        verify(getCommentsWithReplies).invoke(parentIds);

        Assert.assertEquals(Result.Success(flattenedCommentsWithoutReplies), result);
    }

    @Test
    public void getComments_whenUserRequestFailed() throws Exception {
        List<Long> ids = Arrays.asList(11L);
        withComment(replyWithReplies1, ids);

        Result<List<User>> userError = Result.Error(new IOException("User error"));
        whenever(userRepository.getUsers(Arrays.asList(11L))).thenReturn(userError);

        Result<List<CommentWithReplies>> result = repository.invoke(Arrays.asList(11L));

        verify(getCommentsWithReplies).invoke(ids);

        Assert.assertEquals(Result.Success(Arrays.asList(reply1NoUser)), result);
    }

    private void withUsers(Set<User> users, Set<Long> ids) throws Exception {
        Result<Set<User>> userResult = Result.Success(users.stream().collect(Collectors.toSet()));
        whenever(userRepository.getUsers(ids.stream().collect(Collectors.toSet()))).thenReturn(userResult);
    }

    private void withComment(CommentWithReplies comment, List<Long> ids) throws Exception {
        List<CommentWithReplies> comments = Arrays.asList(comment);
        Result<List<CommentWithReplies>> resultParent = Result.Success(comments);
        whenever(getCommentsWithReplies.invoke(ids)).thenReturn(resultParent);
    }
}