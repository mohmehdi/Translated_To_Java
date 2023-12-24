```java
package com.example.android.architecture.blueprints.todoapp.tasks;

import androidx.compose.ui.test.assertIsDisplayed;
import androidx.compose.ui.test.isToggleable;
import androidx.compose.ui.test.junit4.createAndroidComposeRule;
import androidx.compose.ui.test.onNodeWithContentDescription;
import androidx.compose.ui.test.onNodeWithText;
import androidx.compose.ui.test.performClick;
import androidx.navigation.Navigation.findNavController;
import androidx.navigation.findNavController;
import androidx.test.core.app.ApplicationProvider;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.ServiceLocator;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.FakeRepository;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import com.example.android.architecture.blueprints.todoapp.util.saveTaskBlocking;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.TextLayoutMode;

@RunWith(AndroidJUnit4.class)
@MediumTest
@LooperMode(LooperMode.Mode.PAUSED)
@TextLayoutMode(TextLayoutMode.Mode.REALISTIC)
public class TasksScreenTest {

    private TasksRepository repository;

    @Rule
    public createAndroidComposeRule<TasksActivity> composeTestRule = createAndroidComposeRule<>(TasksActivity.class);
    private TasksActivity activity;

    @Before
    public void initRepository() {
        repository = new FakeRepository();
        ServiceLocator.INSTANCE.setTasksRepository(repository);
    }

    @After
    public void cleanupDb() throws InterruptedException {
        ServiceLocator.INSTANCE.resetRepository();
    }

    @Test
    public void displayTask_whenRepositoryHasData() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));

        launchScreen();

        composeTestRule.onNodeWithText("TITLE1").assertIsDisplayed();
    }

    @Test
    public void displayActiveTask() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));

        launchScreen();

        composeTestRule.onNodeWithText("TITLE1").assertIsDisplayed();

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_active)).perform(ViewActions.click());
        composeTestRule.onNodeWithText("TITLE1").assertIsDisplayed();

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_completed)).perform(ViewActions.click());
        composeTestRule.onNodeWithText("TITLE1").assertDoesNotExist();
    }

    @Test
    public void displayCompletedTask() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1", true));

        launchScreen();

        composeTestRule.onNodeWithText("TITLE1").assertIsDisplayed();

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_active)).perform(ViewActions.click());
        composeTestRule.onNodeWithText("TITLE1").assertDoesNotExist();

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_completed)).perform(ViewActions.click());

        composeTestRule.onNodeWithText("TITLE1").assertIsDisplayed();
    }

    @Test
    public void deleteOneTask() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));

        launchScreen();

        composeTestRule.onNodeWithText("TITLE1").performClick();

        Espresso.onView(ViewMatchers.withId(R.id.menu_delete)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_all)).perform(ViewActions.click());
        composeTestRule.onNodeWithText("TITLE1").assertDoesNotExist();
    }

    @Test
    public void deleteOneOfTwoTasks() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));
        repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION2"));

        launchScreen();

        composeTestRule.onNodeWithText("TITLE1").performClick();

        Espresso.onView(ViewMatchers.withId(R.id.menu_delete)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_all)).perform(ViewActions.click());
        composeTestRule.onNodeWithText("TITLE1").assertDoesNotExist();

        composeTestRule.onNodeWithText("TITLE2").assertIsDisplayed();
    }

    @Test
    public void markTaskAsComplete() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));

        launchScreen();

        composeTestRule.onNode(isToggleable()).performClick();

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_all)).perform(ViewActions.click());
        composeTestRule.onNodeWithText("TITLE1").assertIsDisplayed();
        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_active)).perform(ViewActions.click());
        composeTestRule.onNodeWithText("TITLE1").assertDoesNotExist();
        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_completed)).perform(ViewActions.click());
        composeTestRule.onNodeWithText("TITLE1").assertIsDisplayed();
    }

    @Test
    public void markTaskAsActive() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1", true));

        launchScreen();

        composeTestRule.onNode(isToggleable()).performClick();

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_all)).perform(ViewActions.click());
        composeTestRule.onNodeWithText("TITLE1").assertIsDisplayed();
        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_active)).perform(ViewActions.click());
        composeTestRule.onNodeWithText("TITLE1").assertIsDisplayed();
        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_completed)).perform(ViewActions.click());
        composeTestRule.onNodeWithText("TITLE1").assertDoesNotExist();
    }

    @Test
    public void showAllTasks() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));
        repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION2", true));

        launchScreen();

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_all)).perform(ViewActions.click());
        composeTestRule.onNodeWithText("TITLE1").assertIsDisplayed();
        composeTestRule.onNodeWithText("TITLE2").assertIsDisplayed();
    }

    @Test
    public void showActiveTasks() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));
        repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION2"));
        repository.saveTaskBlocking(new Task("TITLE3", "DESCRIPTION3", true));

        launchScreen();

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_active)).perform(ViewActions.click());
        composeTestRule.onNodeWithText("TITLE1").assertIsDisplayed();
        composeTestRule.onNodeWithText("TITLE2").assertIsDisplayed();
        composeTestRule.onNodeWithText("TITLE3").assertDoesNotExist();
    }

    @Test
    public void showCompletedTasks() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));
        repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION2", true));
        repository.saveTaskBlocking(new Task("TITLE3", "DESCRIPTION3", true));

        launchScreen();

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_completed)).perform(ViewActions.click());
        composeTestRule.onNodeWithText("TITLE1").assertDoesNotExist();
        composeTestRule.onNodeWithText("TITLE2").assertIsDisplayed();
        composeTestRule.onNodeWithText("TITLE3").assertIsDisplayed();
    }

    @Test
    public void clearCompletedTasks() {
        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));
        repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION2", true));

        launchScreen();

        Espresso.openActionBarOverflowOrOptionsMenu(getApplicationContext());
        Espresso.onView(ViewMatchers.withText(R.string.menu_clear)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(R.string.nav_all)).perform(ViewActions.click());

        composeTestRule.onNodeWithText