
package com.google.samples.apps.sunflower;

import android.util.Log;
import androidx.compose.ui.test.assertIsDisplayed;
import androidx.compose.ui.test.junit4.createAndroidComposeRule;
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
import dagger.hilt.android.testing.HiltAndroidTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

@HiltAndroidTest
public class GardenActivityTest {

    private final HiltAndroidRule hiltRule = new HiltAndroidRule(this);
    private final androidx.compose.ui.test.junit4.AndroidComposeRule<GardenActivity> composeTestRule = createAndroidComposeRule(GardenActivity.class);

    @Rule
    public final RuleChain rule = RuleChain
        .outerRule(hiltRule)
        .around(composeTestRule);

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
        composeTestRule.onNodeWithText("Add plant")
            .assertExists()
            .assertIsDisplayed()
            .performClick();

        composeTestRule.waitForIdle();

        composeTestRule.onNodeWithTag("plant_list")
            .assertExists();
    }
}
