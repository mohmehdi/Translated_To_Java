
//⚠!#!
//--------------------Class--------------------
//-------------------Functions-----------------
//Functions extra in Java:
//+ getRefWatcher()

//-------------------Extra---------------------
//---------------------------------------------
package leakcanary;

import android.app.Application;
import leakcanary.internal.InternalLeakSentry;

import java.util.concurrent.TimeUnit;

public class LeakSentry {

  public static class Config {
    public boolean watchActivities = true;
    public boolean watchFragments = true;
    public boolean watchFragmentViews = true;
    public long watchDurationMillis = TimeUnit.SECONDS.toMillis(5);
  }

  public static volatile Config config = new Config();


  public static void manualInstall(Application application) {
    InternalLeakSentry.install(application);
  }

}