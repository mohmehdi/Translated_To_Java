package io.plaidapp.core.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsSession;
import android.support.v4.content.ContextCompat;

import io.plaidapp.core.BuildConfig;
import io.plaidapp.core.R;
import io.plaidapp.core.designernews.data.votes.UpvoteStoryService;
import io.plaidapp.core.designernews.data.stories.model.Story;

public class ActivityHelper {

    private static final String PACKAGE_NAME = "io.plaidapp";

    public static Intent intentTo(AddressableActivity addressableActivity) {
        return new Intent(Intent.ACTION_VIEW).setClassName(
                BuildConfig.PACKAGE,
                addressableActivity.getClassName());
    }

    public interface AddressableActivity {
        String getClassName();
    }

    public static class Activities {

        public static class About implements AddressableActivity {
            @Override
            public String getClassName() {
                return PACKAGE_NAME + ".ui.about.AboutActivity";
            }
        }

        public static class DesignerNews {

            public static class Login implements AddressableActivity {
                @Override
                public String getClassName() {
                    return PACKAGE_NAME + ".designernews.ui.login.DesignerNewsLogin";
                }
            }

            public static class Story implements AddressableActivity {
                @Override
                public String getClassName() {
                    return PACKAGE_NAME + ".designernews.ui.story.DesignerNewsStory";
                }

                public static final String EXTRA_STORY = "story";

                public static CustomTabsIntent.Builder customTabIntent(
                        Context context,
                        Story story,
                        CustomTabsSession session) {
                    Intent upvoteStory = new Intent(context, UpvoteStoryService.class);
                    upvoteStory.setAction(UpvoteStoryService.ACTION_UPVOTE);
                    upvoteStory.putExtra(UpvoteStoryService.EXTRA_STORY_ID, story.getId());
                    PendingIntent pendingIntent = PendingIntent.getService(context, 0, upvoteStory, 0);

                    return new CustomTabsIntent.Builder(session)
                            .setToolbarColor(ContextCompat.getColor(context, R.color.designer_news))
                            .setActionButton(drawableToBitmap(context,
                                    R.drawable.ic_upvote_filled_24dp_white),
                                    context.getString(R.string.upvote_story),
                                    pendingIntent,
                                    false)
                            .setShowTitle(true)
                            .enableUrlBarHiding()
                            .addDefaultShareMenuItem();
                }
            }

            public static class PostStory implements AddressableActivity {
                @Override
                public String getClassName() {
                    return PACKAGE_NAME + ".ui.designernews.PostNewDesignerNewsStory";
                }

                public static final int RESULT_DRAG_DISMISSED = 3;
                public static final int RESULT_POSTING = 4;
            }
        }

        public static class Dribbble {

            public static class Shot implements AddressableActivity {
                @Override
                public String getClassName() {
                    return PACKAGE_NAME + ".dribbble.ui.DribbbleShot";
                }

                public static final String EXTRA_SHOT = "EXTRA_SHOT";
                public static final String RESULT_EXTRA_SHOT_ID = "RESULT_EXTRA_SHOT_ID";
            }
        }

        public static class Search implements AddressableActivity {
            @Override
            public String getClassName() {
                return PACKAGE_NAME + ".ui.search.SearchActivity";
            }

            public static final String EXTRA_QUERY = "EXTRA_QUERY";
            public static final String EXTRA_SAVE_DRIBBBLE = "EXTRA_SAVE_DRIBBBLE";
            public static final String EXTRA_SAVE_DESIGNER_NEWS = "EXTRA_SAVE_DESIGNER_NEWS";
            public static final int RESULT_CODE_SAVE = 7;
        }
    }
}