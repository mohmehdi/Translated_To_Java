

package leakcanary.internal;

import android.app.Activity;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import leakcanary.RefWatcher;
import leakcanary.LeakSentry.Config;

public class SupportFragmentDestroyWatcher implements FragmentDestroyWatcher {

    private final RefWatcher refWatcher;
    private final ConfigProvider configProvider;

    private final FragmentManager.FragmentLifecycleCallbacks fragmentLifecycleCallbacks =
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentViewDestroyed(@NonNull FragmentManager fm, @NonNull Fragment fragment) {
                    View view = fragment.getView();
                    if (view != null && configProvider.getConfig().watchFragmentViews) {
                        refWatcher.watch(view);
                    }
                }

                @Override
                public void onFragmentDestroyed(@NonNull FragmentManager fm, @NonNull Fragment fragment) {
                    if (configProvider.getConfig().watchFragments) {
                        refWatcher.watch(fragment);
                    }
                }
            };

    public SupportFragmentDestroyWatcher(
            @NonNull RefWatcher refWatcher,
            @NonNull ConfigProvider configProvider) {
        this.refWatcher = refWatcher;
        this.configProvider = configProvider;
    }

    @Override
    public void watchFragments(@NonNull Activity activity) {
        if (activity instanceof FragmentActivity) {
            FragmentActivity fragmentActivity = (FragmentActivity) activity;
            FragmentManager supportFragmentManager = fragmentActivity.getSupportFragmentManager();
            supportFragmentManager.registerFragmentLifecycleCallbacks(
                    fragmentLifecycleCallbacks, true);
        }
    }
}