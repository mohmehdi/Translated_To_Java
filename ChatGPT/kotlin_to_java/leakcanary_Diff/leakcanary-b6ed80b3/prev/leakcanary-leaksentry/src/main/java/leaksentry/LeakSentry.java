
package leaksentry;

import android.app.Application;
import leaksentry.internal.InternalLeakSentry;
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