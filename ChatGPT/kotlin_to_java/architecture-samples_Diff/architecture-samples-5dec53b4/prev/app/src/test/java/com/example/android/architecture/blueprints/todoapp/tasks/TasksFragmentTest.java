package com.example.android.architecture.blueprints.todoapp.tasks;

import android.content.Context;
import android.os.Bundle;
import androidx.fragment.app.testing.launchFragmentInContainer;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.ServiceLocator;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import com.example.android.architecture.blueprints.todoapp.util.deleteAllTasksBlocking;
import com.example.android.architecture.blueprints.todoapp.util.saveTaskBlocking;
import org.hamcrest.core.IsNot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.TextLayoutMode;

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

        launchFragmentInContainer(TasksFragment.class, new Bundle(), R.style.AppTheme);
        NavController navController = Mockito.mock(NavController.class);
        scenario.onFragment(fragment -> Navigation.setViewNavController(fragment.requireView(), navController));

        Espresso.onView(ViewMatchers.withId(R.id.fab_add_task)).perform(ViewActions.click());

        Mockito.verify(navController).navigate(
                TasksFragmentDirections.actionTasksFragmentToAddEditTaskFragment(
                        null, ApplicationProvider.getApplicationContext().getString(R.string.add_task)));
    }

    @Test
    @MediumTest
    public void displayTask_whenRepositoryHasData() {

        TasksRepository repository = ServiceLocator.provideTasksRepository(ApplicationProvider.getApplicationContext());
        repository.saveTaskBlocking(new Task("title", "description"));

        launchFragmentInContainer(TasksFragment.class, new Bundle(), R.style.AppTheme);

        Espresso.onView(ViewMatchers.withText("title")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void displayActiveTask() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));

        ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_active)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_completed)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(IsNot.not(ViewMatchers.isDisplayed())));
    }

    @Test
    public void displayCompletedTask() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1", true));

        ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_active)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(IsNot.not(ViewMatchers.isDisplayed())));

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_completed)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void deleteOneTask() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));

        ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withText("TITLE1")).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_delete)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_all)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.doesNotExist());
    }

    @Test
    public void deleteOneOfTwoTasks() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));
        repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION2"));

        ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withText("TITLE1")).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_delete)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_all)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.doesNotExist());

        Espresso.onView(ViewMatchers.withText("TITLE2")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }
}