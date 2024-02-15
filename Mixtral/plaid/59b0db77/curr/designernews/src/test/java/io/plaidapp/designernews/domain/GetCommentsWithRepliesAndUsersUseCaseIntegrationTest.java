

package io.plaidapp.designernews.domain;

import com.nhaarman.mockitokotlin2.Mockito;
import com.nhaarman.mockitokotlin2.verify;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.designernews.data.comments.CommentsRemoteDataSource;
import io.plaidapp.designernews.data.comments.CommentsRepository;
import io.plaidapp.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.designernews.data.api.DNService;
import io.plaidapp.designernews.data.users.UserRemoteDataSource;
import io.plaidapp.designernews.data.users.UserRepository;
import io.plaidapp.designernews.errorResponseBody;
import io.plaidapp.designernews.flattendCommentsWithReplies;
import io.plaidapp.designernews.flattenedCommentsWithoutReplies;
import io.plaidapp.designernews.parentCommentResponse;
import io.plaidapp.designernews.repliesResponses;
import io.plaidapp.designernews.reply1;
import io.plaidapp.designernews.reply1NoUser;
import io.plaidapp.designernews.replyResponse1;
import io.plaidapp.designernews.user1;
import io.plaidapp.designernews.user2;
import kotlinx.coroutines.CompletableDeferred;
import kotlinx.coroutines.Runnable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetCommentsWithRepliesAndUsersUseCaseIntegrationTest {
    private DNService service;
    private DesignerNewsService designerNewsService;
    private CommentsRemoteDataSource dataSource;
    private CommentsRepository commentsRepository;
    private UserRepository userRepository;
    private GetCommentsWithRepliesAndUsersUseCase repository;

    @Before
    public void setup() {
        service = mock(DNService.class);
        designerNewsService = mock(DesignerNewsService.class);
        dataSource = new CommentsRemoteDataSource(service);
        commentsRepository = new CommentsRepository(dataSource);
        userRepository = new UserRepository(new UserRemoteDataSource(designerNewsService));
        repository = new GetCommentsWithRepliesAndUsersUseCase(
                new GetCommentsWithRepliesUseCase(commentsRepository),
                userRepository
        );
    }

    @Test
    public void getComments_noReplies_whenCommentsAnUserRequestsSuccessful() throws ExecutionException, InterruptedException {
        CompletableDeferred<Response<List<CommentResponse>>> deferredReply = new CompletableDeferred<>();
        deferredReply.complete(Response.success(Arrays.asList(replyResponse1)));
        when(service.getComments(anyString())).thenReturn(deferredReply);

        CompletableDeferred<Response<List<User>>> deferredUsers = new CompletableDeferred<>();
        deferredUsers.complete(Response.success(Arrays.asList(user1)));
        when(designerNewsService.getUsers(anyString())).thenReturn(deferredUsers);

        Result<List<CommentResponse>> result = repository.getComments(Arrays.asList(11L)).get();

        verify(service).getComments("11");

        Assert.assertEquals(Result.Success(Arrays.asList(reply1)), result);
    }

    @Test
    public void getComments_noReplies_whenCommentsRequestFailed() throws ExecutionException, InterruptedException {
        Response<List<CommentResponse>> apiResult = Response.error(400, errorResponseBody);
        CompletableDeferred<Response<List<CommentResponse>>> deferredReply = new CompletableDeferred<>();
        deferredReply.complete(apiResult);
        when(service.getComments(anyString())).thenReturn(deferredReply);

        Result<List<CommentResponse>> result = repository.getComments(Arrays.asList(11L)).get();

        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void getComments_multipleReplies_whenCommentsAndUsersRequestsSuccessful() throws ExecutionException, InterruptedException {
        CompletableDeferred<Response<List<CommentResponse>>> deferredParent = new CompletableDeferred<>();
        deferredParent.complete(Response.success(Arrays.asList(parentCommentResponse)));
        when(service.getComments(anyString())).thenReturn(deferredParent);

        CompletableDeferred<Response<List<CommentResponse>>> deferredChildren = new CompletableDeferred<>();
        deferredChildren.complete(Response.success(repliesResponses));
        when(service.getComments(anyString())).thenReturn(deferredChildren);

        CompletableDeferred<Response<List<User>>> deferredUsers = new CompletableDeferred<>();
        deferredUsers.complete(Response.success(Arrays.asList(user1, user2)));
        when(designerNewsService.getUsers(anyString())).thenReturn(deferredUsers);

        Result<List<CommentResponse>> result = repository.getComments(Arrays.asList(1L)).get();

        verify(service).getComments("1");
        verify(service).getComments("11,12");
        verify(designerNewsService).getUsers("222,111");

        Assert.assertEquals(Result.Success(flattendCommentsWithReplies), result);
    }

    @Test
    public void getComments_multipleReplies_whenRepliesRequestFailed() throws ExecutionException, InterruptedException {
        CompletableDeferred<Response<List<CommentResponse>>> deferredParent = new CompletableDeferred<>();
        deferredParent.complete(Response.success(Arrays.asList(parentCommentResponse)));
        when(service.getComments(anyString())).thenReturn(deferredParent);

        CompletableDeferred<Response<List<CommentResponse>>> deferredChildren = new CompletableDeferred<>();
        deferredChildren.complete(Response.error(400, errorResponseBody));
        when(service.getComments(anyString())).thenReturn(deferredChildren);

        CompletableDeferred<Response<List<User>>> deferredUsers = new CompletableDeferred<>();
        deferredUsers.complete(Response.success(Arrays.asList(user2)));
        when(designerNewsService.getUsers(anyString())).thenReturn(deferredUsers);

        Result<List<CommentResponse>> result = repository.getComments(Arrays.asList(1L)).get();

        verify(service).getComments("1");
        verify(service).getComments("11,12");
        verify(designerNewsService).getUsers("222");

        Assert.assertEquals(Result.Success(flattenedCommentsWithoutReplies), result);
    }

    @Test
    public void getComments_whenUserRequestFailed() throws ExecutionException, InterruptedException {
        CompletableDeferred<Response<List<CommentResponse>>> deferredReply = new CompletableDeferred<>();
        deferredReply.complete(Response.success(Arrays.asList(replyResponse1)));
        when(service.getComments(anyString())).thenReturn(deferredReply);

        CompletableDeferred<Response<List<User>>> deferredUsers = new CompletableDeferred<>();
        deferredUsers.complete(Response.error(400, errorResponseBody));
        when(designerNewsService.getUsers(anyString())).thenReturn(deferredUsers);

        Result<List<CommentResponse>> result = repository.getComments(Arrays.asList(11L)).get();

        verify(service).getComments("11");
        verify(designerNewsService).getUsers("111");

        Assert.assertEquals(Result.Success(new ArrayList<>(Arrays.asList(reply1NoUser))), result);
    }

    private void withUsers(List<User> users, String ids) throws ExecutionException, InterruptedException {
        Response<List<User>> userResult = Response.success(users);
        CompletableDeferred<Response<List<User>>> deferredUsers = new CompletableDeferred<>();
        deferredUsers.complete(userResult);
        when(designerNewsService.getUsers(ids)).thenReturn(deferredUsers);
    }

    private void withComments(CommentResponse commentResponse, String ids) throws ExecutionException, InterruptedException {
        Response<List<CommentResponse>> resultParent = Response.success(Arrays.asList(commentResponse));
        CompletableDeferred<Response<List<CommentResponse>>> deferredReply = new CompletableDeferred<>();
        deferredReply.complete(resultParent);
        when(service.getComments(ids)).thenReturn(deferredReply);
    }

    private void withComments(List<CommentResponse> commentResponse, String ids) throws ExecutionException, InterruptedException {
        Response<List<CommentResponse>> resultParent = Response.success(commentResponse);
        CompletableDeferred<Response<List<CommentResponse>>> deferredReply = new CompletableDeferred<>();
        deferredReply.complete(resultParent);
        when(service.getComments(ids)).thenReturn(deferredReply);
    }
}