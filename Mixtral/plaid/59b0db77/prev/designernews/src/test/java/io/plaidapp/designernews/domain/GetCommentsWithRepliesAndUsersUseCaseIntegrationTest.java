

package io.plaidapp.designernews.domain;

import com.nhaarman.mockitokotlin2.Mockito;
import com.nhaarman.mockitokotlin2.verify;
import com.nhaarman.mockitokotlin2.whenever;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.DesignerNewsService;
import io.plaidapp.designernews.data.comments.CommentsRemoteDataSource;
import io.plaidapp.designernews.data.comments.CommentsRepository;
import io.plaidapp.designernews.data.comments.model.CommentResponse;
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
import kotlinx.coroutines.Runnable;
import okhttp3.ResponseBody;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class GetCommentsWithRepliesAndUsersUseCaseIntegrationTest {
    private DesignerNewsService service;
    private CommentsRemoteDataSource dataSource;
    private CommentsRepository commentsRepository;
    private UserRepository userRepository;
    private GetCommentsWithRepliesAndUsersUseCase repository;

    @Before
    public void setup() {
        service = Mockito.mock(DesignerNewsService.class);
        dataSource = new CommentsRemoteDataSource(service);
        commentsRepository = new CommentsRepository(dataSource);
        userRepository = new UserRepository(new UserRemoteDataSource(service));
        repository = new GetCommentsWithRepliesAndUsersUseCase(
                new GetCommentsWithRepliesUseCase(commentsRepository),
                userRepository
        );
    }

    @Test
    public void getComments_noReplies_whenCommentsAnUserRequestsSuccessful() throws InterruptedException {
        withComments(replyResponse1, "11");
        withUsers(listOf(user1), "111");

        Result<List<CommentResponse>> result = repository.getComments(List.of(11L));

        verify(service).getComments("11");
        Assert.assertEquals(Result.Success(List.of(reply1)), result);
    }

    @Test
    public void getComments_noReplies_whenCommentsRequestFailed() throws InterruptedException {
        Call<List<CommentResponse>> apiResult = createErrorCall(400, errorResponseBody);
        whenever(service.getComments("11")).thenReturn(apiResult);

        Result<List<CommentResponse>> result = repository.getComments(List.of(11L));

        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof Result.Error);
    }

    @Test
    public void getComments_multipleReplies_whenCommentsAndUsersRequestsSuccessful() throws InterruptedException {
        withComments(parentCommentResponse, "1");
        withComments(repliesResponses, "11,12");
        withUsers(List.of(user1, user2), "222,111");

        Result<List<CommentResponse>> result = repository.getComments(List.of(1L));

        verify(service).getComments("1");
        verify(service).getComments("11,12");
        verify(service).getUsers("222,111");
        Assert.assertEquals(Result.Success(flattendCommentsWithReplies), result);
    }

    @Test
    public void getComments_multipleReplies_whenRepliesRequestFailed() throws InterruptedException {
        withComments(parentCommentResponse, "1");

        Call<List<CommentResponse>> resultChildrenError = createErrorCall(400, errorResponseBody);
        whenever(service.getComments("11,12")).thenReturn(resultChildrenError);

        withUsers(List.of(user2), "222");

        Result<List<CommentResponse>> result = repository.getComments(List.of(1L));

        verify(service).getComments("1");
        verify(service).getComments("11,12");
        verify(service).getUsers("222");
        Assert.assertEquals(Result.Success(flattenedCommentsWithoutReplies), result);
    }

    @Test
    public void getComments_whenUserRequestFailed() throws InterruptedException {
        withComments(replyResponse1, "11");

        Call<List<User>> userError = createErrorCall(400, errorResponseBody);
        whenever(service.getUsers("111")).thenReturn(userError);

        Result<List<CommentResponse>> result = repository.getComments(List.of(11L));

        verify(service).getComments("11");
        verify(service).getUsers("111");
        Assert.assertEquals(Result.Success(List.of(reply1NoUser)), result);
    }

    private void withUsers(List<User> users, String ids) throws InterruptedException {
        Call<List<User>> userResult = createSuccessCall(users);
        whenever(service.getUsers(ids)).thenReturn(userResult);
    }

    private void withComments(CommentResponse commentResponse, String ids) throws InterruptedException {
        Call<List<CommentResponse>> resultParent = createSuccessCall(List.of(commentResponse));
        whenever(service.getComments(ids)).thenReturn(resultParent);
    }

    private void withComments(List<CommentResponse> commentResponse, String ids) throws InterruptedException {
        Call<List<CommentResponse>> resultParent = createSuccessCall(commentResponse);
        whenever(service.getComments(ids)).thenReturn(resultParent);
    }


}