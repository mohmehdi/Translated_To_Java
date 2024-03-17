

package com.squareup.leakcanary.internal;

import android.app.Activity;
import android.app.Application;
import leaksentry.internal.ActivityLifecycleCallbacksAdapter;

public class VisibilityTracker extends ActivityLifecycleCallbacksAdapter {
    private int startedActivityCount = 0;
    private boolean hasVisibleActivities = false;
    private final Listener listener;

    public VisibilityTracker(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onActivityStarted(Activity activity) {
        startedActivityCount++;
        if (!hasVisibleActivities && startedActivityCount == 1) {
            hasVisibleActivities = true;
            listener.invoke(true);
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        if (startedActivityCount > 0) {
            startedActivityCount--;
        }
        if (hasVisibleActivities && startedActivityCount == 0 && !activity.isChangingConfigurations()) {
            hasVisibleActivities = false;
            listener.invoke(false);
        }
    }


    public static void registerVisibilityListener(Application application, Listener listener) {
        application.registerActivityLifecycleCallbacks(new VisibilityTracker(listener));
    }
}