```java
package com.example.android.architecture.blueprints.todoapp.tasks;

import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.testing.launchFragmentInContainer;
import androidx.navigation.Navigation;
import androidx.navigation.testing.TestNavHostController;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.FakeRepository;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import com.example.android.architecture.blueprints.todoapp.util.saveTaskBlocking;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.core.IsNot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.TextLayoutMode;

@RunWith(AndroidJUnit4.class)
@MediumTest
@LooperMode(LooperMode.Mode.PAUSED)
@TextLayoutMode(TextLayoutMode.Mode.REALISTIC)
public class TasksFragmentTest {

    private TasksRepository repository;

    @Before
    public void initRepository() {
        repository = new FakeRepository();
        ServiceLocator.setTasksRepository(repository);
    }

    @After
    public void cleanupDb() throws InterruptedException {
        ServiceLocator.resetRepository();
    }

    @Test
    public void displayTask_whenRepositoryHasData() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));

        launchActivity();

        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void displayActiveTask() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));

        launchActivity();

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

        launchActivity();

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

        launchActivity();

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

        launchActivity();

        Espresso.onView(ViewMatchers.withText("TITLE1")).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_delete)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_all)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.doesNotExist());

        Espresso.onView(ViewMatchers.withText("TITLE2")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void markTaskAsComplete() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));

        launchActivity();

        Espresso.onView(checkboxWithText("TITLE1")).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_all)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_active)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(IsNot.not(ViewMatchers.isDisplayed())));
        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_completed)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void markTaskAsActive() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1", true));

        launchActivity();

        Espresso.onView(checkboxWithText("TITLE1")).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_all)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_active)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_completed)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(IsNot.not(ViewMatchers.isDisplayed())));
    }

    @Test
    public void showAllTasks() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));
        repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION2", true));

        launchActivity();

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_all)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(ViewMatchers.withText("TITLE2")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void showActiveTasks() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));
        repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION2"));
        repository.saveTaskBlocking(new Task("TITLE3", "DESCRIPTION3", true));

        launchActivity();

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_active)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(ViewMatchers.withText("TITLE2")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(ViewMatchers.withText("TITLE3")).check(ViewAssertions.doesNotExist());
    }

    @Test
    public void showCompletedTasks() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));
        repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION2", true));
        repository.saveTaskBlocking(new Task("TITLE3", "DESCRIPTION3", true));

        launchActivity();

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_completed)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText("TITLE1")).check(ViewAssertions.doesNotExist());
        Espresso.onView(ViewMatchers.withText("TITLE2")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(ViewMatchers.withText("TITLE3")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void clearCompletedTasks() {
        repository.saveTaskBlocking(new Task("TITLE