

package com.example.leakcanary.tests;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.rule.ActivityTestRule;
import com.example.leakcanary.MainActivity;
import com.example.leakcanary.R;
import org.junit.Rule;
import org.junit.Test;

public class TuPeuxPasTest {

  @Rule
  public ActivityTestRule<MainActivity> activityRule = new ActivityTestRule<>(MainActivity.class);

  @Test
  public void clickAsyncWork() {
    Espresso.onView(ViewMatchers.withId(R.id.async_work)).perform(ViewActions.click());
  }

  @Test
  public void asyncButtonHasStartText() {
    Espresso.onView(ViewMatchers.withId(R.id.async_work)).check(ViewAssertions.matches(ViewMatchers.withText(R.string.start_async_work)));
  }
}