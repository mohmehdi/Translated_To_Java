package com.example.android.architecture.blueprints.todoapp.tasks;

import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.R.string;
import com.example.android.architecture.blueprints.todoapp.ServiceLocator;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import com.example.android.architecture.blueprints.todoapp.util.EspressoIdlingResource;
import com.example.android.architecture.blueprints.todoapp.util.deleteAllTasksBlocking;
import com.example.android.architecture.blueprints.todoapp.util.saveTaskBlocking;

import org.hamcrest.Matchers;
import org.hamcrest.core.IsNot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class TasksActivityTest {

    private TasksRepository repository;

    @UiThreadTest
    @Before
    public void resetState() {
        repository = ServiceLocator.provideTasksRepository(getApplicationContext());
        repository.deleteAllTasksBlocking();
    }

    @Before
    public void registerIdlingResource() {
        IdlingRegistry.getInstance().register(EspressoIdlingResource.countingIdlingResource);
    }

    @After
    public void unregisterIdlingResource() {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.countingIdlingResource);
    }

    @Test
    public void createTask() {
        ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withId(R.id.fab_add_task)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withId(R.id.add_task_title))
                .perform(ViewActions.typeText("title"));
        Espresso.onView(ViewMatchers.withId(R.id.add_task_description))
                .perform(ViewActions.typeText("description"));
        Espresso.onView(ViewMatchers.withId(R.id.fab_save_task)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withText("title")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void editTask() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION"));

        ActivityScenario activityScenario = ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withText("TITLE1")).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.fab_edit_task)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.add_task_title)).perform(ViewActions.replaceText("NEW TITLE"));
        Espresso.onView(ViewMatchers.withId(R.id.add_task_description)).perform(ViewActions.replaceText("NEW DESCRIPTION"));

        Espresso.onView(ViewMatchers.withId(R.id.fab_save_task)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withText("NEW TITLE")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.doesNotExist());
    }

    @Test
    public void createOneTask_deleteTask() {
        ActivityScenario activityScenario = ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withId(R.id.fab_add_task)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withId(R.id.add_task_title)).perform(ViewActions.typeText("TITLE1"));
        Espresso.onView(ViewMatchers.withId(R.id.add_task_description)).perform(ViewActions.typeText("DESCRIPTION"));
        Espresso.onView(ViewMatchers.withId(R.id.fab_save_task)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withText("TITLE1")).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_delete)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(string.nav_all)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.doesNotExist());
    }

    @Test
    public void createTwoTasks_deleteOneTask() {
        ActivityScenario activityScenario = ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withId(R.id.fab_add_task)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withId(R.id.add_task_title)).perform(ViewActions.typeText("TITLE1"));
        Espresso.onView(ViewMatchers.withId(R.id.add_task_description)).perform(ViewActions.typeText("DESCRIPTION"));
        Espresso.onView(ViewMatchers.withId(R.id.fab_save_task)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.fab_add_task)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withId(R.id.add_task_title)).perform(ViewActions.typeText("TITLE2"));
        Espresso.onView(ViewMatchers.withId(R.id.add_task_description)).perform(ViewActions.typeText("DESCRIPTION"));
        Espresso.onView(ViewMatchers.withId(R.id.fab_save_task)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withText("TITLE2")).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_delete)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(string.nav_all)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(ViewMatchers.withText("TITLE2")).check(ViewAssertions.doesNotExist());
    }

    @Test
    public void markTaskAsCompleteOnDetailScreen_taskIsCompleteInList() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION"));

        ActivityScenario activityScenario = ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withText("TITLE1")).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).perform(ViewActions.click());

        Espresso.pressBack();

        Espresso.onView(Matchers.allOf(ViewMatchers.withId(R.id.complete), ViewMatchers.hasSibling(ViewMatchers.withText("TITLE1"))))
                .check(ViewAssertions.matches(ViewMatchers.isChecked()));
    }

    @Test
    public void markTaskAsActiveOnDetailScreen_taskIsActiveInList() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION", true));

        ActivityScenario activityScenario = ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withText("TITLE1")).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).perform(ViewActions.click());

        Espresso.pressBack();

        Espresso.onView(Matchers.allOf(ViewMatchers.withId(R.id.complete), ViewMatchers.hasSibling(ViewMatchers.withText("TITLE1"))))
                .check(ViewAssertions.matches(ViewMatchers.not(ViewMatchers.isChecked())));
    }

    @Test
    public void markTaskAsCompleteAndActiveOnDetailScreen_taskIsActiveInList() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION"));

        ActivityScenario activityScenario = ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withText("TITLE1")).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).perform(ViewActions.click());

        Espresso.pressBack();

        Espresso.onView(Matchers.allOf(ViewMatchers.withId(R.id.complete), ViewMatchers.hasSibling(ViewMatchers.withText("TITLE1"))))
                .check(ViewAssertions.matches(ViewMatchers.not(ViewMatchers.isChecked())));
    }

    @Test
    public void markTaskAsActiveAndCompleteOnDetailScreen_taskIsCompleteInList() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION", true));

        ActivityScenario activityScenario = ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withText("TITLE1")).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).perform(ViewActions.click());

        Espresso.pressBack();

        Espresso.onView(Matchers.allOf(ViewMatchers.withId(R.id.complete), ViewMatchers.hasSibling(ViewMatchers.withText("TITLE1"))))
                .check(ViewAssertions.matches(ViewMatchers.isChecked()));
    }
}