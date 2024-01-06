package com.google.samples.apps.sunflower;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Rule;
import androidx.compose.ui.test.junit4.CreateAndroidComposeRule;
import androidx.compose.ui.test.junit4.rule.CreateAndroidComposeRule$MainActivityActivityScenarioRule;
import androidx.espresso.Espresso;
import androidx.espresso.IntentTestRule;
import androidx.espresso.action.GeneralClickAction;
import androidx.espresso.action.GeneralClickAction$GestureDescription;
import androidx.espresso.action.GeneralClickAction$MouseButton;
import androidx.espresso.core.internal.deps.guava.collect.Iterables;
import androidx.espresso.intent.Intents;
import androidx.espresso.intent.matcher.IntentMatchers;
import androidx.espresso.matcher.RootMatchers;
import androidx.espresso.matcher.ViewMatchers;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso$OnData;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.common.base.Function;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.intent.Intents.intended;
import static androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class PlantDetailFragmentTest2 {

    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Rule
    public CreateAndroidComposeRule<GardenActivity> composeTestRule =
            new CreateAndroidComposeRule<>(GardenActivity.class, false, new Function<GardenActivity, Void>() {
                @Override
                public Void apply(@NonNull GardenActivity activity) {
                    activity.onCreate(null);
                    return null;
                }
            });

    @Rule
    public RuleChain rule = RuleChain.outerRule(hiltRule).around(composeTestRule);

    @Before
    public void jumpToPlantDetailFragment() {
        composeTestRule.onActivity(new Function<GardenActivity, Void>() {
            @Override
            public Void apply(@NonNull GardenActivity activity) {
                Bundle bundle = new Bundle();
                bundle.putString("plantId", "malus-pumila");
                NavController navController = Navigation.findNavController(activity, R.id.nav_host);
                navController.navigate(R.id.plant_detail_fragment, bundle);
                return null;
            }
        });
    }

    @Test
    public void screen_launches() {
        onView(withText("Apple")).check(matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void testShareTextIntent() {
        Intents.init();

        onView(withText("Apple")).check(matches(ViewMatchers.isDisplayed()));
        onView(withContentDescription("Share")).check(matches(ViewMatchers.isDisplayed()))
                .perform(new GeneralClickAction(
                        GeneralClickAction.GestureDescription.clickDescription(
                                GeneralClickAction.GestureDescription.NEVER_MATCHES),
                        new GeneralClickAction$MouseButton(), 1, 1, 0, 0));

        intended(
                allOf(
                        IntentMatchers.hasAction(Intent.ACTION_SEND),
                        IntentMatchers.hasType("text/plain"),
                        IntentMatchers.hasExtra(
                                Intent.EXTRA_TEXT,
                                "Check out the Apple plant in the Android Sunflower app"
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