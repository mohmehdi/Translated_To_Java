
package com.google.samples.apps.sunflower;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Bundle;
import androidx.compose.ui.test.assertIsDisplayed;
import androidx.compose.ui.test.junit4.createAndroidComposeRule;
import androidx.compose.ui.test.onNodeWithContentDescription;
import androidx.compose.ui.test.onNodeWithText;
import androidx.compose.ui.test.performClick;
import androidx.navigation.Navigation;
import androidx.test.espresso.intent.Intents;
import androidx.test.espresso.intent.matcher.IntentMatchers;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.samples.apps.sunflower.utilities.chooser;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

@HiltAndroidTest
public class PlantDetailFragmentTest2 {

    private final HiltAndroidRule hiltRule = new HiltAndroidRule(this);
    private final androidx.compose.ui.test.junit4.AndroidComposeRule<GardenActivity> composeTestRule = createAndroidComposeRule(GardenActivity.class);

    @Rule
    public final RuleChain rule = RuleChain
        .outerRule(hiltRule)
        .around(composeTestRule);

    @Before
    public void jumpToPlantDetailFragment() {
        composeTestRule.activityRule.getScenario().onActivity(activity -> {
            Bundle bundle = new Bundle();
            bundle.putString("plantId", "malus-pumila");
            Navigation.findNavController(activity, R.id.nav_host).navigate(R.id.plant_detail_fragment, bundle);
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

        Intents.intended(
            chooser(
                Matchers.allOf(
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

        InstrumentationRegistry.getInstrumentation()
            .getUiAutomation()
            .performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
    }
}
