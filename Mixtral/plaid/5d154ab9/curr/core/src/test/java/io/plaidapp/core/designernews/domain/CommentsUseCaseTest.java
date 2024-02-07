package io.plaidapp.core.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.users.UserRepository;
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
import io.plaidapp.test.shared.CoroutinesContextProvider;
import io.plaidapp.test.shared.provideFakeCoroutinesContextProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class CommentsUseCaseTest {

  @Mock
  private CommentsWithRepliesUseCase commentsWithRepliesUseCase;

  @Mock
  private UserRepository userRepository;

  private CommentsUseCase repository;
  private CoroutinesContextProvider coroutinesContextProvider;

  @Test
  public void getComments_noReplies_whenCommentsAnUserRequestsSuccessful()
    throws Exception {
    List<Long> ids = Arrays.asList(11L);
    withComment(replyWithReplies1, ids);

    Set<User> users = new HashSet<>();
    users.add(user1);
    withUsers(users, new HashSet<>(Arrays.asList(111L)));

    Result<List<Comment>> result = null;
    repository.getComments(
      ids,
      new CommentsUseCase.GetCommentsCallback() {
        @Override
        public void onResult(Result<List<Comment>> result) {
          CommentsUseCaseTest.this.result = result;
        }
      }
    );

    Assert.assertEquals(Result.Success(Arrays.asList(reply1)), result);
  }

  @Test
  public void getComments_noReplies_whenCommentsRequestFailed()
    throws Exception {
    Result<List<Comment>> resultError = Result.Error(
      new IOException("Comment error")
    );
    List<Long> ids = Arrays.asList(11L);
    Mockito
      .when(commentsWithRepliesUseCase.getCommentsWithReplies(ids))
      .thenReturn(resultError);

    Result<List<Comment>> result = null;
    repository.getComments(
      ids,
      new CommentsUseCase.GetCommentsCallback() {
        @Override
        public void onResult(Result<List<Comment>> result) {
          CommentsUseCaseTest.this.result = result;
        }
      }
    );

    Assert.assertNotNull(result);
    Assert.assertTrue(result instanceof Result.Error);
  }

  @Test
  public void getComments_multipleReplies_whenCommentsAndUsersRequestsSuccessful()
    throws Exception {
    List<Long> parentIds = Arrays.asList(1L);
    withComment(parentCommentWithReplies, parentIds);

    Set<User> users = new HashSet<>();
    users.add(user1);
    users.add(user2);
    withUsers(users, new HashSet<>(Arrays.asList(111L, 222L)));

    Result<List<Comment>> result = null;
    repository.getComments(
      parentIds,
      new CommentsUseCase.GetCommentsCallback() {
        @Override
        public void onResult(Result<List<Comment>> result) {
          CommentsUseCaseTest.this.result = result;
        }
      }
    );

    Mockito
      .verify(commentsWithRepliesUseCase)
      .getCommentsWithReplies(parentIds);

    Assert.assertEquals(Result.Success(Arrays.asList(parentComment)), result);
  }

  @Test
  public void getComments_multipleReplies_whenRepliesRequestFailed()
    throws Exception {
    List<Long> parentIds = Arrays.asList(1L);
    withComment(parentCommentWithRepliesWithoutReplies, parentIds);

    Set<User> users = new HashSet<>();
    users.add(user2);
    withUsers(users, new HashSet<>(Arrays.asList(222)));

    Result<List<Comment>> result = null;
    repository.getComments(
      parentIds,
      new CommentsUseCase.GetCommentsCallback() {
        @Override
        public void onResult(Result<List<Comment>> result) {
          CommentsUseCaseTest.this.result = result;
        }
      }
    );

    Mockito
      .verify(commentsWithRepliesUseCase)
      .getCommentsWithReplies(parentIds);

    Assert.assertEquals(
      Result.Success(Collections.singletonList(parentCommentWithoutReplies)),
      result
    );
  }

  @Test
  public void getComments_whenUserRequestFailed() throws Exception {
    List<Long> ids = Arrays.asList(11L);
    withComment(replyWithReplies1, ids);

    Result<List<User>> userError = Result.Error(new IOException("User error"));
    Mockito
      .when(userRepository.getUsers(new HashSet<>(Arrays.asList(11L))))
      .thenReturn(userError);

    Result<List<Comment>> result = null;
    repository.getComments(
      ids,
      new CommentsUseCase.GetCommentsCallback() {
        @Override
        public void onResult(Result<List<Comment>> result) {
          CommentsUseCaseTest.this.result = result;
        }
      }
    );

    Mockito.verify(commentsWithRepliesUseCase).getCommentsWithReplies(ids);

    Assert.assertEquals(
      Result.Success(Collections.singletonList(reply1NoUser)),
      result
    );
  }

  private void withUsers(Set<User> users, Set<Long> ids) throws Exception {
    Result<Set<User>> userResult = Result.Success(users);
    Mockito.when(userRepository.getUsers(ids)).thenReturn(userResult);
  }

  private void withComment(CommentWithReplies comment, List<Long> ids)
    throws Exception {
    List<CommentWithReplies> commentList = new ArrayList<>();
    commentList.add(comment);
    Result<List<CommentWithReplies>> resultParent = Result.Success(commentList);
    Mockito
      .when(commentsWithRepliesUseCase.getCommentsWithReplies(ids))
      .thenReturn(resultParent);
  }

  private void withComments(List<CommentWithReplies> comments, List<Long> ids)
    throws Exception {
    Result<List<CommentWithReplies>> resultParent = Result.Success(comments);
    Mockito
      .when(commentsWithRepliesUseCase.getCommentsWithReplies(ids))
      .thenReturn(resultParent);
  }
}
