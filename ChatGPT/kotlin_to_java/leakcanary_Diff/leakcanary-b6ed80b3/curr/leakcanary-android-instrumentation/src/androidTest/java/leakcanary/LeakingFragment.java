

package leakcanary;

import androidx.fragment.app.Fragment;

import androidx.test.platform.app.InstrumentationRegistry;

public class LeakingFragment extends Fragment {

  private static LeakingFragment leakingFragment;

  public static void add(TestActivity activity) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      leakingFragment = new LeakingFragment();
      activity.getSupportFragmentManager()
          .beginTransaction()
          .add(0, leakingFragment)
          .commitNow();
    });
  }
}