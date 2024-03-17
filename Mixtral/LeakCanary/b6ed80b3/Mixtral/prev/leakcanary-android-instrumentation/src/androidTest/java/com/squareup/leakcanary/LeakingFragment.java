

package com.squareup.leakcanary;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.test.platform.app.InstrumentationRegistry;

public class LeakingFragment extends Fragment {
  private static LeakingFragment leakingFragment;

  public static void add(@NonNull TestActivity activity) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
      @Override
      public void run() {
        leakingFragment = new LeakingFragment();
        FragmentManager supportFragmentManager = activity.getSupportFragmentManager();
        supportFragmentManager.beginTransaction()
            .add(0, leakingFragment)
            .commitNow();
      }
    });
  }

  static {
    leakingFragment = null;
  }
}