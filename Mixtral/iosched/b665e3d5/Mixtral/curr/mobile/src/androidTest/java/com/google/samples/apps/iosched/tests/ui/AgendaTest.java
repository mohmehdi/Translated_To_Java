

package com.google.samples.apps.iosched.tests.ui;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.SyncTaskExecutorRule;
import androidx.test.rule.SetPreferencesRule;

import com.google.samples.apps.iosched.R;

import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AgendaTest {

    @Rule
    public ActivityTestRule<MainActivity> activityRule = new ActivityTestRule<>(MainActivity.class, false, false);

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    @Rule
    public SetPreferencesRule preferencesRule = new SetPreferencesRule();

    @Test
    public void agenda_basicViewsDisplayed() {
        activityRule.launchActivity(null);

        Espresso.onView(CoreMatchers.allOf(ViewMatchers.withText(R.string.agenda), ViewMatchers.withId(R.id.title)))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Breakfast")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }
}