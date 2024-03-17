

package leaksentry.internal;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Build;
import leaksentry.RefWatcher;
import leaksentry.Config;

import java.util.Objects;

@SuppressWarnings("deprecation")
@androidx.annotation.RequiresApi(api = Build.VERSION_CODES.O)
internal class AndroidOFragmentDestroyWatcher implements FragmentDestroyWatcher {

    private final RefWatcher refWatcher;
    private final Config configProvider;

    private final FragmentManager.FragmentLifecycleCallbacks fragmentLifecycleCallbacks = new FragmentManager.FragmentLifecycleCallbacks() {
        @Override
        public void onFragmentViewDestroyed(FragmentManager fm, Fragment fragment) {
            if (fragment.getView() != null && configProvider.watchFragmentViews) {
                refWatcher.watch(fragment.getView());
            }
        }

        @Override
        public void onFragmentDestroyed(FragmentManager fm, Fragment fragment) {
            if (configProvider.watchFragments) {
                refWatcher.watch(fragment);
            }
        }
    };

    public AndroidOFragmentDestroyWatcher(RefWatcher refWatcher, Config configProvider) {
        this.refWatcher = Objects.requireNonNull(refWatcher);
        this.configProvider = Objects.requireNonNull(configProvider);
    }

    @Override
    public void watchFragments(Activity activity) {
        FragmentManager fragmentManager = activity.getFragmentManager();
        fragmentManager.registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, true);
    }
}