package io.plaidapp.core.designernews.data.api.model;

import com.google.gson.annotations.SerializedName;
import io.plaidapp.core.designernews.domain.model.CommentWithReplies;
import java.util.Date;
import java.util.List;

public class CommentResponse {

    @SerializedName("id")
    private final long id;
    @SerializedName("body")
    private final String body;
    @SerializedName("created_at")
    private final Date created_at;
    @SerializedName("depth")
    private final int depth;
    @SerializedName("vote_count")
    private int vote_count;
    @SerializedName("links")
    private final CommentLinksResponse links;

    public CommentResponse(long id, String body, Date created_at, int depth, int vote_count, CommentLinksResponse links) {
        this.id = id;
        this.body = body;
        this.created_at = created_at;
        this.depth = depth;
        this.vote_count = vote_count;
        this.links = links;
    }

    public CommentWithReplies toCommentsWithReplies(List<CommentWithReplies> replies) {
        return new CommentWithReplies(
                id,
                links.parentComment,
                body,
                created_at,
                depth,
                links.commentUpvotes.size(),
                links.userId,
                links.story,
                replies
        );
    }
}