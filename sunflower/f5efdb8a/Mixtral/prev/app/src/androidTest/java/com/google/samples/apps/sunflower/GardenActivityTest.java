package com.google.samples.apps.sunflower;

import android.content.pm.ActivityInfo;
import android.support.test.espresso.Espresso;
import android.support.test.espresso.ViewAction;
import android.support.test.espresso.ViewAssertion;
import android.support.test.espresso.contrib.DrawerActions;
import android.support.test.espresso.contrib.NavigationViewActions;
import android.support.test.espresso.matcher.ViewMatchers;
import android.support.test.rule.ActivityTestRule;
import android.view.Gravity;

import org.junit.Rule;
import org.junit.Test;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.contrib.DrawerMatchers.isClosed;
import static android.support.test.espresso.contrib.DrawerMatchers.isOpen;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withContentDescription;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

public class GardenActivityTest {


@Rule public ActivityTestRule<GardenActivity> activityTestRule = new ActivityTestRule<>(GardenActivity.class);

@Test public void clickOnAndroidHomeIcon_OpensAndClosesNavigation() {
    onView(withId(R.id.drawer_layout)).check(matches(isClosed(Gravity.START)));

    clickOnHomeIconToOpenNavigationDrawer();
    checkDrawerIsOpen();
}

@Test public void onRotate_NavigationStaysOpen() {
    clickOnHomeIconToOpenNavigationDrawer();

    activityTestRule.getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    checkDrawerIsOpen();

    activityTestRule.getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    checkDrawerIsOpen();
}

@Test public void clickOnPlantListDrawerMenuItem_StartsPlantListActivity() {
    clickOnHomeIconToOpenNavigationDrawer();

    ViewInteraction navigationView = onView(withId(R.id.navigation_view));
    navigationView.perform(NavigationViewActions.navigateTo(R.id.plant_list_fragment));

    onView(withId(R.id.plant_list)).check(matches(isDisplayed()));
}

private void clickOnHomeIconToOpenNavigationDrawer() {
    ViewInteraction appCompatImageButton = onView(withContentDescription(GardenActivityTest.getToolbarNavigationContentDescription(
            activityTestRule.getActivity(), R.id.toolbar)));
    appCompatImageButton.perform(click());
}

private void checkDrawerIsOpen() {
    onView(withId(R.id.drawer_layout)).check(matches(isOpen(Gravity.START)));
}

public static ViewAction getToolbarNavigationContentDescription(final Object activity, final int toolbarId) {
    return new ViewAction() {
        @Override
        public Matcher<View> getConstraints() {
            return ViewMatchers.isAssignableFrom(android.support.v7.widget.Toolbar.class);
        }

        @Override
        public String getDescription() {
            return "with content description from toolbar";
        }

        @Override
        public void perform(UiController uiController, View view) {
            android.support.v7.widget.Toolbar toolbar = (android.support.v7.widget.Toolbar) view;
            uiController.loopMainThreadUntilIdle();
            uiController.loopMainThreadUntilIdle();
            uiController.sendCommand(new UiController.UiAutomatorCommand() {
                @Override
                public CommandResponse run() {
                    String description = String.valueOf(toolbar.getContentDescription());
                    return new CommandResponse(CommandStatus.SUCCESS, description);
                }
            });
        }
    };
}
}