

package leaksentry.internal;

import android.app.Activity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import leaksentry.RefWatcher;
import leaksentry.Config;

public class SupportFragmentDestroyWatcher implements FragmentDestroyWatcher {

    private final RefWatcher refWatcher;
    private final Config configProvider;

    public SupportFragmentDestroyWatcher(RefWatcher refWatcher, Config configProvider) {
        this.refWatcher = refWatcher;
        this.configProvider = configProvider;
    }

    private final FragmentManager.FragmentLifecycleCallbacks fragmentLifecycleCallbacks = new FragmentManager.FragmentLifecycleCallbacks() {

        @Override
        public void onFragmentViewDestroyed(FragmentManager fm, Fragment fragment) {
            View view = fragment.getView();
            if (view != null && configProvider.watchFragmentViews()) {
                refWatcher.watch(view);
            }
        }

        @Override
        public void onFragmentDestroyed(FragmentManager fm, Fragment fragment) {
            if (configProvider.watchFragments()) {
                refWatcher.watch(fragment);
            }
        }
    };

    @Override
    public void watchFragments(Activity activity) {
        if (activity instanceof FragmentActivity) {
            FragmentActivity fragmentActivity = (FragmentActivity) activity;
            FragmentManager supportFragmentManager = fragmentActivity.getSupportFragmentManager();
            supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, true);
        }
    }
}