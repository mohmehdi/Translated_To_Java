package io.plaidapp.core.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.core.designernews.data.api.errorResponseBody;
import io.plaidapp.core.designernews.data.api.model.Comment;
import io.plaidapp.core.designernews.data.api.model.CommentResponse;
import io.plaidapp.core.designernews.data.api.model.User;
import io.plaidapp.core.designernews.data.api.parentComment;
import io.plaidapp.core.designernews.data.api.parentCommentResponse;
import io.plaidapp.core.designernews.data.api.parentCommentWithoutReplies;
import io.plaidapp.core.designernews.data.api.repliesResponses;
import io.plaidapp.core.designernews.data.api.reply1;
import io.plaidapp.core.designernews.data.api.reply1NoUser;
import io.plaidapp.core.designernews.data.api.replyResponse1;
import io.plaidapp.core.designernews.data.api.user1;
import io.plaidapp.core.designernews.data.api.user2;
import io.plaidapp.core.designernews.data.comments.CommentsRepository;
import io.plaidapp.core.designernews.data.comments.DesignerNewsCommentsRemoteDataSource;
import io.plaidapp.core.designernews.data.users.UserRemoteDataSource;
import io.plaidapp.core.designernews.data.users.UserRepository;
import io.plaidapp.core.designernews.provideCommentsUseCase;
import io.plaidapp.core.designernews.provideCommentsWithRepliesUseCase;
import io.plaidapp.test.shared.provideFakeCoroutinesContextProvider;
import kotlinx.coroutines.experimental.CompletableDeferred;
import kotlinx.coroutines.experimental.runBlocking;
import org.junit.Assert;

import org.junit.Test;
import org.mockito.Mockito;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.List;

public class CommentsUseCaseIntegrationTest {
    private DesignerNewsService service = Mockito.mock(DesignerNewsService.class);
    private DesignerNewsCommentsRemoteDataSource dataSource = new DesignerNewsCommentsRemoteDataSource(service);
    private CommentsRepository commentsRepository = new CommentsRepository(dataSource);
    private UserRepository userRepository = new UserRepository(new UserRemoteDataSource(service));
    private CommentsUseCase repository = provideCommentsUseCase(
            provideCommentsWithRepliesUseCase(commentsRepository),
            userRepository,
            provideFakeCoroutinesContextProvider()
    );

    @Test
    public void getComments_noReplies_whenCommentsAnUserRequestsSuccessful() throws Exception {

        withComments(replyResponse1, "11");

        withUsers(listOf(user1), "111");
        Result<List<Comment>> result = null;


        repository.getComments(listOf(11L), it -> result = it);


        Mockito.verify(service).getComments("11");

        Assert.assertEquals(Result.Success(listOf(reply1)), result);
    }

    @Test
    public void getComments_noReplies_whenCommentsRequestFailed() throws Exception {

        Response<List<CommentResponse>> apiResult = Response.error(400, errorResponseBody);
        Mockito.when(service.getComments("11")).thenReturn(new CompletableDeferred<>(apiResult));
        Result<List<Comment>> result = null;


        repository.getComments(listOf(11L), it -> result = it);


        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void getComments_multipleReplies_whenCommentsAndUsersRequestsSuccessful() throws Exception {



        withComments(parentCommentResponse, "1");

        withComments(repliesResponses, "11,12");

        withUsers(listOf(user1, user2), "222,111");
        Result<List<Comment>> result = null;


        repository.getComments(listOf(1L), it -> result = it);


        Mockito.verify(service).getComments("1");
        Mockito.verify(service).getComments("11,12");
        Mockito.verify(service).getUsers("222,111");

        Assert.assertEquals(Result.Success(listOf(parentComment)), result);
    }

    @Test
    public void getComments_multipleReplies_whenRepliesRequestFailed() throws Exception {


        withComments(parentCommentResponse, "1");

        Response<List<CommentResponse>> resultChildrenError = Response.error(400, errorResponseBody);
        Mockito.when(service.getComments("11,12"))
                .thenReturn(new CompletableDeferred<>(resultChildrenError));

        withUsers(listOf(user2), "222");
        Result<List<Comment>> result = null;


        repository.getComments(listOf(1L), it -> result = it);


        Mockito.verify(service).getComments("1");
        Mockito.verify(service).getComments("11,12");
        Mockito.verify(service).getUsers("222");

        Assert.assertEquals(Result.Success(new ArrayList<>(parentCommentWithoutReplies)), result);
    }

    @Test
    public void getComments_whenUserRequestFailed() throws Exception {



        withComments(replyResponse1, "11");

        Response<List<User>> userError = Response.error(400, errorResponseBody);
        Mockito.when(service.getUsers("111"))
                .thenReturn(new CompletableDeferred<>(userError));
        Result<List<Comment>> result = null;


        repository.getComments(listOf(11L), it -> result = it);


        Mockito.verify(service).getComments("11");
        Mockito.verify(service).getUsers("111");

        Assert.assertEquals(Result.Success(new ArrayList<>(reply1NoUser)), result);
    }


    private void withUsers(List<User> users, String ids) throws Exception {
        Response<List<User>> userResult = Response.success(users);
        Mockito.when(service.getUsers(ids)).thenReturn(new CompletableDeferred<>(userResult));
    }

    private void withComments(CommentResponse commentResponse, String ids) throws Exception {
        Response<List<CommentResponse>> resultParent = Response.success(listOf(commentResponse));
        Mockito.when(service.getComments(ids)).thenReturn(new CompletableDeferred<>(resultParent));
    }

    private void withComments(List<CommentResponse> commentResponse, String ids) throws Exception {
        Response<List<CommentResponse>> resultParent = Response.success(commentResponse);
        Mockito.when(service.getComments(ids)).thenReturn(new CompletableDeferred<>(resultParent));
    }
}