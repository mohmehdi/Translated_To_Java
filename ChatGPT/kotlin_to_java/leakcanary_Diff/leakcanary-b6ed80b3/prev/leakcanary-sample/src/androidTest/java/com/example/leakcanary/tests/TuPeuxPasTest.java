

package com.example.leakcanary.tests;

import androidx.test.rule.ActivityTestRule;
import org.junit.Rule;
import org.junit.Test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import com.example.leakcanary.MainActivity;
import com.example.leakcanary.R;

public class TuPeuxPasTest {

    @Rule
    public ActivityTestRule<MainActivity> activityRule = new ActivityTestRule<>(MainActivity.class);

    @Test
    public void clickAsyncWork() {
        onView(withId(R.id.async_work)).perform(click());
    }

    @Test
    public void asyncButtonHasStartText() {
        onView(withId(R.id.async_work)).check(matches(withText(R.string.start_async_work)));
    }
}