
package com.google.samples.apps.sunflower;

import android.content.pm.ActivityInfo;
import android.support.test.espresso.Espresso;
import android.support.test.espresso.action.ViewActions;
import android.support.test.espresso.assertion.ViewAssertions;
import android.support.test.espresso.contrib.DrawerMatchers;
import android.support.test.espresso.contrib.NavigationViewActions;
import android.support.test.espresso.matcher.ViewMatchers;
import android.support.test.rule.ActivityTestRule;
import android.view.Gravity;

import com.google.samples.apps.sunflower.utilities.ToolbarUtils;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class GardenActivityTest {

    @Rule
    public ActivityTestRule<GardenActivity> activityTestRule = new ActivityTestRule<>(GardenActivity.class);

    @Test
    public void clickOnAndroidHomeIcon_OpensAndClosesNavigation() {
        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout)).check(ViewAssertions.matches(DrawerMatchers.isClosed(Gravity.START)));

        clickOnHomeIconToOpenNavigationDrawer();
        checkDrawerIsOpen();
    }

    @Test
    public void onRotate_NavigationStaysOpen() {
        clickOnHomeIconToOpenNavigationDrawer();

        activityTestRule.getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        checkDrawerIsOpen();

        activityTestRule.getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        checkDrawerIsOpen();
    }

    @Test
    public void clickOnPlantListDrawerMenuItem_StartsPlantListActivity() {
        clickOnHomeIconToOpenNavigationDrawer();

        Espresso.onView(ViewMatchers.withId(R.id.navigation_view))
                .perform(NavigationViewActions.navigateTo(R.id.plant_list_fragment));

        Espresso.onView(ViewMatchers.withId(R.id.plant_list)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void pressDeviceBack_CloseDrawer_Then_PressBack_Close_App() {
        clickOnHomeIconToOpenNavigationDrawer();
        Espresso.onView(ViewMatchers.isRoot()).perform(ViewActions.pressBack());
        checkDrawerIsNotOpen();
        Assert.assertEquals(activityTestRule.getActivity().isFinishing(), false);
        Assert.assertEquals(activityTestRule.getActivity().isDestroyed(), false);
    }

    private void clickOnHomeIconToOpenNavigationDrawer() {
        Espresso.onView(ViewMatchers.withContentDescription(ToolbarUtils.getToolbarNavigationContentDescription(
                activityTestRule.getActivity(), R.id.toolbar))).perform(ViewActions.click());
    }

    private void checkDrawerIsOpen() {
        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout)).check(ViewAssertions.matches(DrawerMatchers.isOpen(Gravity.START)));
    }

    private void checkDrawerIsNotOpen() {
        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout)).check(ViewAssertions.matches(DrawerMatchers.isClosed(Gravity.START)));
    }
}
