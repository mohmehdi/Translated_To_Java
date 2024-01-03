package io.plaidapp.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.comments.model.CommentsWithRepliesKt;
import io.plaidapp.core.designernews.data.comments.CommentsRepository;
import io.plaidapp.core.designernews.domain.model.CommentWithReplies;
import java.io.IOException;
import java.util.List;

public class CommentsWithRepliesUseCase {

    private CommentsRepository commentsRepository;

    public CommentsWithRepliesUseCase(CommentsRepository commentsRepository) {
        this.commentsRepository = commentsRepository;
    }

    public Result<List<CommentWithReplies>> getCommentsWithReplies(List<Long> parentIds) {
        List<List<CommentResponse>> replies = new ArrayList<>();

        Result<List<CommentResponse>> parentComments = commentsRepository.getComments(parentIds);

        while (parentComments instanceof Result.Success) {
            List<CommentResponse> parents = parentComments.getData();

            replies.add(parents);

            List<Long> replyIds = new ArrayList<>();
            for (CommentResponse comment : parents) {
                replyIds.addAll(comment.getLinks().getComments());
            }
            if (!replyIds.isEmpty()) {
                parentComments = commentsRepository.getComments(replyIds);
            } else {
                if (!replies.isEmpty()) {
                    return new Result.Success<>(matchComments(replies));
                }
            }
        }

        if (!replies.isEmpty()) {
            return new Result.Success<>(matchComments(replies));
        } else if (parentComments instanceof Result.Error) {
            return parentComments;
        } else {
            return new Result.Error<>(new IOException("Unable to get comments"));
        }
    }

    private List<CommentWithReplies> matchComments(List<List<CommentResponse>> comments) {
        List<CommentWithReplies> commentsWithReplies = Collections.emptyList();
        for (int index = comments.size() - 1; index >= 0; index--) {
            commentsWithReplies = matchCommentsWithReplies(comments.get(index), commentsWithReplies);
        }
        return commentsWithReplies;
    }

    private List<CommentWithReplies> matchCommentsWithReplies(
            List<CommentResponse> comments,
            List<CommentWithReplies> replies) {
        Map<Long, List<CommentWithReplies>> commentReplyMapping = new HashMap<>();

        for (CommentWithReplies reply : replies) {
            long parentId = reply.getParentId();
            if (!commentReplyMapping.containsKey(parentId)) {
                commentReplyMapping.put(parentId, new ArrayList<>());
            }
            commentReplyMapping.get(parentId).add(reply);
        }

        List<CommentWithReplies> result = new ArrayList<>();
        for (CommentResponse comment : comments) {
            List<CommentWithReplies> commentReplies = commentReplyMapping.getOrDefault(comment.getId(), Collections.emptyList());
            result.add(CommentsWithRepliesKt.toCommentsWithReplies(comment, commentReplies));
        }
        return result;
    }
}