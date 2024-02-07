package io.plaidapp.core.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.data.comments.CommentsRepository;
import io.plaidapp.core.designernews.parentCommentResponse;
import io.plaidapp.core.designernews.parentCommentWithReplies;
import io.plaidapp.core.designernews.parentCommentWithRepliesWithoutReplies;
import io.plaidapp.core.designernews.reply1;
import io.plaidapp.core.designernews.replyResponse1;
import io.plaidapp.core.designernews.replyResponse2;
import io.plaidapp.core.designernews.replyWithReplies1;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
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
  public void getComments_noReplies_whenRequestSuccessful() throws Exception {
    List<Long> ids = Arrays.asList(reply1.id);
    Result repositoryResult = Result.Success(Arrays.asList(replyResponse1));
    Mockito.when(repository.getComments(ids)).thenReturn(repositoryResult);

    Result result = useCase.getCommentsWithReplies(ids);

    Mockito.verify(repository).getComments(ids);

    Assert.assertEquals(
      Result.Success(Arrays.asList(replyWithReplies1)),
      result
    );
  }

  @Test
  public void getComments_noReplies_whenRequestFailed() throws Exception {
    List<Long> ids = Arrays.asList(11L);
    Result repositoryResult = Result.Error(
      new IOException("Unable to get comments")
    );
    Mockito.when(repository.getComments(ids)).thenReturn(repositoryResult);

    Result result = useCase.getCommentsWithReplies(ids);

    Assert.assertNotNull(result);
    Assert.assertTrue(result instanceof Result.Error);
  }

  @Test
  public void getComments_multipleReplies_whenRequestSuccessful()
    throws Exception {
    Result<List<parentCommentResponse>> resultParent = Result.Success(
      Arrays.asList(parentCommentResponse)
    );
    List<Long> parentIds = Arrays.asList(1L);
    Mockito.when(repository.getComments(parentIds)).thenReturn(resultParent);

    List<Long> childrenIds = Arrays.asList(11L, 12L);
    Result<List<replyResponse>> resultChildren = Result.Success(
      Arrays.asList(replyResponse1, replyResponse2)
    );
    Mockito
      .when(repository.getComments(childrenIds))
      .thenReturn(resultChildren);

    Result result = useCase.getCommentsWithReplies(Arrays.asList(1L));

    Mockito.verify(repository).getComments(parentIds);
    Mockito.verify(repository).getComments(childrenIds);

    Assert.assertEquals(
      Result.Success(new ArrayList<>(Arrays.asList(parentCommentWithReplies))),
      result
    );
  }

  @Test
  public void getComments_multipleReplies_whenRepliesRequestFailed()
    throws Exception {
    Result<List<parentCommentResponse>> resultParent = Result.Success(
      Arrays.asList(parentCommentResponse)
    );
    List<Long> parentIds = Arrays.asList(1L);
    Mockito.when(repository.getComments(parentIds)).thenReturn(resultParent);

    Result<List<replyResponse>> resultChildrenError = Result.Error(
      new IOException("Unable to get comments")
    );
    List<Long> childrenIds = Arrays.asList(11L, 12L);
    Mockito
      .when(repository.getComments(childrenIds))
      .thenReturn(resultChildrenError);

    Result result = useCase.getCommentsWithReplies(Arrays.asList(1L));

    Mockito.verify(repository).getComments(parentIds);
    Mockito.verify(repository).getComments(childrenIds);

    Assert.assertEquals(
      Result.Success(
        new ArrayList<>(Arrays.asList(parentCommentWithRepliesWithoutReplies))
      ),
      result
    );
  }
}
