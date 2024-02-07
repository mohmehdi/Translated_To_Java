package io.plaidapp.core.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsSession;
import android.support.v4.content.ContextCompat;
import io.plaidapp.core.BuildConfig;
import io.plaidapp.core.R;
import io.plaidapp.core.designernews.data.stories.model.Story;
import io.plaidapp.core.designernews.data.votes.UpvoteStoryService;
import java.util.Objects;

public class ActivityHelper {

  private static final String PACKAGE_NAME = "io.plaidapp";

  public static Intent intentTo(AddressableActivity addressableActivity) {
    return new Intent(Intent.ACTION_VIEW)
      .setClassName(BuildConfig.APPLICATION_ID, addressableActivity.className);
  }

  public interface AddressableActivity {}

  public static class Activities {

    public static class About implements AddressableActivity {

      private static final String className =
        "$PACKAGE_NAME.ui.about.AboutActivity";
    }

    public static class DesignerNews {

      public static class Login implements AddressableActivity {

        private static final String className =
          "$PACKAGE_NAME.designernews.ui.login.DesignerNewsLogin";
      }

      public static class Story implements AddressableActivity {

        private static final String className =
          "$PACKAGE_NAME.designernews.ui.story.DesignerNewsStory";
        public static final String EXTRA_STORY = "story";

        public static CustomTabsIntent.Builder customTabIntent(
          Context context,
          Story story,
          CustomTabsSession session
        ) {
          Intent upvoteStory = new Intent(context, UpvoteStoryService.class);
          upvoteStory.setAction(UpvoteStoryService.ACTION_UPVOTE);
          upvoteStory.putExtra(
            UpvoteStoryService.EXTRA_STORY_ID,
            story.getId()
          );
          PendingIntent pendingIntent = PendingIntent.getService(
            context,
            0,
            upvoteStory,
            0
          );

          CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder(
            session
          );
          builder.setToolbarColor(
            ContextCompat.getColor(context, R.color.designer_news)
          );
          builder.setActionButton(
            drawableToBitmap(context, R.drawable.ic_upvote_filled_24dp_white),
            context.getString(R.string.upvote_story),
            pendingIntent,
            false
          );
          builder.setShowTitle(true);
          builder.enableUrlBarHiding();
          builder.addDefaultShareMenuItem();

          return builder;
        }
      }

      public static class PostStory implements AddressableActivity {

        private static final String className =
          "$PACKAGE_NAME.ui.designernews.PostNewDesignerNewsStory";
        public static final int RESULT_DRAG_DISMISSED = 3;
        public static final int RESULT_POSTING = 4;
      }
    }

    public static class Dribbble {

      public static class Shot implements AddressableActivity {

        private static final String className =
          "$PACKAGE_NAME.dribbble.ui.DribbbleShot";
      }
    }

    public static class Search implements AddressableActivity {

      private static final String className =
        "$PACKAGE_NAME.ui.search.SearchActivity";
    }
  }
}
