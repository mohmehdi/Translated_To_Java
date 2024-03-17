

package leakcanary.internal;

import android.app.Activity;
import android.app.Application;
import leakcanary.RefWatcher;
import leakcanary.LeakSentry.Config;
import leakcanary.activity.ActivityLifecycleCallbacksAdapter;

public class ActivityDestroyWatcher {
    private final RefWatcher refWatcher;
    private final Config config;

    private final ActivityLifecycleCallbacksAdapter lifecycleCallbacks = new ActivityLifecycleCallbacksAdapter() {
        @Override
        public void onActivityDestroyed(Activity activity) {
            if (config.watchActivities) {
                refWatcher.watch(activity);
            }
        }
    };

    private ActivityDestroyWatcher(RefWatcher refWatcher, Config config) {
        this.refWatcher = refWatcher;
        this.config = config;
    }

    public static void install(
            Application application,
            RefWatcher refWatcher,
            ConfigProvider configProvider) {
        ActivityDestroyWatcher activityDestroyWatcher =
                new ActivityDestroyWatcher(refWatcher, configProvider.get());
        application.registerActivityLifecycleCallbacks(activityDestroyWatcher.lifecycleCallbacks);
    }

}