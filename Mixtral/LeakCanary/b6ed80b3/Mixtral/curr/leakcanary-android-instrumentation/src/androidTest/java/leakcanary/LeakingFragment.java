

package leakcanary;

import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.vtest.runner.AndroidJUnitRunner;

public class LeakingFragment extends Fragment {
    private static LeakingFragment leakingFragment;

    public static void add(TestActivity activity) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                leakingFragment = new LeakingFragment();
                activity.getSupportFragmentManager()
                        .beginTransaction()
                        .add(0, leakingFragment)
                        .commitNow();
            }
        });
    }
}