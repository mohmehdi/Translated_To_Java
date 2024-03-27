
package com.squareup.leakcanary.internal;

import android.app.Activity;
import android.app.Application;
import leaksentry.internal.ActivityLifecycleCallbacksAdapter;

internal class VisibilityTracker {

  private int startedActivityCount = 0;
  private boolean hasVisibleActivities = false;
  private final (Boolean) -> Unit listener;

  public VisibilityTracker((Boolean) -> Unit listener) {
    this.listener = listener;
  }

  public void onActivityStarted(Activity activity) {
    startedActivityCount++;
    if (!hasVisibleActivities && startedActivityCount == 1) {
      hasVisibleActivities = true;
      listener.invoke(true);
    }
  }

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

internal void registerVisibilityListener(Application application, (Boolean) -> Unit listener) {
  application.registerActivityLifecycleCallbacks(new VisibilityTracker(listener));
}