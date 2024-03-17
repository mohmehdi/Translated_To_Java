

package leakcanary;

import android.app.Application;
import leakcanary.InstrumentationLeakDetector;

public class InstrumentationTestApplication extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    InstrumentationLeakDetector.updateConfig();
  }
}