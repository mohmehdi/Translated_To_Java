
package com.example.android.architecture.blueprints.todoapp.tasks;

import android.app.Activity;
import android.view.Gravity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.contrib.DrawerActions;
import androidx.test.espresso.contrib.DrawerMatchers;
import androidx.test.espresso.matcher.ViewMatchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.filters.LargeTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.custom.action.NavigationViewActions;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class AppNavigationTest {

    private String getToolbarNavigationContentDescription(ActivityScenario<? extends Activity> activityScenario) {
        String description = "";
        activityScenario.onActivity(activity -> {
            description = ((Toolbar) activity.findViewById(R.id.toolbar)).getNavigationContentDescription().toString();
        });
        return description;
    }

    @Test
    public void clickOnStatisticsNavigationItem_ShowsStatisticsScreen() {
        ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout))
                .check(ViewAssertions.matches(DrawerMatchers.isClosed(Gravity.START)))
                .perform(DrawerActions.open());

        Espresso.onView(ViewMatchers.withId(R.id.nav_view)).perform(NavigationViewActions.navigateTo(R.id.statistics_navigation_menu_item));

        Espresso.onView(ViewMatchers.withId(R.id.statistics)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void tasksScreen_clickOnAndroidHomeIcon_OpensNavigation() {
        ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout))
                .check(ViewAssertions.matches(DrawerMatchers.isClosed(Gravity.START)));

        Espresso.onView(ViewMatchers.withContentDescription(getToolbarNavigationContentDescription(activityScenario)))
                .perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout))
                .check(ViewAssertions.matches(DrawerMatchers.isOpen(Gravity.START)));
    }
}
