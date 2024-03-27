

package leaksentry.internal;

import android.app.Activity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import leaksentry.RefWatcher;
import leaksentry.LeakSentry.Config;

public class SupportFragmentDestroyWatcher implements FragmentDestroyWatcher {

  private RefWatcher refWatcher;
  private ConfigProvider configProvider;

  private FragmentManager.FragmentLifecycleCallbacks fragmentLifecycleCallbacks = new FragmentManager.FragmentLifecycleCallbacks() {

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
    if (activity instanceof FragmentActivity) {
      FragmentActivity fragmentActivity = (FragmentActivity) activity;
      FragmentManager supportFragmentManager = fragmentActivity.getSupportFragmentManager();
      supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, true);
    }
  }
}