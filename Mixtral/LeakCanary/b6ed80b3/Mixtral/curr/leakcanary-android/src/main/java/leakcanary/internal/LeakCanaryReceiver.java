

package leakcanary.internal;

import android.app.Application;
import leakcanary.AbstractLeakSentryReceiver;

public class LeakCanaryReceiver extends AbstractLeakSentryReceiver {
  @Override
  public void onLeakSentryInstalled(Application application) {
    InternalLeakCanary.onLeakSentryInstalled(application);
  }

  @Override
  public void onReferenceRetained() {
    InternalLeakCanary.onReferenceRetained();
  }
}