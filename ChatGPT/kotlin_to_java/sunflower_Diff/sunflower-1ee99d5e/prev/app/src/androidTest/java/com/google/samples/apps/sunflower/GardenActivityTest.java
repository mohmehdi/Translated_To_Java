
package com.google.samples.apps.sunflower;

import android.util.Log;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

@HiltAndroidTest
public class GardenActivityTest {

    private final HiltAndroidRule hiltRule = new HiltAndroidRule(this);
    private final ActivityScenarioRule<GardenActivity> activityTestRule = new ActivityScenarioRule<>(GardenActivity.class);

    @Rule
    public final RuleChain rule = RuleChain
        .outerRule(hiltRule)
        .around(activityTestRule);

    @Before
    public void setup() {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config = new Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(new SynchronousExecutor())
            .build();

        WorkManagerTestInitHelper.initializeTestWorkManager(context, config);
    }

    @Test
    public void clickAddPlant_OpensPlantList() {
        Espresso.onView(ViewMatchers.withId(R.id.add_plant)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withId(R.id.plant_list)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }
}
