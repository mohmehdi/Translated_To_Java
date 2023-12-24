
package com.example.android.architecture.blueprints.todoapp.tasks;

import android.view.Gravity;

import androidx.compose.ui.test.assertIsDisplayed;
import androidx.compose.ui.test.junit4.createAndroidComposeRule;
import androidx.compose.ui.test.onNodeWithContentDescription;
import androidx.compose.ui.test.onNodeWithText;
import androidx.compose.ui.test.performClick;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.findNavController;
import androidx.test.core.app.ApplicationProvider;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.contrib.DrawerActions;
import androidx.test.espresso.contrib.DrawerMatchers;
import androidx.test.espresso.contrib.NavigationViewActions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.ServiceLocator;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import com.example.android.architecture.blueprints.todoapp.util.DataBindingIdlingResource;
import com.example.android.architecture.blueprints.todoapp.util.EspressoIdlingResource;
import com.example.android.architecture.blueprints.todoapp.util.monitorActivity;
import com.example.android.architecture.blueprints.todoapp.util.saveTaskBlocking;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class AppNavigationTest {

    private TasksRepository tasksRepository;

    private final DataBindingIdlingResource dataBindingIdlingResource = new DataBindingIdlingResource();

    @Rule
    public final createAndroidComposeRule<TasksActivity> composeTestRule = createAndroidComposeRule<>(TasksActivity.class);
    private TasksActivity activity;

    @Before
    public void init() {
        tasksRepository = ServiceLocator.provideTasksRepository(ApplicationProvider.getApplicationContext());
    }

    @After
    public void reset() {
        ServiceLocator.resetRepository();
    }

    @Before
    public void registerIdlingResource() {
        IdlingRegistry.getInstance().register(EspressoIdlingResource.countingIdlingResource);
        IdlingRegistry.getInstance().register(dataBindingIdlingResource);
    }

    @After
    public void unregisterIdlingResource() {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.countingIdlingResource);
        IdlingRegistry.getInstance().unregister(dataBindingIdlingResource);
    }

    @Test
    public void drawerNavigationFromTasksToStatistics() {
        dataBindingIdlingResource.monitorActivity(composeTestRule.getScenario());

        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout))
                .check(ViewAssertions.matches(DrawerMatchers.isClosed(Gravity.START)))
                .perform(DrawerActions.open());

        Espresso.onView(ViewMatchers.withId(R.id.nav_view))
                .perform(NavigationViewActions.navigateTo(R.id.statistics_fragment_dest));

        composeTestRule.onNodeWithText("You have no tasks.").assertIsDisplayed();
        composeTestRule.waitForIdle();

        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout))
                .check(ViewAssertions.matches(DrawerMatchers.isClosed(Gravity.START)))
                .perform(DrawerActions.open());

        Espresso.onView(ViewMatchers.withId(R.id.nav_view))
                .perform(NavigationViewActions.navigateTo(R.id.tasks_fragment_dest));

        Espresso.onView(ViewMatchers.withId(R.id.tasks_container_layout)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void tasksScreen_clickOnAndroidHomeIcon_OpensNavigation() {
        dataBindingIdlingResource.monitorActivity(composeTestRule.getScenario());

        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout))
                .check(ViewAssertions.matches(DrawerMatchers.isClosed(Gravity.START)));

        Espresso.onView(ViewMatchers.withContentDescription(
                composeTestRule.getScenario().getToolbarNavigationContentDescription()))
                .perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout))
                .check(ViewAssertions.matches(DrawerMatchers.isOpen(Gravity.START)));
    }

    @Test
    public void statsScreen_clickOnAndroidHomeIcon_OpensNavigation() {
        dataBindingIdlingResource.monitorActivity(composeTestRule.getScenario());

        composeTestRule.getScenario().onActivity(activity -> {
            activity.findNavController(R.id.nav_host_fragment).navigate(R.id.statistics_fragment_dest);
        });
        composeTestRule.waitForIdle();

        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout))
                .check(ViewAssertions.matches(DrawerMatchers.isClosed(Gravity.START)));

        Espresso.onView(ViewMatchers.withContentDescription(
                composeTestRule.getScenario().getToolbarNavigationContentDescription()))
                .perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.drawer_layout))
                .check(ViewAssertions.matches(DrawerMatchers.isOpen(Gravity.START)));
    }

    @Test
    public void taskDetailScreen_doubleUIBackButton() {
        dataBindingIdlingResource.monitorActivity(composeTestRule.getScenario());

        Task task = new Task("UI <- button", "Description");
        tasksRepository.saveTaskBlocking(task);
        composeTestRule.waitForIdle();

        Espresso.onView(ViewMatchers.withText("UI <- button")).perform(ViewActions.click());

        composeTestRule.onNodeWithContentDescription(activity.getString(R.string.edit_task))
                .performClick();

        Espresso.onView(ViewMatchers.withContentDescription(
                composeTestRule.getScenario().getToolbarNavigationContentDescription()))
                .perform(ViewActions.click());
        composeTestRule.onNodeWithText("UI <- button").assertIsDisplayed();

        Espresso.onView(ViewMatchers.withContentDescription(
                composeTestRule.getScenario().getToolbarNavigationContentDescription()))
                .perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withId(R.id.tasks_container_layout)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void taskDetailScreen_doubleBackButton() {
        dataBindingIdlingResource.monitorActivity(composeTestRule.getScenario());

        Task task = new Task("Back button", "Description");
        tasksRepository.saveTaskBlocking(task);
        composeTestRule.waitForIdle();

        Espresso.onView(ViewMatchers.withText("Back button")).perform(ViewActions.click());

        composeTestRule.onNodeWithContentDescription(activity.getString(R.string.edit_task))
                .performClick();

        Espresso.pressBack();
        composeTestRule.onNodeWithText("Back button").assertIsDisplayed();

        Espresso.pressBack();
        Espresso.onView(ViewMatchers.withId(R.id.tasks_container_layout)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }
}
