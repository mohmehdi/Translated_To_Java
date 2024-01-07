package com.google.samples.apps.sunflower;

import android.util.Log;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.ViewAssertion;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@HiltAndroidTest
public class GardenActivityTest {

    private final HiltAndroidRule hiltRule = new HiltAndroidRule(this);
    private final ActivityScenarioRule < GardenActivity > activityTestRule = new ActivityScenarioRule < > (GardenActivity.class);

    @Rule
    public RuleChain rule = RuleChain
        .outerRule(hiltRule)
        .around(activityTestRule);

    @Before
    public void setup() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config = new Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(new SynchronousExecutor())
            .build();

        WorkManagerTestInitHelper.initializeTestWorkManager(context, config);
    }

    @Test
    public void clickAddPlant_OpensPlantList() {
        ViewAction clickAction = ViewActions.click();
        ViewAssertion displayedAssertion = ViewMatchers.isDisplayed();

        Espresso.onView(ViewMatchers.withId(R.id.add_plant)).perform(clickAction);

        Espresso.onView(ViewMatchers.withId(R.id.plant_list)).check(displayedAssertion);
    }
}