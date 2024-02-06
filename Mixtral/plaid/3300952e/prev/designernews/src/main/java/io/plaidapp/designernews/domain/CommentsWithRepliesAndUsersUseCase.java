




package io.plaidapp.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.users.model.User;
import io.plaidapp.core.designernews.domain.model.Comment;
import io.plaidapp.core.designernews.domain.model.CommentWithReplies;
import io.plaidapp.core.designernews.domain.model.toComment;
import io.plaidapp.designernews.data.users.UserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CommentsWithRepliesAndUsersUseCase {
    private final CommentsWithRepliesUseCase commentsWithRepliesUseCase;
    private final UserRepository userRepository;

    public CommentsWithRepliesAndUsersUseCase(CommentsWithRepliesUseCase commentsWithRepliesUseCase,
                                              UserRepository userRepository) {
        this.commentsWithRepliesUseCase = commentsWithRepliesUseCase;
        this.userRepository = userRepository;
    }

    public Result<List<Comment>> invoke(List<Long> ids) {
        Result<List<CommentWithReplies>> commentsWithRepliesResult =
                commentsWithRepliesUseCase.getCommentsWithReplies(ids);

        if (commentsWithRepliesResult instanceof Result.Error) {
            return commentsWithRepliesResult;
        }

        List<CommentWithReplies> commentsWithReplies =
                ((Result.Success<List<CommentWithReplies>>) commentsWithRepliesResult).getData();

        Set<Long> userIds = new HashSet<>();
        createUserIds(commentsWithReplies, userIds);

        Result<Set<User>> usersResult = userRepository.getUsers(userIds);
        Set<User> users = usersResult instanceof Result.Success ? usersResult.getData() : new HashSet<>();

        List<Comment> comments = createComments(commentsWithReplies, users);
        return new Result.Success<>(comments);
    }

    private void createUserIds(List<CommentWithReplies> comments, Set<Long> userIds) {
        for (CommentWithReplies comment : comments) {
            userIds.add(comment.getUserId());
            createUserIds(comment.getReplies(), userIds);
        }
    }

    private List<Comment> createComments(List<CommentWithReplies> commentsWithReplies,
                                         Set<User> users) {
        Map<Long, User> userMapping = users.stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        List<Comment> comments = new ArrayList<>();
        for (CommentWithReplies commentWithReplies : commentsWithReplies) {
            List<CommentWithReplies.Reply> replies = commentWithReplies.getReplies();
            if (replies != null) {
                for (int i = 0; i < replies.size(); i++) {
                    replies.set(i, replies.get(i).withUser(userMapping.get(replies.get(i).getUserId())));
                }
            }
            comments.add(commentWithReplies.toComment(userMapping.get(commentWithReplies.getUserId())));
        }

        return comments;
    }
}