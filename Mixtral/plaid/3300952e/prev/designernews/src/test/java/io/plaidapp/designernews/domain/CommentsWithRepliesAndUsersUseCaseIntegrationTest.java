




package io.plaidapp.designernews.domain;

import com.nhaarman.mockito_kotlin.mock;
import com.nhaarman.mockito_kotlin.verify;
import com.nhaarman.mockito_kotlin.whenever;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.comments.CommentsRemoteDataSource;
import io.plaidapp.core.designernews.data.comments.CommentsRepository;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.designernews.data.users.UserRemoteDataSource;
import io.plaidapp.designernews.data.users.UserRepository;
import io.plaidapp.designernews.errorResponseBody;
import io.plaidapp.designernews.flattendCommentsWithReplies;
import io.plaidapp.designernews.flattenedCommentsWithoutReplies;
import io.plaidapp.designernews.parentCommentResponse;
import io.plaidapp.designernews.provideCommentsWithRepliesAndUsersUseCase;
import io.plaidapp.designernews.provideCommentsWithRepliesUseCase;
import io.plaidapp.designernews.repliesResponses;
import io.plaidapp.designernews.reply1;
import io.plaidapp.designernews.reply1NoUser;
import io.plaidapp.designernews.replyResponse1;
import io.plaidapp.designernews.user1;
import io.plaidapp.designernews.user2;
import kotlinx.coroutines.experimental.CompletableDeferred;
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert;
import org.junit.Assert.assertEquals;
import org.junit.Assert.assertNotNull;
import org.junit.Assert.assertTrue;
import org.junit.Test;
import retrofit2.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CommentsWithRepliesAndUsersUseCaseIntegrationTest {
    private DesignerNewsService service = mock();
    private CommentsRemoteDataSource dataSource = new CommentsRemoteDataSource(service);
    private CommentsRepository commentsRepository = new CommentsRepository(dataSource);
    private UserRepository userRepository = new UserRepository(new UserRemoteDataSource(service));
    private provideCommentsWithRepliesAndUsersUseCase repository = provideCommentsWithRepliesAndUsersUseCase(
            provideCommentsWithRepliesUseCase(commentsRepository),
            userRepository
    );

    @Test
    public void getComments_noReplies_whenCommentsAnUserRequestsSuccessful() throws ExecutionException, InterruptedException {
        withComments(replyResponse1, "11");
        withUsers(Arrays.asList(user1), "111");

        Result<List<CommentResponse>> result = repository.invoke(Arrays.asList(11L)).get();

        verify(service).getComments("11");
        assertEquals(Result.Success(Arrays.asList(reply1)), result);
    }

    @Test
    public void getComments_noReplies_whenCommentsRequestFailed() throws ExecutionException, InterruptedException {
        Response<List<CommentResponse>> apiResult = Response.error(400, errorResponseBody);
        when(service.getComments(anyString())).thenReturn(CompletableDeferred.completedFuture(apiResult));

        Result<List<CommentResponse>> result = repository.invoke(Arrays.asList(11L)).get();

        assertNotNull(result);
        assertTrue(result instanceof Result.Error);
    }

    @Test
    public void getComments_multipleReplies_whenCommentsAndUsersRequestsSuccessful() throws ExecutionException, InterruptedException {
        withComments(parentCommentResponse, "1");
        withComments(repliesResponses, "11,12");
        withUsers(Arrays.asList(user1, user2), "222,111");

        Result<List<CommentResponse>> result = repository.invoke(Arrays.asList(1L)).get();

        verify(service).getComments("1");
        verify(service).getComments("11,12");
        verify(service).getUsers("222,111");
        assertEquals(Result.Success(flattendCommentsWithReplies), result);
    }

    @Test
    public void getComments_multipleReplies_whenRepliesRequestFailed() throws ExecutionException, InterruptedException {
        withComments(parentCommentResponse, "1");

        Response<List<CommentResponse>> resultChildrenError = Response.error(400, errorResponseBody);
        when(service.getComments(anyString()))
                .thenReturn(CompletableDeferred.completedFuture(resultChildrenError));

        withUsers(Arrays.asList(user2), "222");

        Result<List<CommentResponse>> result = repository.invoke(Arrays.asList(1L)).get();

        verify(service).getComments("1");
        verify(service).getComments("11,12");
        verify(service).getUsers("222");
        assertEquals(Result.Success(flattenedCommentsWithoutReplies), result);
    }

    @Test
    public void getComments_whenUserRequestFailed() throws ExecutionException, InterruptedException {
        withComments(replyResponse1, "11");

        Response<List<User>> userError = Response.error(400, errorResponseBody);
        when(service.getUsers(anyString()))
                .thenReturn(CompletableDeferred.completedFuture(userError));

        Result<List<CommentResponse>> result = repository.invoke(Arrays.asList(11L)).get();

        verify(service).getComments("11");
        verify(service).getUsers("111");
        assertEquals(Result.Success(new ArrayList<>(Arrays.asList(reply1NoUser))), result);
    }

    private void withUsers(List<User> users, String ids) throws ExecutionException, InterruptedException {
        Response<List<User>> userResult = Response.success(users);
        when(service.getUsers(ids))
                .thenReturn(CompletableDeferred.completedFuture(userResult));
    }

    private void withComments(CommentResponse commentResponse, String ids) throws ExecutionException, InterruptedException {
        Response<List<CommentResponse>> resultParent = Response.success(Arrays.asList(commentResponse));
        when(service.getComments(ids))
                .thenReturn(CompletableDeferred.completedFuture(resultParent));
    }

    private void withComments(List<CommentResponse> commentResponse, String ids) throws ExecutionException, InterruptedException {
        Response<List<CommentResponse>> resultParent = Response.success(commentResponse);
        when(service.getComments(ids))
                .thenReturn(CompletableDeferred.completedFuture(resultParent));
    }
}