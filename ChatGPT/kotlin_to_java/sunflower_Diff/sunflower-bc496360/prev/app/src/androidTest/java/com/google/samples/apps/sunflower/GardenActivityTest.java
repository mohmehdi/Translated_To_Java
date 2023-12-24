
package com.google.samples.apps.sunflower;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.Rule;
import org.junit.Test;

public class GardenActivityTest {

    @Rule
    public ActivityScenarioRule<GardenActivity> activityTestRule = new ActivityScenarioRule<>(GardenActivity.class);

    @Test
    public void clickAddPlant_OpensPlantList() {
        Espresso.onView(ViewMatchers.withId(R.id.add_plant)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withId(R.id.plant_list)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }
}
