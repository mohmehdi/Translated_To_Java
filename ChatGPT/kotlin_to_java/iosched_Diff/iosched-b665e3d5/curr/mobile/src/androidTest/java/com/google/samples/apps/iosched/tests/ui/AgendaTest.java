package com.google.samples.apps.iosched.tests.ui;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;

@RunWith(AndroidJUnit4.class)
public class AgendaTest {

    @Rule
    public ActivityTestRule activityRule = new ActivityTestRule<>(R.id.navigation_agenda);

    @Rule
    public SyncTaskExecutorRule syncTaskExecutorRule = new SyncTaskExecutorRule();

    @Rule
    public SetPreferencesRule preferencesRule = new SetPreferencesRule();

    @Test
    public void agenda_basicViewsDisplayed() {

        onView(allOf(withText(R.string.agenda), withId(R.id.title))).check(matches(isDisplayed()));

        onView(withText("Breakfast")).check(matches(isDisplayed()));
    }
}