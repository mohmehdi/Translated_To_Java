package io.plaidapp.core.designernews.domain;

import io.plaidapp.core.data.CoroutinesContextProvider;
import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.api.model.Comment;
import io.plaidapp.core.designernews.data.api.model.User;
import io.plaidapp.core.designernews.data.users.UserRepository;
import io.plaidapp.core.designernews.domain.model.CommentWithReplies;
import io.plaidapp.core.designernews.domain.model.CommentKt;
import kotlinx.coroutines.experimental.launch;
import kotlinx.coroutines.experimental.withContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class CommentsUseCase {

    private CommentsWithRepliesUseCase commentsWithRepliesUseCase;
    private UserRepository userRepository;
    private CoroutinesContextProvider contextProvider;

    public CommentsUseCase(
            CommentsWithRepliesUseCase commentsWithRepliesUseCase,
            UserRepository userRepository,
            CoroutinesContextProvider contextProvider) {
        this.commentsWithRepliesUseCase = commentsWithRepliesUseCase;
        this.userRepository = userRepository;
        this.contextProvider = contextProvider;
    }

    public void getComments(
            List<Long> ids,
            OnResultListener<List<Comment>> onResult) {
        launch(contextProvider.getIo()) {

            Result<List<CommentWithReplies>> commentsWithRepliesResult = commentsWithRepliesUseCase.getCommentsWithReplies(ids);
            if (commentsWithRepliesResult instanceof Result.Error) {
                withContext(contextProvider.getMain(), () -> {
                    onResult.onResult(new Result.Error<>(commentsWithRepliesResult.getException()));
                });
                return@launch;
            }
            List<CommentWithReplies> commentsWithReplies = ((Result.Success<List<CommentWithReplies>>) commentsWithRepliesResult).getData().orEmpty();

            Set<Long> userIds = new HashSet<>();
            createUserIds(commentsWithReplies, userIds);


            Result<Set<User>> usersResult = userRepository.getUsers(userIds);
            Set<User> users = usersResult instanceof Result.Success ? usersResult.getData() : Collections.emptySet();

            List<Comment> comments = createComments(commentsWithReplies, users);
            withContext(contextProvider.getMain(), () -> onResult.onResult(new Result.Success<>(comments)));
        }
    }

    private void createUserIds(List<CommentWithReplies> comments, Set<Long> userIds) {
        for (CommentWithReplies comment : comments) {
            userIds.add(comment.getUserId());
            createUserIds(comment.getReplies(), userIds);
        }
    }

    private List<Comment> createComments(
            List<CommentWithReplies> commentsWithReplies,
            Set<User> users) {
        Map<Long, User> userMapping = users.stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return match(commentsWithReplies, userMapping);
    }

    private List<Comment> match(
            List<CommentWithReplies> commentsWithReplies,
            Map<Long, User> users) {
        List<Comment> comments = new ArrayList<>();
        for (CommentWithReplies comment : commentsWithReplies) {
            User user = users.get(comment.getUserId());
            comments.add(comment.toComment(match(comment.getReplies(), users), user));
        }
        return comments;
    }

    public interface OnResultListener<T> {
        void onResult(Result<T> result);
    }
}