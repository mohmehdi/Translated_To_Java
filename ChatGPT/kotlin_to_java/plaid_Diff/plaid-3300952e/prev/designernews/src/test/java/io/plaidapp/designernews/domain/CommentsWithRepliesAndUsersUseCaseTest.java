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
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CommentsWithRepliesAndUsersUseCaseTest {
    private CommentsWithRepliesUseCase commentsWithRepliesUseCase = mock(CommentsWithRepliesUseCase.class);
    private UserRepository userRepository = mock(UserRepository.class);
    private CommentsWithRepliesAndUsersUseCase repository = new CommentsWithRepliesAndUsersUseCase(
            commentsWithRepliesUseCase,
            userRepository
    );

    @Test
    public void getComments_noReplies_whenCommentsAnUserRequestsSuccessful() throws Exception {

        List<Long> ids = Arrays.asList(11L);
        withComment(replyWithReplies1, ids);

        withUsers(new HashSet<>(Arrays.asList(user1)), new HashSet<>(Arrays.asList(111L)));


        Result<List<CommentWithReplies>> result = repository.getComments(ids);


        Assert.assertEquals(Result.Success(Arrays.asList(reply1)), result);
    }

    @Test
    public void getComments_noReplies_whenCommentsRequestFailed() throws Exception {

        Result.Error resultError = new Result.Error(new IOException("Comment error"));
        List<Long> ids = Arrays.asList(11L);
        whenever(commentsWithRepliesUseCase.getCommentsWithReplies(ids)).thenReturn(resultError);


        Result<List<CommentWithReplies>> result = repository.getComments(ids);


        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void getComments_multipleReplies_whenCommentsAndUsersRequestsSuccessful() throws Exception {



        List<Long> parentIds = Arrays.asList(1L);
        withComment(parentCommentWithReplies, parentIds);
        withUsers(new HashSet<>(Arrays.asList(user1, user2)), new HashSet<>(Arrays.asList(111L, 222L)));


        Result<List<CommentWithReplies>> result = repository.getComments(Arrays.asList(1L));


        verify(commentsWithRepliesUseCase).getCommentsWithReplies(parentIds);

        Assert.assertEquals(Result.Success(flattendCommentsWithReplies), result);
    }

    @Test
    public void getComments_multipleReplies_whenRepliesRequestFailed() throws Exception {

        List<Long> parentIds = Arrays.asList(1L);
        withComment(parentCommentWithRepliesWithoutReplies, parentIds);

        withUsers(new HashSet<>(Arrays.asList(user2)), new HashSet<>(Arrays.asList(222)));


        Result<List<CommentWithReplies>> result = repository.getComments(Arrays.asList(1L));


        verify(commentsWithRepliesUseCase).getCommentsWithReplies(parentIds);

        Assert.assertEquals(Result.Success(flattenedCommentsWithoutReplies), result);
    }

    @Test
    public void getComments_whenUserRequestFailed() throws Exception {



        List<Long> ids = Arrays.asList(11L);
        withComment(replyWithReplies1, ids);

        Result.Error userError = new Result.Error(new IOException("User error"));
        whenever(userRepository.getUsers(new HashSet<>(Arrays.asList(11L)))).thenReturn(userError);


        Result<List<CommentWithReplies>> result = repository.getComments(Arrays.asList(11L));


        verify(commentsWithRepliesUseCase).getCommentsWithReplies(ids);

        List<CommentWithReplies> expectedResult = new ArrayList<>();
        expectedResult.add(reply1NoUser);
        Assert.assertEquals(Result.Success(expectedResult), result);
    }


    private void withUsers(Set<User> users, Set<Long> ids) throws Exception {
        Result<Set<User>> userResult = new Result.Success<>(users);
        whenever(userRepository.getUsers(ids)).thenReturn(userResult);
    }

    private void withComment(CommentWithReplies comment, List<Long> ids) throws Exception {
        Result<List<CommentWithReplies>> resultParent = new Result.Success<>(Arrays.asList(comment));
        whenever(commentsWithRepliesUseCase.getCommentsWithReplies(ids)).thenReturn(resultParent);
    }
}