package io.plaidapp.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.designernews.data.comments.CommentsRepository;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.data.comments.model.CommentsWithReplies;
import io.plaidapp.core.designernews.domain.model.CommentWithReplies;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

public class GetCommentsWithRepliesUseCase {

    private CommentsRepository commentsRepository;

    @Inject
    public GetCommentsWithRepliesUseCase(CommentsRepository commentsRepository) {
        this.commentsRepository = commentsRepository;
    }

    public Result<List<CommentWithReplies>> invoke(List<Long> parentIds) {
        List<List<CommentResponse>> replies = new ArrayList<>();

        Result<List<CommentResponse>> parentComments = commentsRepository.getComments(parentIds);

        while (parentComments instanceof Result.Success) {
            List<CommentResponse> parents = parentComments.data;

            replies.add(parents);

            List<Long> replyIds = new ArrayList<>();
            for (CommentResponse comment : parents) {
                replyIds.addAll(comment.links.comments);
            }
            if (!replyIds.isEmpty()) {
                parentComments = commentsRepository.getComments(replyIds);
            } else {
                if (!replies.isEmpty()) {
                    return Result.Success(matchComments(replies));
                }
            }
        }

        if (!replies.isEmpty()) {
            return Result.Success(matchComments(replies));
        } else if (parentComments instanceof Result.Error) {
            return parentComments;
        } else {
            return Result.Error(new IOException("Unable to get comments"));
        }
    }

    private List<CommentWithReplies> matchComments(List<List<CommentResponse>> comments) {
        List<CommentWithReplies> commentsWithReplies = new ArrayList<>();
        for (int index = comments.size() - 1; index >= 0; index--) {
            commentsWithReplies = matchCommentsWithReplies(comments.get(index), commentsWithReplies);
        }
        return commentsWithReplies;
    }

    private List<CommentWithReplies> matchCommentsWithReplies(
            List<CommentResponse> comments,
            List<CommentWithReplies> replies) {
        List<CommentWithReplies> commentWithRepliesList = new ArrayList<>();
        for (CommentResponse comment : comments) {
            List<CommentWithReplies> commentReplies = new ArrayList<>();
            for (CommentWithReplies reply : replies) {
                if (reply.getParentId() == comment.getId()) {
                    commentReplies.add(reply);
                }
            }
            commentWithRepliesList.add(comment.toCommentsWithReplies(commentReplies));
        }
        return commentWithRepliesList;
    }
}