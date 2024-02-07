package io.plaidapp.core.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.api.ApiResponse;
import io.plaidapp.core.designernews.data.api.parentCommentResponse;
import io.plaidapp.core.designernews.data.api.parentCommentWithReplies;
import io.plaidapp.core.designernews.data.api.parentCommentWithRepliesWithoutReplies;
import io.plaidapp.core.designernews.data.api.reply1;
import io.plaidapp.core.designernews.data.api.replyResponse1;
import io.plaidapp.core.designernews.data.api.replyResponse2;
import io.plaidapp.core.designernews.data.api.replyWithReplies1;
import io.plaidapp.core.designernews.data.comments.CommentsRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class CommentsWithRepliesUseCaseTest {

  private CommentsRepository repository = Mockito.mock(
    CommentsRepository.class
  );
  private CommentsWithRepliesUseCase useCase = new CommentsWithRepliesUseCase(
    repository
  );

  @Test
  public void getComments_noReplies_whenRequestSuccessful()
    throws ExecutionException, InterruptedException {
    List<Long> ids = List.of(reply1.id);
    Result<ApiResponse<List<replyResponse1>>> repositoryResult = Result.success(
      List.of(replyResponse1)
    );
    Mockito.when(repository.getComments(ids)).thenReturn(repositoryResult);

    Result<ApiResponse<List<replyWithReplies1>>> result = useCase.getCommentsWithReplies(
      ids
    );

    Mockito.verify(repository).getComments(ids);

    Assert.assertEquals(Result.success(List.of(replyWithReplies1)), result);
  }

  @Test
  public void getComments_noReplies_whenRequestFailed()
    throws ExecutionException, InterruptedException {
    List<Long> ids = List.of(11L);
    Result<ApiResponse<List<replyResponse1>>> repositoryResult = Result.error(
      new IOException("Unable to get comments")
    );
    Mockito.when(repository.getComments(ids)).thenReturn(repositoryResult);

    Result<ApiResponse<List<replyWithReplies1>>> result = useCase.getCommentsWithReplies(
      ids
    );

    Assert.assertNotNull(result);
    Assert.assertTrue(result instanceof Result.Error);
  }

  @Test
  public void getComments_multipleReplies_whenRequestSuccessful()
    throws ExecutionException, InterruptedException {
    List<Long> parentIds = List.of(1L);
    Result<ApiResponse<List<parentCommentResponse>>> resultParent = Result.success(
      List.of(parentCommentResponse)
    );
    Mockito.when(repository.getComments(parentIds)).thenReturn(resultParent);

    List<Long> childrenIds = List.of(11L, 12L);
    Result<ApiResponse<List<replyResponse1>>> resultChildren = Result.success(
      List.of(replyResponse1, replyResponse2)
    );
    Mockito
      .when(repository.getComments(childrenIds))
      .thenReturn(resultChildren);

    Result<ApiResponse<List<parentCommentWithReplies>>> result = useCase.getCommentsWithReplies(
      List.of(1L)
    );

    Mockito.verify(repository).getComments(parentIds);
    Mockito.verify(repository).getComments(childrenIds);

    Assert.assertEquals(
      Result.success(List.of(parentCommentWithReplies)),
      result
    );
  }

  @Test
  public void getComments_multipleReplies_whenRepliesRequestFailed()
    throws ExecutionException, InterruptedException {
    List<Long> parentIds = List.of(1L);
    Result<ApiResponse<List<parentCommentResponse>>> resultParent = Result.success(
      List.of(parentCommentResponse)
    );
    Mockito.when(repository.getComments(parentIds)).thenReturn(resultParent);

    List<Long> childrenIds = List.of(11L, 12L);
    Result<ApiResponse<List<replyResponse1>>> resultChildrenError = Result.error(
      new IOException("Unable to get comments")
    );
    Mockito
      .when(repository.getComments(childrenIds))
      .thenReturn(resultChildrenError);

    Result<ApiResponse<List<parentCommentWithRepliesWithoutReplies>>> result = useCase.getCommentsWithReplies(
      List.of(1L)
    );

    Mockito.verify(repository).getComments(parentIds);
    Mockito.verify(repository).getComments(childrenIds);

    Assert.assertEquals(
      Result.success(List.of(parentCommentWithRepliesWithoutReplies)),
      result
    );
  }
}
