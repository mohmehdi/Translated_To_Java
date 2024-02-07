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
import androidx.core.animation.DoOnCancel;
import androidx.core.animation.DoOnEnd;
import androidx.core.view.ViewCompat;
import java.util.Arrays;

public class DesignerNewsStoryHolder
  extends RecyclerView.ViewHolder
  implements Divided {

  private Story story;
  private final BaselineGridTextView title;
  private final TextView comments;
  private final ImageButton pocket;
  private final boolean pocketIsInstalled;
  private final OnPocketClicked onPocketClicked;
  private final OnCommentsClicked onCommentsClicked;
  private final OnItemClicked onItemClicked;

  public DesignerNewsStoryHolder(
    View itemView,
    boolean pocketIsInstalled,
    OnPocketClicked onPocketClicked,
    OnCommentsClicked onCommentsClicked,
    OnItemClicked onItemClicked
  ) {
    super(itemView);
    this.pocketIsInstalled = pocketIsInstalled;
    this.onPocketClicked = onPocketClicked;
    this.onCommentsClicked = onCommentsClicked;
    this.onItemClicked = onItemClicked;
    title = itemView.findViewById(R.id.story_title);
    comments = itemView.findViewById(R.id.story_comments);
    pocket = itemView.findViewById(R.id.pocket);

    if (pocketIsInstalled) {
      pocket.setVisibility(View.VISIBLE);
      pocket.setImageAlpha(178);
      pocket.setOnClickListener(v ->
        story != null &&
        onPocketClicked.onPocketClicked(story, getAdapterPosition())
      );
    } else {
      pocket.setVisibility(View.GONE);
    }

    comments.setOnClickListener(v -> {
      if (story != null) {
        TransitionData data = new TransitionData(
          story,
          getAdapterPosition(),
          title,
          getSharedElementsForTransition(),
          itemView
        );
        onCommentsClicked.onCommentsClicked(data);
      }
    });

    itemView.setOnClickListener(v ->
      story != null && onItemClicked.onItemClicked(story)
    );
  }

  public void bind(Story story) {
    this.story = story;
    title.setText(story.getTitle());
    title.setAlpha(1f);
    comments.setText(String.valueOf(story.getCommentCount()));
    itemView.setTransitionName(story.getUrl());
  }

  private Pair<View, String>[] getSharedElementsForTransition() {
    Context context = itemView.getContext();
    View[] sharedElements = new View[] { title, itemView, itemView };
    String[] sharedElementNames = new String[] {
      context.getString(R.string.transition_story_title),
      context.getString(R.string.transition_story_title_background),
      context.getString(R.string.transition_story_background),
    };
    return Pair.create(sharedElements, sharedElementNames);
  }

  public Animator createAddToPocketAnimator() {
    ViewGroup parent = (ViewGroup) pocket.getParent().getParent();
    int initialLeft = pocket.getLeft();
    int initialTop = pocket.getTop();
    int translatedLeft = (itemView.getWidth() - pocket.getWidth()) / 2;
    int translatedTop =
      initialTop - (itemView.getHeight() - pocket.getHeight()) / 2;
    GravityArcMotion arc = new GravityArcMotion();

    ObjectAnimator titleMoveFadeOut = ObjectAnimator.ofPropertyValuesHolder(
      title,
      PropertyValuesHolder.ofFloat(
        View.TRANSLATION_Y,
        -(itemView.getHeight() / 5f)
      ),
      PropertyValuesHolder.ofFloat(View.ALPHA, 0.54f)
    );

    ObjectAnimator pocketMoveUp = ObjectAnimator.ofFloat(
      pocket,
      View.TRANSLATION_X,
      View.TRANSLATION_Y,
      arc.getPath(initialLeft, initialTop, translatedLeft, translatedTop)
    );
    ObjectAnimator pocketScaleUp = ObjectAnimator.ofPropertyValuesHolder(
      pocket,
      PropertyValuesHolder.ofFloat(View.SCALE_X, 3f),
      PropertyValuesHolder.ofFloat(View.SCALE_Y, 3f)
    );
    ObjectAnimator pocketFadeUp = ObjectAnimator.ofInt(
      pocket,
      ViewCompat.getImageAlpha(pocket),
      255
    );

    AnimatorSet up = new AnimatorSet();
    up.playTogether(
      titleMoveFadeOut,
      pocketMoveUp,
      pocketScaleUp,
      pocketFadeUp
    );
    up.setDuration(300L);
    up.setInterpolator(
      AnimUtils.getFastOutSlowInInterpolator(itemView.getContext())
    );

    ObjectAnimator titleMoveFadeIn = ObjectAnimator.ofPropertyValuesHolder(
      title,
      PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f),
      PropertyValuesHolder.ofFloat(View.ALPHA, 1f)
    );
    ObjectAnimator pocketMoveDown = ObjectAnimator.ofFloat(
      pocket,
      View.TRANSLATION_X,
      View.TRANSLATION_Y,
      arc.getPath(translatedLeft, translatedTop, 0f, 0f)
    );
    ObjectAnimator pvhPocketScaleDown = ObjectAnimator.ofPropertyValuesHolder(
      pocket,
      PropertyValuesHolder.ofFloat(View.SCALE_X, 1f),
      PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f)
    );
    ObjectAnimator pocketFadeDown = ObjectAnimator.ofInt(
      pocket,
      ViewCompat.getImageAlpha(pocket),
      178
    );

    AnimatorSet down = new AnimatorSet();
    down.playTogether(
      titleMoveFadeIn,
      pocketMoveDown,
      pvhPocketScaleDown,
      pocketFadeDown
    );
    down.setStartDelay(500L);
    down.setDuration(300L);
    down.setInterpolator(
      AnimUtils.getFastOutSlowInInterpolator(itemView.getContext())
    );

    AnimatorSet animatorSet = new AnimatorSet();
    animatorSet.playSequentially(up, down);

    animatorSet.doOnEnd(
      new DoOnEnd() {
        @Override
        public void onEnd(Animator animation) {
          parent.setClipChildren(true);
        }
      }
    );

    animatorSet.doOnCancel(
      new DoOnCancel() {
        @Override
        public void onCancel(Animator animation) {
          title.setAlpha(1f);
          title.setTranslationY(0f);
          pocket.setTranslationX(0f);
          pocket.setTranslationY(0f);
          pocket.setScaleX(1f);
          pocket.setScaleY(1f);
          ViewCompat.setImageAlpha(pocket, 178);
        }
      }
    );

    return animatorSet;
  }

  public Animator createStoryCommentReturnAnimator() {
    AnimatorSet animator = new AnimatorSet();
    animator.playTogether(
      ObjectAnimator.ofFloat(pocket, View.ALPHA, 0f, 1f),
      ObjectAnimator.ofFloat(comments, View.ALPHA, 0f, 1f)
    );
    animator.setDuration(120L);
    animator.setInterpolator(
      AnimUtils.getLinearOutSlowInInterpolator(itemView.getContext())
    );
    animator.doOnCancel(() -> {
      ViewCompat.setAlpha(pocket, 1f);
      ViewCompat.setAlpha(comments, 1f);
    });
    return animator;
  }

  public static class TransitionData {

    private final Story story;
    private final int position;
    private final BaselineGridTextView title;
    private final Pair<View, String>[] sharedElements;
    private final View itemView;

    public TransitionData(
      Story story,
      int position,
      BaselineGridTextView title,
      Pair<View, String>[] sharedElements,
      View itemView
    ) {
      this.story = story;
      this.position = position;
      this.title = title;
      this.sharedElements = sharedElements;
      this.itemView = itemView;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;

      TransitionData transitionData = (TransitionData) other;

      if (position != transitionData.position) return false;
      if (!title.equals(transitionData.title)) return false;
      if (
        !Arrays.equals(sharedElements, transitionData.sharedElements)
      ) return false;
      return itemView.equals(transitionData.itemView);
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
