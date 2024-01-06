package com.google.samples.apps.sunflower;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.pm.ActivityInfo;
import android.support.test.espresso.Espresso;
import android.support.test.espresso.ViewAction;
import android.support.test.espresso.ViewAssertion;
import android.support.test.espresso.action.ViewActions;
import android.support.test.espresso.action.ViewActions.click;
import android.support.test.espresso.contrib.DrawerMatchers;
import android.support.test.espresso.contrib.NavigationViewActions;
import android.support.test.espresso.matcher.ViewMatchers;
import android.support.test.rule.ActivityTestRule;
import android.view.Gravity;
import android.view.View;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

public class GardenActivityTest {

  private TestRule activityTestRule = new ActivityTestRule<>(
    GardenActivity.class
  );

  @Test
  public void clickOnAndroidHomeIcon_OpensAndClosesNavigation() {
    Espresso
      .onView(ViewMatchers.withId(R.id.drawer_layout))
      .check(matches(DrawerMatchers.isClosed(Gravity.START)));

    clickOnHomeIconToOpenNavigationDrawer();
    checkDrawerIsOpen();
  }

  @Test
  public void onRotate_NavigationStaysOpen() {
    clickOnHomeIconToOpenNavigationDrawer();

    ((ActivityTestRule) activityTestRule).getActivity()
      .setRequestedOrientation(SCREEN_ORIENTATION_LANDSCAPE);
    checkDrawerIsOpen();

    ((ActivityTestRule) activityTestRule).getActivity()
      .setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT);
    checkDrawerIsOpen();
  }

  @Test
  public void clickOnPlantListDrawerMenuItem_StartsPlantListActivity() {
    clickOnHomeIconToOpenNavigationDrawer();

    Espresso
      .onView(ViewMatchers.withId(R.id.navigation_view))
      .perform(NavigationViewActions.navigateTo(R.id.plant_list_fragment));

    Espresso
      .onView(ViewMatchers.withId(R.id.plant_list))
      .check(matches(ViewMatchers.isDisplayed()));
  }

  @Test
  public void pressDeviceBack_CloseDrawer_Then_PressBack_Close_App() {
    clickOnHomeIconToOpenNavigationDrawer();
    Espresso.onView(ViewMatchers.isRoot()).perform(ViewActions.pressBack());
    checkDrawerIsNotOpen();
    assertFalse(activityTestRule.getActivity().isFinishing());
    assertFalse(activityTestRule.getActivity().isDestroyed());
  }

  private void clickOnHomeIconToOpenNavigationDrawer() {
    Espresso
      .onView(
        ViewMatchers.withContentDescription(
          getToolbarNavigationContentDescription(
            ((ActivityTestRule) activityTestRule).getActivity(),
            R.id.toolbar
          )
        )
      )
      .perform(click());
  }

  private void checkDrawerIsOpen() {
    Espresso
      .onView(ViewMatchers.withId(R.id.drawer_layout))
      .check(matches(DrawerMatchers.isOpen(Gravity.START)));
  }

  private void checkDrawerIsNotOpen() {
    Espresso
      .onView(ViewMatchers.withId(R.id.drawer_layout))
      .check(matches(DrawerMatchers.isClosed(Gravity.START)));
  }

  private String getToolbarNavigationContentDescription(
    Object activity,
    int toolbarId
  ) {
    return ((Activity) activity).getResources()
      .getString(R.string.nav_drawer_open);
  }
}
