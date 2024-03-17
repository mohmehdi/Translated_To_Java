

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

        public Config(boolean watchActivities, boolean watchFragments, boolean watchFragmentViews, long watchDurationMillis) {
            this.watchActivities = watchActivities;
            this.watchFragments = watchFragments;
            this.watchFragmentViews = watchFragmentViews;
            this.watchDurationMillis = watchDurationMillis;
        }

        public Config() {
        }
    }

    @Volatile
    public static Config config = new Config();

    public static RefWatcher refWatcher() {
        return InternalLeakSentry.refWatcher;
    }

    public static void manualInstall(Application application) {
        InternalLeakSentry.install(application);
    }
}