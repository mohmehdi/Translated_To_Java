

package leakcanary;

import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import com.squareup.leakcanary.instrumentation.test.R;

public class TestActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
    }
}