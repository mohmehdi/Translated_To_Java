package com.example.android.architecture.blueprints.todoapp.tasks;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.testing.launchFragmentInContainer;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.ServiceLocator;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import com.example.android.architecture.blueprints.todoapp.util.deleteAllTasksBlocking;
import com.example.android.architecture.blueprints.todoapp.util.saveTaskBlocking;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.TextLayoutMode;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.core.IsNot.not;

@RunWith(AndroidJUnit4.class)
@MediumTest
@LooperMode(LooperMode.Mode.PAUSED)
@TextLayoutMode(TextLayoutMode.Mode.REALISTIC)
public class TasksFragmentTest {

    private TasksRepository repository;

    @Before
    public void setup() {
        repository = ServiceLocator.provideTasksRepository(ApplicationProvider.getApplicationContext());
    }

    @After
    public void tearDown() {
        repository.deleteAllTasksBlocking();
    }

    @Test
    public void clickAddTaskButton_navigateToAddEditFragment() {
        ActivityScenario<TasksFragment> scenario = launchFragmentInContainer(TasksFragment.class, new Bundle(), R.style.AppTheme);
        NavController navController = Mockito.mock(NavController.class);
        scenario.onFragment(fragment ->
                Navigation.setViewNavController(fragment.requireView(), navController));

        onView(withId(R.id.fab_add_task)).perform(click());

        verify(navController).navigate(
                TasksFragmentDirections.actionTasksFragmentToAddEditTaskFragment(
                        null, ApplicationProvider.getApplicationContext().getString(R.string.add_task)));
    }

    @Test
    public void displayTask_whenRepositoryHasData() {
        TasksRepository repository = ServiceLocator.provideTasksRepository(ApplicationProvider.getApplicationContext());
        repository.saveTaskBlocking(new Task("title", "description"));

        launchFragmentInContainer(TasksFragment.class, new Bundle(), R.style.AppTheme);

        onView(withText("title")).check(matches(isDisplayed()));
    }

    @Test
    public void displayActiveTask() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));

        ActivityScenario.launch(TasksActivity.class);

        onView(withText("TITLE1")).check(matches(isDisplayed()));

        onView(withId(R.id.menu_filter)).perform(click());
        onView(withText(R.string.nav_active)).perform(click());
        onView(withText("TITLE1")).check(matches(isDisplayed()));

        onView(withId(R.id.menu_filter)).perform(click());
        onView(withText(R.string.nav_completed)).perform(click());
        onView(withText("TITLE1")).check(matches(not(isDisplayed())));
    }

    @Test
    public void displayCompletedTask() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1", true));

        ActivityScenario.launch(TasksActivity.class);

        onView(withText("TITLE1")).check(matches(isDisplayed()));

        onView(withId(R.id.menu_filter)).perform(click());
        onView(withText(R.string.nav_active)).perform(click());
        onView(withText("TITLE1")).check(matches(not(isDisplayed())));

        onView(withId(R.id.menu_filter)).perform(click());
        onView(withText(R.string.nav_completed)).perform(click());
        onView(withText("TITLE1")).check(matches(isDisplayed()));
    }

    @Test
    public void deleteOneTask() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));

        ActivityScenario.launch(TasksActivity.class);

        onView(withText("TITLE1")).perform(click());

        onView(withId(R.id.menu_delete)).perform(click());

        onView(withId(R.id.menu_filter)).perform(click());
        onView(withText(R.string.nav_all)).perform(click());
        onView(withText("TITLE1")).check(doesNotExist());
    }

    @Test
    public void deleteOneOfTwoTasks() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));
        repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION2"));

        ActivityScenario.launch(TasksActivity.class);

        onView(withText("TITLE1")).perform(click());

        onView(withId(R.id.menu_delete)).perform(click());

        onView(withId(R.id.menu_filter)).perform(click());
        onView(withText(R.string.nav_all)).perform(click());
        onView(withText("TITLE1")).check(doesNotExist());
        onView(withText("TITLE2")).check(matches(isDisplayed()));
    }
     @Test
    public void markTaskAsComplete() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));

        ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(checkboxWithText("TITLE1")).perform(Espresso.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_all)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_active)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(CoreMatchers.not(ViewMatchers.isDisplayed())));
        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_completed)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void markTaskAsActive() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1", true));

        ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(checkboxWithText("TITLE1")).perform(Espresso.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_all)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_active)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_completed)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(CoreMatchers.not(ViewMatchers.isDisplayed())));
    }

    @Test
    public void showAllTasks() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));
        repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION2", true));

        ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_all)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(ViewMatchers.withText("TITLE2")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void showActiveTasks() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));
        repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION2"));
        repository.saveTaskBlocking(new Task("TITLE3", "DESCRIPTION3", true));

        ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_active)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(ViewMatchers.withText("TITLE2")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(ViewMatchers.withText("TITLE3")).check(ViewAssertions.doesNotExist());
    }

    @Test
    public void showCompletedTasks() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));
        repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION2", true));
        repository.saveTaskBlocking(new Task("TITLE3", "DESCRIPTION3", true));

        ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_completed)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.doesNotExist());
        Espresso.onView(ViewMatchers.withText("TITLE2")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(ViewMatchers.withText("TITLE3")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void clearCompletedTasks() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));
        repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION2", true));

        ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);

        Espresso.openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext());
        Espresso.onView(ViewMatchers.withText(R.string.menu_clear)).perform(Espresso.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_all)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(ViewMatchers.withText("TITLE2")).check(ViewAssertions.doesNotExist());
    }

    @Test
    public void noTasks_AllTasksFilter_AddTaskViewVisible() {
        ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_all)).perform(Espresso.click());

        Espresso.onView(ViewMatchers.withText("You have no TO-DOs!")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void noTasks_CompletedTasksFilter_AddTaskViewNotVisible() {
        ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_completed)).perform(Espresso.click());

        Espresso.onView(ViewMatchers.withText("You have no completed TO-DOs!")).check(ViewAssertions.matches((ViewMatchers.isDisplayed())));
    }

    @Test
    public void noTasks_ActiveTasksFilter_AddTaskViewNotVisible() {
        ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(Espresso.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_active)).perform(Espresso.click());

        Espresso.onView(ViewMatchers.withText("You have no active TO-DOs!")).check(ViewAssertions.matches((ViewMatchers.isDisplayed())));
    }

    private Matcher<View> checkboxWithText(String text) {
        return CoreMatchers.allOf(ViewMatchers.withId(R.id.complete), ViewMatchers.hasSibling(ViewMatchers.withText(text)));
    }
}
