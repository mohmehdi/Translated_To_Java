
package com.example.android.architecture.blueprints.todoapp.tasks;

import androidx.compose.ui.test.SemanticsNodeInteraction;
import androidx.compose.ui.test.junit4.createAndroidComposeRule;
import androidx.compose.ui.test.onNodeWithContentDescription;
import androidx.compose.ui.test.onNodeWithText;
import androidx.compose.ui.test.performClick;
import androidx.compose.ui.test.performTextInput;
import androidx.compose.ui.test.performTextReplacement;
import androidx.test.core.app.ApplicationProvider;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.IdlingRegistry;
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
public class TasksActivityTest {

    private TasksRepository repository;

    @Rule
    public createAndroidComposeRule<TasksActivity> composeTestRule = createAndroidComposeRule<>(TasksActivity.class);
    private TasksActivity activity;

    private DataBindingIdlingResource dataBindingIdlingResource = new DataBindingIdlingResource();

    @Before
    public void init() {
        activity = composeTestRule.activity();
        ApplicationProvider.getApplicationContext();
        ServiceLocator.createDataBase(getApplicationContext(), true);
        repository = ServiceLocator.provideTasksRepository(getApplicationContext());
        repository.deleteAllTasksBlocking();
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
    public void editTask() {
        dataBindingIdlingResource.monitorActivity(composeTestRule.getScenario());

        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION"));
        composeTestRule.waitForIdle();

        onView(withText("TITLE1")).perform(click());
        composeTestRule.onNodeWithText("TITLE1").assertIsDisplayed();
        composeTestRule.onNodeWithText("DESCRIPTION").assertIsDisplayed();
        composeTestRule.onNode(isToggleable()).assertIsOff();

        composeTestRule.onNodeWithContentDescription(activity.getString(R.string.edit_task))
                .performClick();
        findTextField("TITLE1").performTextReplacement("NEW TITLE");
        findTextField("DESCRIPTION").performTextReplacement("NEW DESCRIPTION");
        composeTestRule.onNodeWithContentDescription(activity.getString(R.string.cd_save_task))
                .performClick();

        onView(withText("NEW TITLE")).check(matches(isDisplayed()));
        onView(withText("TITLE1")).check(doesNotExist());
    }

    @Test
    public void createOneTask_deleteTask() {
        dataBindingIdlingResource.monitorActivity(composeTestRule.getScenario());

        composeTestRule.onNodeWithContentDescription(activity.getString(R.string.edit_task))
                .performClick();
        findTextField(R.string.title_hint).performTextInput("TITLE1");
        findTextField(R.string.description_hint).performTextInput("DESCRIPTION");
        composeTestRule.onNodeWithContentDescription(activity.getString(R.string.cd_save_task))
                .performClick();

        onView(withText("TITLE1")).perform(click());

        onView(withId(R.id.menu_delete)).perform(click());

        onView(withId(R.id.menu_filter)).perform(click());
        onView(withText(R.string.nav_all)).perform(click());
        onView(withText("TITLE1")).check(doesNotExist());
    }

    @Test
    public void createTwoTasks_deleteOneTask() {
        dataBindingIdlingResource.monitorActivity(composeTestRule.getScenario());

        repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION"));
        repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION"));
        composeTestRule.waitForIdle();

        onView(withText("TITLE2")).perform(click());

        onView(withId(R.id.menu_delete)).perform(click());

        onView(withId(R.id.menu_filter)).perform(click());
        onView(withText(R.string.nav_all)).perform(click());
        onView(withText("TITLE1")).check(matches(isDisplayed()));
        onView(withText("TITLE2")).check(doesNotExist());
    }

    @Test
    public void markTaskAsCompleteOnDetailScreen_taskIsCompleteInList() {
        dataBindingIdlingResource.monitorActivity(composeTestRule.getScenario());

        String taskTitle = "COMPLETED";
        repository.saveTaskBlocking(new Task(taskTitle, "DESCRIPTION"));
        composeTestRule.waitForIdle();

        onView(withText(taskTitle)).perform(click());

        composeTestRule.onNode(isToggleable()).performClick();

        onView(
                withContentDescription(
                        composeTestRule.getScenario().getToolbarNavigationContentDescription()
                )
        ).perform(click());

        composeTestRule.onNode(isToggleable()).assertIsOn();
    }

    @Test
    public void markTaskAsActiveOnDetailScreen_taskIsActiveInList() {
        dataBindingIdlingResource.monitorActivity(composeTestRule.getScenario());

        String taskTitle = "ACTIVE";
        repository.saveTaskBlocking(new Task(taskTitle, "DESCRIPTION", true));
        composeTestRule.waitForIdle();

        onView(withText(taskTitle)).perform(click());

        composeTestRule.onNode(isToggleable()).performClick();

        onView(
                withContentDescription(
                        composeTestRule.getScenario().getToolbarNavigationContentDescription()
                )
        ).perform(click());

        composeTestRule.onNode(isToggleable()).assertIsOff();
    }

    @Test
    public void markTaskAsCompleteAndActiveOnDetailScreen_taskIsActiveInList() {
        dataBindingIdlingResource.monitorActivity(composeTestRule.getScenario());

        String taskTitle = "ACT-COMP";
        repository.saveTaskBlocking(new Task(taskTitle, "DESCRIPTION"));
        composeTestRule.waitForIdle();

        onView(withText(taskTitle)).perform(click());

        composeTestRule.onNode(isToggleable()).performClick();

        composeTestRule.onNode(isToggleable()).performClick();

        onView(
                withContentDescription(
                        composeTestRule.getScenario().getToolbarNavigationContentDescription()
                )
        ).perform(click());

        composeTestRule.onNode(isToggleable()).assertIsOff();
    }

    @Test
    public void markTaskAsActiveAndCompleteOnDetailScreen_taskIsCompleteInList() {
        dataBindingIdlingResource.monitorActivity(composeTestRule.getScenario());

        String taskTitle = "COMP-ACT";
        repository.saveTaskBlocking(new Task(taskTitle, "DESCRIPTION", true));
        composeTestRule.waitForIdle();

        onView(withText(taskTitle)).perform(click());

        composeTestRule.onNode(isToggleable()).performClick();

        composeTestRule.onNode(isToggleable()).performClick();

        onView(
                withContentDescription(
                        composeTestRule.getScenario().getToolbarNavigationContentDescription()
                )
        ).perform(click());

        composeTestRule.onNode(isToggleable()).assertIsOn();
    }

    @Test
    public void createTask() {
        dataBindingIdlingResource.monitorActivity(composeTestRule.getScenario());

        composeTestRule.onNodeWithContentDescription(activity.getString(R.string.add_task))
                .performClick();
        findTextField(R.string.title_hint).performTextInput("title");
        findTextField(R.string.description_hint).performTextInput("description");
        composeTestRule.onNodeWithContentDescription(activity.getString(R.string.cd_save_task))
                .performClick();

        onView(withText("title")).check(matches(isDisplayed()));
    }

    private SemanticsNodeInteraction findTextField(int textId) {
        return composeTestRule.onNode(
                ViewMatchers.hasSetTextAction() and ViewMatchers.hasText(activity.getString(textId))
        );
    }

    private SemanticsNodeInteraction findTextField(String text) {
        return composeTestRule.onNode(
                ViewMatchers.hasSetTextAction() and ViewMatchers.hasText(text)
        );
    }
}
