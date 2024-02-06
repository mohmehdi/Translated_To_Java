




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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetCommentsWithRepliesAndUsersUseCaseIntegrationTest {
    private DesignerNewsService service = mock(DesignerNewsService.class);
    private CommentsRemoteDataSource dataSource = new CommentsRemoteDataSource(service);
    private CommentsRepository commentsRepository = new CommentsRepository(dataSource);
    private UserRepository userRepository = new UserRepository(new UserRemoteDataSource(service));
    private provideCommentsWithRepliesAndUsersUseCase repository = provideCommentsWithRepliesAndUsersUseCase(
            provideCommentsWithRepliesUseCase(commentsRepository),
            userRepository
    );

    @Test
    public void getComments_noReplies_whenCommentsAnUserRequestsSuccessful() throws Exception {
        CompletableDeferred<Response<List<CommentResponse>>> resultParent = new CompletableDeferred<>();
        resultParent.complete(Response.success(Arrays.asList(replyResponse1)));
        when(service.getComments("11")).thenReturn(resultParent);

        CompletableDeferred<Response<List<User>>> resultUsers = new CompletableDeferred<>();
        resultUsers.complete(Response.success(Arrays.asList(user1)));
        when(service.getUsers("111")).thenReturn(resultUsers);

        Result<List<CommentResponse>> result = repository.invoke(Arrays.asList(11L));

        verify(service).getComments("11");

        assertEquals(Result.Success(Arrays.asList(reply1)), result);
    }

    @Test
    public void getComments_noReplies_whenCommentsRequestFailed() throws Exception {
        Response<List<CommentResponse>> apiResult = Response.error(400, errorResponseBody);
        CompletableDeferred<Response<List<CommentResponse>>> resultParent = new CompletableDeferred<>();
        resultParent.complete(apiResult);
        when(service.getComments("11")).thenReturn(resultParent);

        Result<List<CommentResponse>> result = repository.invoke(Arrays.asList(11L));

        assertNotNull(result);
        assertTrue(result instanceof Result.Error);
    }

    @Test
    public void getComments_multipleReplies_whenCommentsAndUsersRequestsSuccessful() throws Exception {
        CompletableDeferred<Response<List<CommentResponse>>> resultParent = new CompletableDeferred<>();
        resultParent.complete(Response.success(Arrays.asList(parentCommentResponse)));
        when(service.getComments("1")).thenReturn(resultParent);

        CompletableDeferred<Response<List<CommentResponse>>> resultChildren = new CompletableDeferred<>();
        resultChildren.complete(Response.success(repliesResponses));
        when(service.getComments("11,12")).thenReturn(resultChildren);

        CompletableDeferred<Response<List<User>>> resultUsers = new CompletableDeferred<>();
        resultUsers.complete(Response.success(Arrays.asList(user1, user2)));
        when(service.getUsers("222,111")).thenReturn(resultUsers);

        Result<List<CommentResponse>> result = repository.invoke(Arrays.asList(1L));

        verify(service).getComments("1");
        verify(service).getComments("11,12");
        verify(service).getUsers("222,111");

        assertEquals(Result.Success(flattendCommentsWithReplies), result);
    }

    @Test
    public void getComments_multipleReplies_whenRepliesRequestFailed() throws Exception {
        CompletableDeferred<Response<List<CommentResponse>>> resultParent = new CompletableDeferred<>();
        resultParent.complete(Response.success(Arrays.asList(parentCommentResponse)));
        when(service.getComments("1")).thenReturn(resultParent);

        CompletableDeferred<Response<List<CommentResponse>>> resultChildren = new CompletableDeferred<>();
        Response<List<CommentResponse>> resultChildrenError = Response.error(400, errorResponseBody);
        resultChildren.complete(resultChildrenError);
        when(service.getComments("11,12")).thenReturn(resultChildren);

        CompletableDeferred<Response<List<User>>> resultUsers = new CompletableDeferred<>();
        resultUsers.complete(Response.success(Arrays.asList(user2)));
        when(service.getUsers("222")).thenReturn(resultUsers);

        Result<List<CommentResponse>> result = repository.invoke(Arrays.asList(1L));

        verify(service).getComments("1");
        verify(service).getComments("11,12");
        verify(service).getUsers("222");

        assertEquals(Result.Success(flattenedCommentsWithoutReplies), result);
    }

    @Test
    public void getComments_whenUserRequestFailed() throws Exception {
        CompletableDeferred<Response<List<CommentResponse>>> resultParent = new CompletableDeferred<>();
        resultParent.complete(Response.success(Arrays.asList(replyResponse1)));
        when(service.getComments("11")).thenReturn(resultParent);

        CompletableDeferred<Response<List<User>>> resultUsers = new CompletableDeferred<>();
        Response<List<User>> userError = Response.error(400, errorResponseBody);
        resultUsers.complete(userError);
        when(service.getUsers("111")).thenReturn(resultUsers);

        Result<List<CommentResponse>> result = repository.invoke(Arrays.asList(11L));

        verify(service).getComments("11");
        verify(service).getUsers("111");

        assertEquals(Result.Success(Arrays.asList(reply1NoUser)), result);
    }

    private void withUsers(List<User> users, String ids) throws Exception {
        CompletableDeferred<Response<List<User>>> resultUsers = new CompletableDeferred<>();
        resultUsers.complete(Response.success(users));
        when(service.getUsers(ids)).thenReturn(resultUsers);
    }

    private void withComments(CommentResponse commentResponse, String ids) {
        CompletableDeferred<Response<List<CommentResponse>>> resultParent = new CompletableDeferred<>();
        List<CommentResponse> commentResponseList = new ArrayList<>();
        commentResponseList.add(commentResponse);
        resultParent.complete(Response.success(commentResponseList));
        when(service.getComments(ids)).thenReturn(resultParent);
    }

    private void withComments(List<CommentResponse> commentResponse, String ids) {
        CompletableDeferred<Response<List<CommentResponse>>> resultParent = new CompletableDeferred<>();
        resultParent.complete(Response.success(commentResponse));
        when(service.getComments(ids)).thenReturn(resultParent);
    }
}