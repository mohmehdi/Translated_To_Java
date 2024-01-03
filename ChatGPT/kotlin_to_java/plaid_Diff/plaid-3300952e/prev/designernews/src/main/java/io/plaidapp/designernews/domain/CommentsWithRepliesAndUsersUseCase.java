package io.plaidapp.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.core.designernews.domain.model.CommentWithReplies;
import io.plaidapp.core.designernews.domain.model.CommentKt;
import io.plaidapp.designernews.data.users.UserRepository;

import java.util.List;
import java.util.Set;

public class CommentsWithRepliesAndUsersUseCase {

    private CommentsWithRepliesUseCase commentsWithRepliesUseCase;
    private UserRepository userRepository;

    public CommentsWithRepliesAndUsersUseCase(CommentsWithRepliesUseCase commentsWithRepliesUseCase,
                                              UserRepository userRepository) {
        this.commentsWithRepliesUseCase = commentsWithRepliesUseCase;
        this.userRepository = userRepository;
    }

    public Result<List<Comment>> invoke(List<Long> ids) {

        Result<List<CommentWithReplies>> commentsWithRepliesResult = commentsWithRepliesUseCase.getCommentsWithReplies(ids);
        if (commentsWithRepliesResult instanceof Result.Error) {
            return (Result<List<Comment>>) commentsWithRepliesResult;
        }
        List<CommentWithReplies> commentsWithReplies = ((Result.Success<List<CommentWithReplies>>) commentsWithRepliesResult).getData().isEmpty()
                ? new ArrayList<>()
                : ((Result.Success<List<CommentWithReplies>>) commentsWithRepliesResult).getData();

        Set<Long> userIds = new HashSet<>();
        createUserIds(commentsWithReplies, userIds);


        Result<Set<User>> usersResult = userRepository.getUsers(userIds);
        Set<User> users = usersResult instanceof Result.Success
                ? usersResult.getData()
                : new HashSet<>();

        List<Comment> comments = createComments(commentsWithReplies, users);
        return new Result.Success<>(comments);
    }

    private void createUserIds(List<CommentWithReplies> comments, Set<Long> userIds) {
        for (CommentWithReplies comment : comments) {
            userIds.add(comment.getUserId());
            createUserIds(comment.getReplies(), userIds);
        }
    }

    private List<Comment> createComments(List<CommentWithReplies> commentsWithReplies, Set<User> users) {
        Map<Long, User> userMapping = users.stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return commentsWithReplies.stream()
                .flatMap(CommentWithReplies::flattenWithReplies)
                .map(comment -> CommentKt.toComment(userMapping.get(comment.getUserId())))
                .collect(Collectors.toList());
    }
}