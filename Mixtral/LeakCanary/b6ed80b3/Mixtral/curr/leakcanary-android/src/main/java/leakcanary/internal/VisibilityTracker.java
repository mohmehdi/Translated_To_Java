

package leakcanary.internal;

import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;

public class VisibilityTracker implements ActivityLifecycleCallbacks {
    private int startedActivityCount = 0;
    private boolean hasVisibleActivities;
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


}

internal static void registerVisibilityListener(Application application, VisibilityTracker.Listener listener) {
    application.registerActivityLifecycleCallbacks(new VisibilityTracker(listener));
}