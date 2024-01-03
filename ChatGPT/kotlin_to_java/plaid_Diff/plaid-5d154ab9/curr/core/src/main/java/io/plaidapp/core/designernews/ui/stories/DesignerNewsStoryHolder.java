package io.plaidapp.core.designernews.ui.stories;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.support.v7.widget.RecyclerView;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Arrays;

import io.plaidapp.core.R;
import io.plaidapp.core.designernews.data.stories.model.Story;
import io.plaidapp.core.ui.recyclerview.Divided;
import io.plaidapp.core.ui.transitions.GravityArcMotion;
import io.plaidapp.core.ui.widget.BaselineGridTextView;
import io.plaidapp.core.util.AnimUtils;
import io.plaidapp.core.util.ViewUtils;

public class DesignerNewsStoryHolder extends RecyclerView.ViewHolder implements Divided {
    private Story story;
    private final BaselineGridTextView title;
    private final TextView comments;
    private final ImageButton pocket;

    public DesignerNewsStoryHolder(View itemView, boolean pocketIsInstalled,
                                   OnPocketClickedListener onPocketClicked,
                                   OnCommentsClickedListener onCommentsClicked,
                                   OnItemClickedListener onItemClicked) {
        super(itemView);
        title = itemView.findViewById(R.id.story_title);
        comments = itemView.findViewById(R.id.story_comments);
        pocket = itemView.findViewById(R.id.pocket);

        pocket.setVisibility(pocketIsInstalled ? View.VISIBLE : View.GONE);
        if (pocketIsInstalled) {
            pocket.setImageAlpha(178);
            pocket.setOnClickListener(v -> {
                if (story != null) {
                    onPocketClicked.onPocketClicked(story, getAdapterPosition());
                }
            });
        }

        comments.setOnClickListener(v -> {
            if (story != null) {
                TransitionData data = new TransitionData(story, getAdapterPosition(), title,
                        getSharedElementsForTransition(), itemView);
                onCommentsClicked.onCommentsClicked(data);
            }
        });

        itemView.setOnClickListener(v -> {
            if (story != null) {
                onItemClicked.onItemClicked(story);
            }
        });
    }

    public void bind(Story story) {
        this.story = story;
        title.setText(story.getTitle());
        title.setAlpha(1f);
        comments.setText(String.valueOf(story.getCommentCount()));
        itemView.setTransitionName(story.getUrl());
    }

    private Pair<View, String>[] getSharedElementsForTransition() {
        String[] transitionNames = itemView.getContext().getResources().getStringArray(R.array.transition_story);
        return new Pair[]{
                Pair.create((View) title, transitionNames[0]),
                Pair.create(itemView, transitionNames[1]),
                Pair.create(itemView, transitionNames[2])
        };
    }

    public Animator createAddToPocketAnimator() {
        ((ViewGroup) pocket.getParent().getParent()).setClipChildren(false);
        int initialLeft = pocket.getLeft();
        int initialTop = pocket.getTop();
        int translatedLeft = (itemView.getWidth() - pocket.getWidth()) / 2;
        int translatedTop = initialTop - (itemView.getHeight() - pocket.getHeight()) / 2;
        GravityArcMotion arc = new GravityArcMotion();

        ObjectAnimator titleMoveFadeOut = ObjectAnimator.ofPropertyValuesHolder(
                title,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -(itemView.getHeight() / 5)),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.54f));

        ObjectAnimator pocketMoveUp = ObjectAnimator.ofFloat(pocket,
                View.TRANSLATION_X, View.TRANSLATION_Y,
                arc.getPath(initialLeft, initialTop, translatedLeft, translatedTop));
        ObjectAnimator pocketScaleUp = ObjectAnimator.ofPropertyValuesHolder(pocket,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 3f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 3f));
        ObjectAnimator pocketFadeUp = ObjectAnimator.ofInt(pocket,
                ViewUtils.IMAGE_ALPHA, 255);

        AnimatorSet up = new AnimatorSet();
        up.playTogether(titleMoveFadeOut, pocketMoveUp, pocketScaleUp, pocketFadeUp);
        up.setDuration(300L);
        up.setInterpolator(AnimUtils.getFastOutSlowInInterpolator(itemView.getContext()));

        ObjectAnimator titleMoveFadeIn = ObjectAnimator.ofPropertyValuesHolder(title,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f));
        ObjectAnimator pocketMoveDown = ObjectAnimator.ofFloat(pocket,
                View.TRANSLATION_X, View.TRANSLATION_Y,
                arc.getPath(translatedLeft, translatedTop, 0, 0));
        ObjectAnimator pocketScaleDown = ObjectAnimator.ofPropertyValuesHolder(pocket,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f));
        ObjectAnimator pocketFadeDown = ObjectAnimator.ofInt(pocket,
                ViewUtils.IMAGE_ALPHA, 178);

        AnimatorSet down = new AnimatorSet();
        down.playTogether(titleMoveFadeIn, pocketMoveDown, pocketScaleDown, pocketFadeDown);
        down.setStartDelay(500L);
        down.setDuration(300L);
        down.setInterpolator(AnimUtils.getFastOutSlowInInterpolator(itemView.getContext()));

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playSequentially(up, down);

        animatorSet.doOnEnd(animator -> ((ViewGroup) pocket.getParent().getParent()).setClipChildren(true));
        animatorSet.doOnCancel(animator -> {
            title.setAlpha(1f);
            title.setTranslationY(0f);

            pocket.setTranslationX(0f);
            pocket.setTranslationY(0f);
            pocket.setScaleX(1f);
            pocket.setScaleY(1f);
            pocket.setImageAlpha(178);
        });

        return animatorSet;
    }

    public Animator createStoryCommentReturnAnimator() {
        AnimatorSet animator = new AnimatorSet();
        animator.playTogether(
                ObjectAnimator.ofFloat(pocket, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(comments, View.ALPHA, 0f, 1f));
        animator.setDuration(120L);
        animator.setInterpolator(AnimUtils.getLinearOutSlowInInterpolator(itemView.getContext()));
        animator.doOnCancel(animator1 -> {
            pocket.setAlpha(1f);
            comments.setAlpha(1f);
        });
        return animator;
    }

    public interface OnPocketClickedListener {
        void onPocketClicked(Story story, int adapterPosition);
    }

    public interface OnCommentsClickedListener {
        void onCommentsClicked(TransitionData data);
    }

    public interface OnItemClickedListener {
        void onItemClicked(Story story);
    }

    public static class TransitionData {
        private final Story story;
        private final int position;
        private final BaselineGridTextView title;
        private final Pair<View, String>[] sharedElements;
        private final View itemView;

        public TransitionData(Story story, int position, BaselineGridTextView title,
                              Pair<View, String>[] sharedElements, View itemView) {
            this.story = story;
            this.position = position;
            this.title = title;
            this.sharedElements = sharedElements;
            this.itemView = itemView;
        }

        public Story getStory() {
            return story;
        }

        public int getPosition() {
            return position;
        }

        public BaselineGridTextView getTitle() {
            return title;
        }

        public Pair<View, String>[] getSharedElements() {
            return sharedElements;
        }

        public View getItemView() {
            return itemView;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TransitionData that = (TransitionData) o;

            if (position != that.position) return false;
            if (!title.equals(that.title)) return false;
            // Probably incorrect - comparing Object[] arrays with Arrays.equals
            if (!Arrays.equals(sharedElements, that.sharedElements)) return false;
            return itemView.equals(that.itemView);
        }

        @Override
        public int hashCode() {
            int result = position;
            result = 31 * result + title.hashCode();
            result = 31 * result + Arrays.hashCode(sharedElements);
            result = 31 * result + itemView.hashCode();
            return result;
        }
    }
}