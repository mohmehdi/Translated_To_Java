

package leakcanary;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.test.platform.app.InstrumentationRegistry;
import com.squareup.leakcanary.instrumentation.test.R;

public class ViewLeakingFragment extends Fragment {

  private View leakingView;

  @Override
  public View onCreateView(
    LayoutInflater inflater,
    ViewGroup container,
    Bundle savedInstanceState
  ) {
    return new View(container.getContext());
  }

  @Override
  public void onViewCreated(
    View view,
    Bundle savedInstanceState
  ) {
    leakingView = view;
  }

  public static void addToBackstack(TestActivity activity) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
      activity.getSupportFragmentManager()
          .beginTransaction()
          .addToBackStack(null)
          .replace(R.id.fragments, new ViewLeakingFragment())
          .commit();
    });
  }
}