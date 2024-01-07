package com.google.samples.apps.sunflower;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.compose.ui.test.junit4.CreateAndroidComposeRule;
import androidx.compose.ui.test.onNodeWithTag;
import androidx.compose.ui.test.onNodeWithText;
import androidx.compose.ui.test.onRoot;
import androidx.compose.ui.test.performClick;
import androidx.compose.ui.test.printToLog;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Configuration;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.WorkManagerTestInitHelper;
import dagger.hilt.android.testing.HiltAndroidRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertNotNull;

@org.junit.runner.RunWith(androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner.class)
public class GardenActivityTest {

    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Rule
    public CreateAndroidComposeRule < GardenActivity > composeTestRule = new CreateAndroidComposeRule < > (GardenActivity.class);

    @Rule
    public RuleChain rule = RuleChain.outerRule(hiltRule).around(composeTestRule);

    @Before
    public void setup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() - > {
            androidx.work.WorkManager workManager = androidx.work.WorkManager.getInstance(InstrumentationRegistry.getInstrumentation().getTargetContext());
            Configuration config = new Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(new SynchronousExecutor())
            .build();

            WorkManagerTestInitHelper.initializeTestWorkManager(InstrumentationRegistry.getInstrumentation().getTargetContext(), config);
        });
    }

    @Test
    public void clickAddPlant_OpensPlantList() {
        // Given that no Plants are added to the user's garden

        // When the "Add Plant" button is clicked
        onView(withText("Add plant")).check(matches(isDisplayed())).perform(click());

        composeTestRule.waitForIdle();

        // Then the pager should change to the Plant List page
        onView(withTagValue("plant_list")).check(matches(isDisplayed()));
    }
}