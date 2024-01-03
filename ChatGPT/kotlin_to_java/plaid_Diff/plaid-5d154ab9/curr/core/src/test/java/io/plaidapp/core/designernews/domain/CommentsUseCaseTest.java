package io.plaidapp.core.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.users.UserRepository;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.core.designernews.domain.model.CommentWithReplies;
import io.plaidapp.core.designernews.parentComment;
import io.plaidapp.core.designernews.parentCommentWithReplies;
import io.plaidapp.core.designernews.parentCommentWithRepliesWithoutReplies;
import io.plaidapp.core.designernews.parentCommentWithoutReplies;
import io.plaidapp.core.designernews.reply1;
import io.plaidapp.core.designernews.reply1NoUser;
import io.plaidapp.core.designernews.replyWithReplies1;
import io.plaidapp.core.designernews.user1;
import io.plaidapp.core.designernews.user2;
import io.plaidapp.test.shared.provideFakeCoroutinesContextProvider;
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CommentsUseCaseTest {
    private CommentsWithRepliesUseCase commentsWithRepliesUseCase = Mockito.mock(CommentsWithRepliesUseCase.class);
    private UserRepository userRepository = Mockito.mock(UserRepository.class);
    private CommentsUseCase repository = new CommentsUseCase(
            commentsWithRepliesUseCase,
            userRepository,
            provideFakeCoroutinesContextProvider()
    );

    @Test
    public void getComments_noReplies_whenCommentsAnUserRequestsSuccessful() throws Exception {
        List<Long> ids = Arrays.asList(11L);
        withComment(replyWithReplies1, ids);

        withUsers(new HashSet<>(Arrays.asList(user1)), new HashSet<>(Arrays.asList(111L)));
        Result<List<Comment>> result = null;

        repository.getComments(ids, it -> result = it);

        Assert.assertEquals(Result.Success(Arrays.asList(reply1)), result);
    }

    @Test
    public void getComments_noReplies_whenCommentsRequestFailed() throws Exception {
        Result.Error resultError = new Result.Error(new IOException("Comment error"));
        List<Long> ids = Arrays.asList(11L);
        Mockito.when(commentsWithRepliesUseCase.getCommentsWithReplies(ids)).thenReturn(resultError);
        Result<List<Comment>> result = null;

        repository.getComments(ids, it -> result = it);

        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void getComments_multipleReplies_whenCommentsAndUsersRequestsSuccessful() throws Exception {
        List<Long> parentIds = Arrays.asList(1L);
        withComment(parentCommentWithReplies, parentIds);
        withUsers(new HashSet<>(Arrays.asList(user1, user2)), new HashSet<>(Arrays.asList(111L, 222L)));
        Result<List<Comment>> result = null;

        repository.getComments(Arrays.asList(1L), it -> result = it);

        Mockito.verify(commentsWithRepliesUseCase).getCommentsWithReplies(parentIds);

        Assert.assertEquals(Result.Success(Arrays.asList(parentComment)), result);
    }

    @Test
    public void getComments_multipleReplies_whenRepliesRequestFailed() throws Exception {
        List<Long> parentIds = Arrays.asList(1L);
        withComment(parentCommentWithRepliesWithoutReplies, parentIds);

        withUsers(new HashSet<>(Arrays.asList(user2)), new HashSet<>(Arrays.asList(222L)));
        Result<List<Comment>> result = null;

        repository.getComments(Arrays.asList(1L), it -> result = it);

        Mockito.verify(commentsWithRepliesUseCase).getCommentsWithReplies(parentIds);

        Assert.assertEquals(Result.Success(new ArrayList<>(Arrays.asList(parentCommentWithoutReplies))), result);
    }

    @Test
    public void getComments_whenUserRequestFailed() throws Exception {
        List<Long> ids = Arrays.asList(11L);
        withComment(replyWithReplies1, ids);

        Result.Error userError = new Result.Error(new IOException("User error"));
        Mockito.when(userRepository.getUsers(new HashSet<>(Arrays.asList(11L)))).thenReturn(userError);
        Result<List<Comment>> result = null;

        repository.getComments(Arrays.asList(11L), it -> result = it);

        Mockito.verify(commentsWithRepliesUseCase).getCommentsWithReplies(ids);

        Assert.assertEquals(Result.Success(new ArrayList<>(Arrays.asList(reply1NoUser))), result);
    }

    private void withUsers(Set<User> users, Set<Long> ids) throws Exception {
        Result<Set<User>> userResult = new Result.Success<>(users);
        Mockito.when(userRepository.getUsers(ids)).thenReturn(userResult);
    }

    private void withComment(CommentWithReplies comment, List<Long> ids) throws Exception {
        Result<List<CommentWithReplies>> resultParent = new Result.Success<>(Arrays.asList(comment));
        Mockito.when(commentsWithRepliesUseCase.getCommentsWithReplies(ids)).thenReturn(resultParent);
    }

    private void withComments(List<CommentWithReplies> comment, List<Long> ids) throws Exception {
        Result<List<CommentWithReplies>> resultParent = new Result.Success<>(comment);
        Mockito.when(commentsWithRepliesUseCase.getCommentsWithReplies(ids)).thenReturn(resultParent);
    }
}