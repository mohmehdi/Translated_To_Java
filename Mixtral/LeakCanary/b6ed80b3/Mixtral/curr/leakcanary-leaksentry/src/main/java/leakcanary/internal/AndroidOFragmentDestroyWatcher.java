

package leakcanary.internal;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Build;
import leakcanary.RefWatcher;
import leakcanary.Config;

import java.util.Objects;

@SuppressWarnings("deprecation")
@androidx.annotation.RequiresApi(api = Build.VERSION_CODES.O)
class AndroidOFragmentDestroyWatcher implements FragmentDestroyWatcher {

    private final RefWatcher refWatcher;
    private final Config config;

    private final FragmentManager.FragmentLifecycleCallbacks fragmentLifecycleCallbacks =
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentViewDestroyed(FragmentManager fm, Fragment fragment) {
                    Objects.requireNonNull(fragment).getView();
                    if (config.watchFragmentViews) {
                        refWatcher.watch(fragment.getView());
                    }
                }

                @Override
                public void onFragmentDestroyed(FragmentManager fm, Fragment fragment) {
                    if (config.watchFragments) {
                        refWatcher.watch(fragment);
                    }
                }
            };

    AndroidOFragmentDestroyWatcher(RefWatcher refWatcher, Config configProvider) {
        this.refWatcher = refWatcher;
        this.config = configProvider;
    }

    @Override
    public void watchFragments(Activity activity) {
        FragmentManager fragmentManager = activity.getFragmentManager();
        fragmentManager.registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, true);
    }
}