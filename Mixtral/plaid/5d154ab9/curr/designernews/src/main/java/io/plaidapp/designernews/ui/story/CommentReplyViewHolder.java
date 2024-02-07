package io.plaidapp.designernews.ui.story;

import android.support.design.widget.TextInputLayout;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import io.plaidapp.designernews.R;
import io.plaidapp.core.designernews.domain.model.Comment;

public class CommentReplyViewHolder extends RecyclerView.ViewHolder {
    public final Button commentVotes;
    public final TextInputLayout replyLabel;
    public final EditText commentReply;
    public final ImageButton postReply;

    public CommentReplyViewHolder(View itemView) {
        super(itemView);
        commentVotes = itemView.findViewById(R.id.comment_votes);
        replyLabel = itemView.findViewById(R.id.comment_reply_label);
        commentReply = itemView.findViewById(R.id.comment_reply);
        postReply = itemView.findViewById(R.id.post_reply);
    }

    public void bindCommentReply(Comment comment) {
        commentVotes.setText(String.valueOf(comment.upvotesCount));
        commentVotes.setActivated(comment.upvoted != null && comment.upvoted);
    }
}