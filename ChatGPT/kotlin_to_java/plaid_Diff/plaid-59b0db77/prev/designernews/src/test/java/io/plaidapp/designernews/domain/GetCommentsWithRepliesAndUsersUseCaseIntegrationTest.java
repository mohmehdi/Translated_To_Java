package io.plaidapp.designernews.domain;

import com.nhaarman.mockitokotlin2.mock;
import com.nhaarman.mockitokotlin2.verify;
import com.nhaarman.mockitokotlin2.whenever;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.designernews.data.comments.CommentsRemoteDataSource;
import io.plaidapp.designernews.data.comments.CommentsRepository;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.users.model.User;
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
import kotlinx.coroutines.runBlocking;
import org.junit.Assert;

import org.junit.Test;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GetCommentsWithRepliesAndUsersUseCaseIntegrationTest {
    private DesignerNewsService service = mock(DesignerNewsService.class);
    private CommentsRemoteDataSource dataSource =
            new CommentsRemoteDataSource(service);
    private CommentsRepository commentsRepository =
            new CommentsRepository(dataSource);
    private UserRepository userRepository = new UserRepository(new UserRemoteDataSource(service));
    private GetCommentsWithRepliesAndUsersUseCase repository = new GetCommentsWithRepliesAndUsersUseCase(
            new GetCommentsWithRepliesUseCase(commentsRepository),
            userRepository
    );

    @Test
    public void getComments_noReplies_whenCommentsAnUserRequestsSuccessful() throws Exception {

        withComments(replyResponse1, "11");

        withUsers(Arrays.asList(user1), "111");


        Result<List<CommentResponse>> result = runBlocking(repository.invoke(Arrays.asList(11L)));


        verify(service).getComments("11");

        Assert.assertEquals(Result.Success(Arrays.asList(reply1)), result);
    }

    @Test
    public void getComments_noReplies_whenCommentsRequestFailed() throws Exception {

        Response<List<CommentResponse>> apiResult = Response.error(
                400,
                errorResponseBody
        );
        whenever(service.getComments("11")).thenReturn(new CompletableDeferred<>(apiResult));


        Result<List<CommentResponse>> result = runBlocking(repository.invoke(Arrays.asList(11L)));


        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void getComments_multipleReplies_whenCommentsAndUsersRequestsSuccessful() throws Exception {


        withComments(parentCommentResponse, "1");

        withComments(repliesResponses, "11,12");

        withUsers(Arrays.asList(user1, user2), "222,111");


        Result<List<CommentResponse>> result = runBlocking(repository.invoke(Arrays.asList(1L)));


        verify(service).getComments("1");
        verify(service).getComments("11,12");
        verify(service).getUsers("222,111");

        Assert.assertEquals(Result.Success(flattendCommentsWithReplies), result);
    }

    @Test
    public void getComments_multipleReplies_whenRepliesRequestFailed() throws Exception {


        withComments(parentCommentResponse, "1");

        Response<List<CommentResponse>> resultChildrenError = Response.error(
                400,
                errorResponseBody
        );
        whenever(service.getComments("11,12"))
                .thenReturn(new CompletableDeferred<>(resultChildrenError));

        withUsers(Arrays.asList(user2), "222");


        Result<List<CommentResponse>> result = runBlocking(repository.invoke(Arrays.asList(1L)));


        verify(service).getComments("1");
        verify(service).getComments("11,12");
        verify(service).getUsers("222");

        Assert.assertEquals(Result.Success(flattenedCommentsWithoutReplies), result);
    }

    @Test
    public void getComments_whenUserRequestFailed() throws Exception {


        withComments(replyResponse1, "11");

        Response<List<User>> userError = Response.error(
                400,
                errorResponseBody
        );
        whenever(service.getUsers("111"))
                .thenReturn(new CompletableDeferred<>(userError));


        Result<List<CommentResponse>> result = runBlocking(repository.invoke(Arrays.asList(11L)));


        verify(service).getComments("11");
        verify(service).getUsers("111");

        List<CommentResponse> expectedResult = new ArrayList<>();
        expectedResult.add(reply1NoUser);

        Assert.assertEquals(Result.Success(expectedResult), result);
    }


    private void withUsers(List<User> users, String ids) throws Exception {
        Response<List<User>> userResult = Response.success(users);
        whenever(service.getUsers(ids)).thenReturn(new CompletableDeferred<>(userResult));
    }

    private void withComments(CommentResponse commentResponse, String ids) {
        Response<List<CommentResponse>> resultParent = Response.success(Arrays.asList(commentResponse));
        whenever(service.getComments(ids)).thenReturn(new CompletableDeferred<>(resultParent));
    }

    private void withComments(List<CommentResponse> commentResponse, String ids) {
        Response<List<CommentResponse>> resultParent = Response.success(commentResponse);
        whenever(service.getComments(ids)).thenReturn(new CompletableDeferred<>(resultParent));
    }
}