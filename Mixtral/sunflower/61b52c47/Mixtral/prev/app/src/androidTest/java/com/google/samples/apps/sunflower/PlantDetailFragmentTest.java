package com.google.samples.apps.sunflower;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.RunWith;
import androidx.compose.ui.test.assertIsDisplayed;
import androidx.compose.ui.test.junit4.AndroidComposeRule;
import androidx.compose.ui.test.onNodeWithContentDescription;
import androidx.compose.ui.test.onNodeWithText;
import androidx.compose.ui.test.performClick;
import androidx.navigation.NavController;
import androidx.navigation.findNavController;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.intent.Intents;
import androidx.test.espresso.intent.matcher.IntentMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class PlantDetailFragmentTest {

    @Rule
    public AndroidComposeRule<GardenActivity> composeTestRule = new AndroidComposeRule<>(GardenActivity.class);

    @Before
    public void jumpToPlantDetailFragment() {
        composeTestRule.activityRule.getScenario().onActivity(activity -> {
            Bundle bundle = new Bundle();
            bundle.putString("plantId", "malus-pumila");
            findNavController(activity, R.id.nav_host).navigate(R.id.plant_detail_fragment, bundle);
        });
    }

    @Test
    public void screen_launches() {
        composeTestRule.onNodeWithText("Apple").assertIsDisplayed();
    }

    @Test
    public void testShareTextIntent() {
        Intents.init();

        composeTestRule.onNodeWithText("Apple").assertIsDisplayed();
        composeTestRule.onNodeWithContentDescription("Share").assertIsDisplayed().performClick();

        intended(
            chooser(
                allOf(
                    IntentMatchers.hasAction(Intent.ACTION_SEND),
                    IntentMatchers.hasType("text/plain"),
                    IntentMatchers.hasExtra(
                        Intent.EXTRA_TEXT,
                        "Check out the Apple plant in the Android Sunflower app"
                    )
                )
            )
        );
        Intents.release();

        // dismiss the Share Dialog
        InstrumentationRegistry.getInstrumentation()
            .getUiAutomation()
            .performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
    }
}