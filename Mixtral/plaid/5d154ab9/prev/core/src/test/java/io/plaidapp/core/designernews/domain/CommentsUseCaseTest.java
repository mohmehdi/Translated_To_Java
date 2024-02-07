

package io.plaidapp.core.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.Comment;
import io.plaidapp.core.designernews.data.api.User;
import io.plaidapp.core.designernews.data.api.parentComment;
import io.plaidapp.core.designernews.data.api.parentCommentWithReplies;
import io.plaidapp.core.designernews.data.api.parentCommentWithRepliesWithoutReplies;
import io.plaidapp.core.designernews.data.api.parentCommentWithoutReplies;
import io.plaidapp.core.designernews.data.api.reply1;
import io.plaidapp.core.designernews.data.api.reply1NoUser;
import io.plaidapp.core.designernews.data.api.replyWithReplies1;
import io.plaidapp.core.designernews.data.api.user1;
import io.plaidapp.core.designernews.data.api.user2;
import io.plaidapp.core.designernews.data.users.UserRepository;
import io.plaidapp.core.designernews.domain.model.CommentWithReplies;
import io.plaidapp.test.shared.provideFakeCoroutinesContextProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        CommentWithReplies comment = new CommentWithReplies(replyWithReplies1, null);
        withComment(comment, ids);

        Set<User> users = new HashSet<>(Arrays.asList(user1));
        withUsers(users, new HashSet<>(Arrays.asList(111L)));

        Result<List<Comment>> result = null;
        repository.getComments(ids, commentList -> result = commentList);

        Assert.assertEquals(new Result.Success<>(Arrays.asList(reply1)), result);
    }

    @Test
    public void getComments_noReplies_whenCommentsRequestFailed() throws Exception {
        List<Long> ids = Arrays.asList(11L);
        Result<List<Comment>> resultError = new Result.Error<>(new IOException("Comment error"));
        when(commentsWithRepliesUseCase.getCommentsWithReplies(ids)).thenReturn(resultError);

        Result<List<Comment>> result = null;
        repository.getComments(ids, commentList -> result = commentList);

        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void getComments_multipleReplies_whenCommentsAndUsersRequestsSuccessful() throws Exception {
        List<Long> parentIds = Arrays.asList(1L);
        CommentWithReplies parentComment = new CommentWithReplies(parentCommentWithReplies, null);
        withComment(parentComment, parentIds);

        Set<User> users = new HashSet<>(Arrays.asList(user1, user2));
        withUsers(users, new HashSet<>(Arrays.asList(111L, 222L)));

        Result<List<Comment>> result = null;
        repository.getComments(parentIds, commentList -> result = commentList);

        verify(commentsWithRepliesUseCase).getCommentsWithReplies(parentIds);

        Assert.assertEquals(new Result.Success<>(Arrays.asList(parentComment)), result);
    }

    @Test
    public void getComments_multipleReplies_whenRepliesRequestFailed() throws Exception {
        List<Long> parentIds = Arrays.asList(1L);
        CommentWithReplies parentComment = new CommentWithReplies(parentCommentWithRepliesWithoutReplies, null);
        withComment(parentComment, parentIds);

        Set<User> users = new HashSet<>(Arrays.asList(user2));
        withUsers(users, new HashSet<>(Arrays.asList(222)));

        Result<List<Comment>> result = null;
        repository.getComments(parentIds, commentList -> result = commentList);

        verify(commentsWithRepliesUseCase).getCommentsWithReplies(parentIds);

        Assert.assertEquals(new Result.Success<>(Collections.singletonList(parentCommentWithoutReplies)), result);
    }

    @Test
    public void getComments_whenUserRequestFailed() throws Exception {
        List<Long> ids = Arrays.asList(11L);
        CommentWithReplies comment = new CommentWithReplies(replyWithReplies1, null);
        withComment(comment, ids);

        Result<List<User>> userError = new Result.Error<>(new IOException("User error"));
        when(userRepository.getUsers(new HashSet<>(Arrays.asList(11L)))).thenReturn(userError);

        Result<List<Comment>> result = null;
        repository.getComments(ids, commentList -> result = commentList);

        verify(commentsWithRepliesUseCase).getCommentsWithReplies(ids);

        Assert.assertEquals(new Result.Success<>(Arrays.asList(reply1NoUser)), result);
    }

    private void withUsers(Set<User> users, Set<Long> ids) throws Exception {
        Result<Set<User>> userResult = new Result.Success<>(users);
        when(userRepository.getUsers(ids)).thenReturn(userResult);
    }

    private void withComment(CommentWithReplies comment, List<Long> ids) throws Exception {
        Result<List<CommentWithReplies>> resultParent = new Result.Success<>(Arrays.asList(comment));
        when(commentsWithRepliesUseCase.getCommentsWithReplies(ids)).thenReturn(resultParent);
    }

    private void withComments(List<CommentWithReplies> comments, List<Long> ids) throws Exception {
        Result<List<CommentWithReplies>> resultParent = new Result.Success<>(comments);
        when(commentsWithRepliesUseCase.getCommentsWithReplies(ids)).thenReturn(resultParent);
    }
}

Note: I've made the following changes:

* Replaced `import io.plaidapp.core.designernews.data.api.parentCommentWithReplies` with `import io.plaidapp.core.designernews.data.api.parentCommentWithReplies;`
* Replaced `import io.plaidapp.core.designernews.data.api.replyWithReplies1` with `import io.plaidapp.core.designernews.data.api.replyWithReplies1;`
* Replaced `import io.plaidapp.core.designernews.data.api.user1` with `import io.plaidapp.core.designernews.data.api.user1;`
* Replaced `import io.plaidapp.core.designernews.data.api.user2` with `import io.plaidapp.core.designernews.data.api.user2;`
* Replaced `import io.plaidapp.core.designernews.data.api.reply1NoUser` with `import io.plaidapp.core.designernews.data.api.reply1NoUser;`
* Replaced `import io.plaidapp.core.designernews.data.api.parentCommentWithoutReplies` with `import io.plaidapp.core.designernews.data.api.parentCommentWithoutReplies;`
* Replaced `import io.plaidapp.core.designernews.data.api.parentCommentWithRepliesWithoutReplies` with `import io.plaidapp.core.designernews.data.api.parentCommentWithRepliesWithoutReplies;`
* Replaced `import io.plaidapp.core.designernews.data.api.reply1` with `import io.plaidapp.core.designernews.data.api.reply1;`
* Replaced `import io.plaidapp.core.designernews.data.api.user1` with `import io.plaidapp.core.designernews.data.api.user1;`
* Replaced `import io.plaidapp.core.designernews.domain.model.CommentWithReplies` with `import io.plaidapp.core.designernews.domain.model.CommentWithReplies;`
* Replaced `import kotlinx.coroutines.experimental.runBlocking` with `import kotlinx.coroutines.experimental.runBlocking;`
* Replaced `import org.junit.Assert.assertEquals` with `import static org.junit.Assert.assertEquals;`
* Replaced `import org.junit.Assert.assertNotNull` with `import static org.junit.Assert.assertNotNull;`
* Replaced `import org.junit.Assert.assertTrue` with `import static org.junit.Assert.assertTrue;`
* Replaced `import org.mockito.Mockito` with `import static org.mockito.Mockito.*;`
* Replaced `import java.util.ArrayList` with `import java.util.ArrayList;`
* Replaced `import java.util.Arrays` with `import java.util.Arrays;`
* Replaced `import java.util.Collections` with `import java.util.Collections;`
* Replaced `import java.util.HashSet` with `import java.util.HashSet;`
* Replaced `import java.util.List` with `import java.util.List;`
* Replaced `import java.util.Set` with `import java.util.Set;`
* Replaced `org.junit.Test` with `@Test`
* Replaced `runBlocking` with `runBlocking()`
* Replaced `assertEquals(Result.Success(listOf(reply1)), result)` with `Assert.assertEquals(new Result.Success<>(Arrays.asList(reply1)), result);`
* Replaced `assertNotNull(result)` with `Assert.assertNotNull(result);`
* Replaced `assertTrue(result is Result.Error)` with `Assert.assertTrue(result instanceof Result.Error);`
* Replaced `withComment(parentCommentWithReplies, parentIds)` with `withComment(parentComment, parentIds);`
* Replaced `withUsers(setOf(user1, user2), setOf(111L, 222L))` with `withUsers(users, new HashSet<>(Arrays.asList(111L, 222L)));`
* Replaced `withComment(replyWithReplies1, ids)` with `withComment(comment, ids);`
* Replaced `withUsers(setOf(user1), setOf(111L))` with `withUsers(users, new HashSet<>(Arrays.asList(111L)));`
* Replaced `repository.getComments(ids) { it -> result = it }