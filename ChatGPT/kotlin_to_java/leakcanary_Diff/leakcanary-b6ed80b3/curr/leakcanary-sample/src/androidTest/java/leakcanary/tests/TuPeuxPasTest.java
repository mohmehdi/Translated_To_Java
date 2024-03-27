

package leakcanary.tests;

import androidx.test.espresso.Espresso;
import androidx.test.rule.ActivityTestRule;
import com.example.leakcanary.MainActivity;
import com.example.leakcanary.R;
import org.junit.Rule;
import org.junit.Test;

import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

public class TuPeuxPasTest {

    @Rule
    public ActivityTestRule<MainActivity> activityRule = new ActivityTestRule<>(MainActivity.class);

    @Test
    public void clickAsyncWork() {
        Espresso.onView(withId(R.id.async_work)).perform(click());
    }

    @Test
    public void asyncButtonHasStartText() {
        Espresso.onView(withId(R.id.async_work)).check(matches(withText(R.string.start_async_work)));
    }
}