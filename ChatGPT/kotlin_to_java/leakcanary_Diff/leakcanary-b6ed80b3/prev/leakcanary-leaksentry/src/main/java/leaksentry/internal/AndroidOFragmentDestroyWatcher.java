
package leaksentry.internal;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Build;
import androidx.annotation.RequiresApi;
import leaksentry.RefWatcher;
import leaksentry.LeakSentry.Config;

@RequiresApi(api = Build.VERSION_CODES.O)
class AndroidOFragmentDestroyWatcher implements FragmentDestroyWatcher {

    private final RefWatcher refWatcher;
    private final ConfigProvider configProvider;

    AndroidOFragmentDestroyWatcher(RefWatcher refWatcher, ConfigProvider configProvider) {
        this.refWatcher = refWatcher;
        this.configProvider = configProvider;
    }

    private final FragmentManager.FragmentLifecycleCallbacks fragmentLifecycleCallbacks = new FragmentManager.FragmentLifecycleCallbacks() {
        @Override
        public void onFragmentViewDestroyed(FragmentManager fm, Fragment fragment) {
            android.view.View view = fragment.getView();
            if (view != null && configProvider.getConfig().watchFragmentViews) {
                refWatcher.watch(view);
            }
        }

        @Override
        public void onFragmentDestroyed(FragmentManager fm, Fragment fragment) {
            if (configProvider.getConfig().watchFragments) {
                refWatcher.watch(fragment);
            }
        }
    };

    @Override
    public void watchFragments(Activity activity) {
        FragmentManager fragmentManager = activity.getFragmentManager();
        fragmentManager.registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, true);
    }

}