package io.plaidapp.core.designernews.domain;

import io.plaidapp.core.data.CoroutinesContextProvider;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.users.UserRepository;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.core.designernews.domain.model.CommentWithReplies;
import io.plaidapp.core.designernews.domain.model.toComment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;

class CommentsUseCase {

  private final CommentsWithRepliesUseCase commentsWithRepliesUseCase;
  private final UserRepository userRepository;
  private final CoroutinesContextProvider contextProvider;

  public CommentsUseCase(
    CommentsWithRepliesUseCase commentsWithRepliesUseCase,
    UserRepository userRepository,
    CoroutinesContextProvider contextProvider
  ) {
    this.commentsWithRepliesUseCase = commentsWithRepliesUseCase;
    this.userRepository = userRepository;
    this.contextProvider = contextProvider;
  }

  public void getComments(
    List<Long> ids,
    Function1<Result<List<Comment>>> onResult
  ) {
    contextProvider.io.execute(() -> {
      Result<List<CommentWithReplies>> commentsWithRepliesResult = commentsWithRepliesUseCase.getCommentsWithReplies(
        ids
      );
      if (commentsWithRepliesResult instanceof Result.Error) {
        contextProvider.main.execute(() -> {
          onResult.invoke(
            Result.error(((Result.Error) commentsWithRepliesResult).exception)
          );
        });
        return;
      }
      List<CommentWithReplies> commentsWithReplies = commentsWithRepliesResult instanceof Result.Success
        ? (
          (Result.Success<List<CommentWithReplies>>) commentsWithRepliesResult
        ).data
        : new ArrayList<>();

      Set<Long> userIds = new HashSet<>();
      createUserIds(commentsWithReplies, userIds);

      Result<Set<User>> usersResult = userRepository.getUsers(userIds);
      Set<User> users = usersResult instanceof Result.Success
        ? usersResult.getData()
        : new HashSet<>();

      List<Comment> comments = createComments(commentsWithReplies, users);
      contextProvider.main.execute(() ->
        onResult.invoke(Result.success(comments))
      );
    });
  }

  private void createUserIds(
    List<CommentWithReplies> comments,
    Set<Long> userIds
  ) {
    for (CommentWithReplies comment : comments) {
      userIds.add(comment.userId);
      createUserIds(comment.replies, userIds);
    }
  }

  private List<Comment> createComments(
    List<CommentWithReplies> commentsWithReplies,
    Set<User> users
  ) {
    Map<Long, User> userMapping = new HashMap<>();
    for (User user : users) {
      userMapping.put(user.getId(), user);
    }
    return match(commentsWithReplies, userMapping);
  }

  private List<Comment> match(
    List<CommentWithReplies> commentsWithReplies,
    Map<Long, User> users
  ) {
    List<Comment> comments = new ArrayList<>();
    for (CommentWithReplies comment : commentsWithReplies) {
      User user = users.get(comment.userId);
      comments.add(comment.toComment(match(comment.replies, users), user));
    }
    return comments;
  }
}
