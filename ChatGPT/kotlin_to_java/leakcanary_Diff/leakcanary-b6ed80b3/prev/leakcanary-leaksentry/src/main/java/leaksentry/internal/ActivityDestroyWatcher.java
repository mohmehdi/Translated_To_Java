
package leaksentry.internal;

import android.app.Activity;
import android.app.Application;
import leaksentry.RefWatcher;
import leaksentry.LeakSentry.Config;

public class ActivityDestroyWatcher {

    private RefWatcher refWatcher;
    private ConfigProvider configProvider;

    private ActivityLifecycleCallbacksAdapter lifecycleCallbacks = new ActivityLifecycleCallbacksAdapter() {
        @Override
        public void onActivityDestroyed(Activity activity) {
            if (configProvider.getConfig().watchActivities) {
                refWatcher.watch(activity);
            }
        }
    };

    private ActivityDestroyWatcher(RefWatcher refWatcher, ConfigProvider configProvider) {
        this.refWatcher = refWatcher;
        this.configProvider = configProvider;
    }

    public static void install(Application application, RefWatcher refWatcher, ConfigProvider configProvider) {
        ActivityDestroyWatcher activityDestroyWatcher = new ActivityDestroyWatcher(refWatcher, configProvider);
        application.registerActivityLifecycleCallbacks(activityDestroyWatcher.lifecycleCallbacks);
    }

}