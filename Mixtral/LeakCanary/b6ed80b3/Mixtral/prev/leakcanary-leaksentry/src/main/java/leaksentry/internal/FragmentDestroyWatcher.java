

package leaksentry.internal;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import leaksentry.RefWatcher;
import leaksentry.LeakSentry;
import leaksentry.LeakSentry.Config;
import java.lang.reflect.Class;
import java.util.ArrayList;
import java.util.List;

public class FragmentDestroyWatcher {

    public interface FragmentDestroyWatcher {
        void watchFragments(Activity activity);
    }

    private static final String SUPPORT_FRAGMENT_CLASS_NAME = "androidx.fragment.app.Fragment";

    public static void install(
            Application application,
            RefWatcher refWatcher,
            ConfigProvider configProvider) {
        List<FragmentDestroyWatcher> fragmentDestroyWatchers = new ArrayList<>();

        if (VERSION.SDK_INT >= CODES.O) {
            fragmentDestroyWatchers.add(new AndroidOFragmentDestroyWatcher(refWatcher, configProvider));
        }

        if (classAvailable(SUPPORT_FRAGMENT_CLASS_NAME)) {
            fragmentDestroyWatchers.add(new SupportFragmentDestroyWatcher(refWatcher, configProvider));
        }

        if (fragmentDestroyWatchers.size() == 0) {
            return;
        }

        application.registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacksAdapter() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                for (FragmentDestroyWatcher watcher : fragmentDestroyWatchers) {
                    watcher.watchFragments(activity);
                }
            }
        });
    }

    private static boolean classAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}