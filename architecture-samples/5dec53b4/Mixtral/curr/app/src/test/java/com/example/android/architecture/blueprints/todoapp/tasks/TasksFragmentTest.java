package com.example.android.architecture.blueprints.todoapp.tasks;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.testing.LaunchFragmentInContainer;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.Espresso.onView;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import com.example.android.architecture.blueprints.todoapp.service.TestServiceLocator;
import com.example.android.architecture.blueprints.todoapp.util.DeleteAllTasksBlocking;
import com.example.android.architecture.blueprints.todoapp.util.SaveTaskBlocking;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.TextLayoutMode;

import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(AndroidJUnit4.class)
@MediumTest
@LooperMode(LooperMode.Mode.PAUSED)
@TextLayoutMode(TextLayoutMode.Mode.REALISTIC)
public class TasksFragmentTest {


private TasksRepository repository;

@Rule
public ActivityTestRule<TasksActivity> activityRule =
        new ActivityTestRule<>(TasksActivity.class);

@Before
public void setup() {
    repository = TestServiceLocator.getInstance().provideTasksRepository(ApplicationProvider.getApplicationContext());
}

@After
public void tearDown() {
    new DeleteAllTasksBlocking().deleteAllTasksBlocking(repository);
}

@Test
public void clickAddTaskButton_navigateToAddEditFragment() {
    LaunchFragmentInContainer<TasksFragment> scenario =
            LaunchFragmentInContainer.launch(new Bundle(), R.style.AppTheme);
    NavController navController = mock(NavController.class);
    scenario.onFragment(fragment ->
            Navigation.setViewNavController(fragment.requireView(), navController));

    Espresso.onView(withId(R.id.fab_add_task)).perform(click());

    verify(navController).navigate(
            TasksFragmentDirections.actionTasksFragmentToAddEditTaskFragment(
                    null, ApplicationProvider.getApplicationContext().getString(R.string.add_task)));
}

@Test
public void displayTask_whenRepositoryHasData() {
    new SaveTaskBlocking().saveTaskBlocking(repository, new Task("title", "description"));

    LaunchFragmentInContainer<TasksFragment> scenario =
            LaunchFragmentInContainer.launch(new Bundle(), R.style.AppTheme);

    Espresso.onView(withText("title")).check(matches(isDisplayed()));
}

@Test
public void displayActiveTask() {
    new SaveTaskBlocking().saveTaskBlocking(repository, new Task("TITLE1", "DESCRIPTION1"));

    activityRule.launchActivity(new Intent());

    Espresso.onView(withText("TITLE1")).check(matches(isDisplayed()));

    Espresso.onView(withId(R.id.menu_filter)).perform(click());
    Espresso.onView(withText(R.string.nav_active)).perform(click());
    Espresso.onView(withText("TITLE1")).check(matches(isDisplayed()));

    Espresso.onView(withId(R.id.menu_filter)).perform(click());
    Espresso.onView(withText(R.string.nav_completed)).perform(click());
    Espresso.onView(withText("TITLE1")).check(matches(not(isDisplayed())));
}

@Test
public void displayCompletedTask() {
    new SaveTaskBlocking().saveTaskBlocking(repository, new Task("TITLE1", "DESCRIPTION1", true));

    activityRule.launchActivity(new Intent());

    Espresso.onView(withText("TITLE1")).check(matches(isDisplayed()));

    Espresso.onView(withId(R.id.menu_filter)).perform(click());
    Espresso.onView(withText(R.string.nav_active)).perform(click());
    Espresso.onView(withText("TITLE1")).check(matches(not(isDisplayed())));

    Espresso.onView(withId(R.id.menu_filter)).perform(click());
    Espresso.onView(withText(R.string.nav_completed)).perform(click());
    Espresso.onView(withText("TITLE1")).check(matches(isDisplayed()));
}

@Test
public void deleteOneTask() {
    new SaveTaskBlocking().saveTaskBlocking(repository, new Task("TITLE1", "DESCRIPTION1"));

    activityRule.launchActivity(new Intent());

    Espresso.onView(withText("TITLE1")).perform(click());

    Espresso.onView(withId(R.id.menu_delete)).perform(click());

    Espresso.onView(withId(R.id.menu_filter)).perform(click());
    Espresso.onView(withText(R.string.nav_all)).perform(click());
    Espresso.onView(withText("TITLE1")).check(doesNotExist());
}

@Test
public void deleteOneOfTwoTasks() {
    new SaveTaskBlocking().saveTaskBlocking(repository, new Task("TITLE1", "DESCRIPTION1"));
    new SaveTaskBlocking().saveTaskBlocking(repository, new Task("TITLE2", "DESCRIPTION2"));

    activityRule.launchActivity(new Intent());

    Espresso.onView(withText("TITLE1")).perform(click());

    Espresso.onView(withId(R.id.menu_delete)).perform(click());

    Espresso.onView(withId(R.id.menu_filter)).perform(click());
    Espresso.onView(withText(R.string.nav_all)).perform(click());
    Espresso.onView(withText("TITLE1")).check(doesNotExist());

    Espresso.onView(withText("TITLE2")).check(matches(isDisplayed()));
}

@Test
public void markTaskAsComplete() {
    new SaveTaskBlocking().saveTaskBlocking(repository, new Task("TITLE1", "DESCRIPTION1"));

    AppCompatActivity activity = activityRule.getActivity();
    ActivityResultLauncher<String> requestPermissionLauncher =
            activity.registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            });

    Espresso.onView(checkboxWithText("TITLE1")).perform(click());

    Espresso.onView(withId(R.id.menu_filter)).perform(click());
    Espresso.onView(withText(R.string.nav_all)).perform(click());
    Espresso.onView(withText("TITLE1")).check(matches(isDisplayed()));
    Espresso.onView(withId(R.id.menu_filter)).perform(click());
    Espresso.onView(withText(R.string.nav_active)).perform(click());
    Espresso.onView(withText("TITLE1")).check(matches(not(isDisplayed())));
    Espresso.onView(withId(R.id.menu_filter)).perform(click());
    Espresso.onView(withText(R.string.nav_completed)).perform(click());
    Espresso.onView(withText("TITLE1")).check(matches(isDisplayed()));
}

@Test
public void markTaskAsActive() {
    new SaveTaskBlocking().saveTaskBlocking(repository, new Task("TITLE1", "DESCRIPTION1", true));

    AppCompatActivity activity = activityRule.getActivity();
    ActivityResultLauncher<String> requestPermissionLauncher =
            activity.registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            });

    Espresso.onView(checkboxWithText("TITLE1")).perform(click());

    Espresso.onView(withId(R.id.menu_filter)).perform(click());
    Espresso.onView(withText(R.string.nav_all)).perform(click());
    Espresso.onView(withText("TITLE1")).check(matches(isDisplayed()));
    Espresso.onView(withId(R.id.menu_filter)).perform(click());
    Espresso.onView(withText(R.string.nav_active)).perform(click());
    Espresso.onView(withText("TITLE1")).check(matches(isDisplayed()));
    Espresso.onView(withId(R.id.menu_filter)).perform(click());
    Espresso.onView(withText(R.string.nav_completed)).perform(click());
    Espresso.onView(withText("TITLE1")).check(matches(not(isDisplayed())));
}

@Test
public void showAllTasks() {
    new SaveTaskBlocking().saveTaskBlocking(repository, new Task("TITLE1", "DESCRIPTION1"));
    new SaveTaskBlocking().saveTaskBlocking(repository, new Task("TITLE2", "DESCRIPTION2", true));

    activityRule.launchActivity(new Intent());

    Espresso.onView(withId(R.id.menu_filter)).perform(click());
    Espresso.onView(withText(R.string.nav_all)).perform(click());
    Espresso.onView(withText("TITLE1")).check(matches(isDisplayed()));
    Espresso.onView(withText("TITLE2")).check(matches(isDisplayed()));
}

@Test
public void showActiveTasks() {
    new SaveTaskBlocking().saveTaskBlocking(repository, new Task("TITLE1", "DESCRIPTION1"));
    new SaveTaskBlocking().saveTaskBlocking(repository, new Task("TITLE2", "DESCRIPTION2"));
    new SaveTaskBlocking().saveTaskBlocking(repository, new Task("TITLE3", "DESCRIPTION3", true));

    activityRule.launchActivity(new Intent());

    Espresso.onView(withId(R.id.menu_filter)).perform(click());
    Espresso.onView(withText(R.string.nav_active)).perform(click());
    Espresso.onView(withText("TITLE1")).check(matches(isDisplayed()));
    Espresso.onView(withText("TITLE2")).check(matches(isDisplayed()));
    Espresso.onView(withText("TITLE3")).check(doesNotExist());
}

@Test
public void showCompletedTasks() {
    new SaveTaskBlocking().saveTaskBlocking(repository, new Task("TITLE1", "DESCRIPTION1"));
    new SaveTaskBlocking().saveTaskBlocking(repository, new Task("TITLE2", "DESCRIPTION2", true));
    new SaveTaskBlocking().saveTaskBlocking(repository, new Task("TITLE3", "DESCRIPTION3", true));

    activityRule.launchActivity(new Intent());

    Espresso.onView(withId(R.id.menu_filter)).perform(click());
    Espresso.onView(withText(R.string.nav_completed)).perform(click());
    Espresso.onView(withText("TITLE1")).check(doesNotExist());
    Espresso.onView(withText("TITLE2")).check(matches(isDisplayed()));
    Espresso.onView(withText("TITLE3")).check(matches(isDisplayed()));
}

@Test
public void clearCompletedTasks() {
    new SaveTaskBlocking().saveTaskBlocking(repository, new Task("TITLE1", "DESCRIPTION1"));
    new SaveTaskBlocking().saveTaskBlocking(repository, new Task("TITLE2", "DESCRIPTION2", true));

    activityRule.launchActivity(new Intent());

    openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext());
    Espresso.onView(withText(R.string.menu_clear)).perform(click());

    Espresso.onView(withId(R.id.menu_filter)).perform(click());
    Espresso.onView(withText(R.string.nav_all)).perform(click());

    Espresso.onView(withText("TITLE1")).check(matches(isDisplayed()));
    Espresso.onView(withText("TITLE2")).check(doesNotExist());
}

@Test
public void noTasks_AllTasksFilter_AddTaskViewVisible() {
    activityRule.launchActivity(new Intent());

    Espresso.onView(withId(R.id.menu_filter)).perform(click());
    Espresso.onView(withText(R.string.nav_all)).perform(click());

    Espresso.onView(withText("You have no TO-DOs!")).check(matches(isDisplayed()));
}

@Test
public void noTasks_CompletedTasksFilter_AddTaskViewNotVisible() {
    activityRule.launchActivity(new Intent());

    Espresso.onView(withId(R.id.menu_filter)).perform(click());
    Espresso.onView(withText(R.string.nav_completed)).perform(click());

    Espresso.onView(withText("You have no completed TO-DOs!")).check(matches((isDisplayed())));
}

@Test
public void noTasks_ActiveTasksFilter_AddTaskViewNotVisible() {
    activityRule.launchActivity(new Intent());

    Espresso.onView(withId(R.id.menu_filter)).perform(click());
    Espresso.onView(withText(R.string.nav_active)).perform(click());

    Espresso.onView(withText("You have no active TO-DOs!")).check(matches((isDisplayed())));
}

private Matcher<View> checkboxWithText(String text) {
    return allOf(withId(R.id.complete), hasSibling(withText(text)));
}
}