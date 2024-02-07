package io.plaidapp.designernews.domain;

import io.plaidapp.core.data.Result;
import io.plaidapp.core.designernews.data.comments.CommentsRepository;
import io.plaidapp.core.designernews.data.comments.model.CommentResponse;
import io.plaidapp.core.designernews.domain.model.CommentWithReplies;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

public class GetCommentsWithRepliesUseCase {

  private final CommentsRepository commentsRepository;

  @Inject
  public GetCommentsWithRepliesUseCase(CommentsRepository commentsRepository) {
    this.commentsRepository = commentsRepository;
  }

  public Result<List<CommentWithReplies>> invoke(List<Long> parentIds) {
    List<List<CommentResponse>> replies = new ArrayList<>();

    Result<List<CommentResponse>> parentComments = commentsRepository.getComments(
      parentIds
    );

    while (parentComments.succeeded()) {
      List<CommentResponse> parents = parentComments.getOrThrow();

      replies.add(parents);

      List<Long> replyIds = new ArrayList<>();
      for (CommentResponse comment : parents) {
        replyIds.addAll(comment.getLinks().getComments());
      }
      if (!replyIds.isEmpty()) {
        parentComments = commentsRepository.getComments(replyIds);
      } else {
        if (!replies.isEmpty()) {
          return Result.success(matchComments(replies));
        }
      }
    }

    if (replies.isEmpty()) {
      if (parentComments.isFailure()) {
        return parentComments;
      } else {
        throw new IOException("Unable to get comments");
      }
    }

    return Result.success(matchComments(replies));
  }

  private List<CommentWithReplies> matchComments(
    List<List<CommentResponse>> comments
  ) {
    List<CommentWithReplies> commentsWithReplies = new ArrayList<>();
    for (int index = comments.size() - 1; index >= 0; index--) {
      commentsWithReplies =
        matchCommentsWithReplies(comments.get(index), commentsWithReplies);
    }
    return commentsWithReplies;
  }

  private List<CommentWithReplies> matchCommentsWithReplies(
    List<CommentResponse> comments,
    List<CommentWithReplies> replies
  ) {
    Map<Long, List<CommentWithReplies>> commentReplyMapping = new HashMap<>();
    for (CommentWithReplies reply : replies) {
      List<CommentWithReplies> repliesList = commentReplyMapping.getOrDefault(
        reply.getParentId(),
        new ArrayList<>()
      );
      repliesList.add(reply);
      commentReplyMapping.put(reply.getParentId(), repliesList);
    }

    List<CommentWithReplies> result = new ArrayList<>();
    for (CommentResponse comment : comments) {
      List<CommentWithReplies> commentReplies = commentReplyMapping.getOrDefault(
        comment.getId(),
        new ArrayList<>()
      );
      result.add(comment.toCommentsWithReplies(commentReplies));
    }

    return result;
  }
}
