

package com.squareup.leakcanary.internal;

import android.app.Application;
import leaksentry.AbstractLeakSentryReceiver;

public class LeakCanaryReceiver extends AbstractLeakSentryReceiver {
  @Override
  public void onLeakSentryInstalled(Application application) {
    com.squareup.leakcanary.internal.InternalLeakCanary.onLeakSentryInstalled(application);
  }

  @Override
  public void onReferenceRetained() {
    com.squareup.leakcanary.internal.InternalLeakCanary.onReferenceRetained();
  }
}