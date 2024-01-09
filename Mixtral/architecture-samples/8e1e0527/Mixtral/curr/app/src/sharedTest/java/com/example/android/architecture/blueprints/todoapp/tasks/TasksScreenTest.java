package com.example.android.architecture.blueprints.todoapp.tasks;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.os.Looper;
import androidx.compose.ui.test.junit4.createAndroidComposeRule;
import androidx.navigation.NavController;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.RootMatchers;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.FakeRepository;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import com.example.android.architecture.blueprints.todoapp.service.ServiceLocator;
import com.example.android.architecture.blueprints.todoapp.util.LiveDataTestUtil;
import com.example.android.architecture.blueprints.todoapp.util.saveTaskBlocking;
import kotlinx.coroutines.ExperimentalCoroutinesApi;
import kotlinx.coroutines.test.TestCoroutineScope;
import kotlinx.coroutines.test.runBlockingTest;
import org.junit.After;
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
@ExperimentalCoroutinesApi
public class TasksScreenTest {

  private TasksRepository repository;
  private TestCoroutineScope testScope;

  @Rule
  public ActivityTestRule composeTestRule = new ActivityTestRule<>(
    TasksActivity.class
  );

  private Context activity;

  @Before
  public void initRepository() {
    repository = new FakeRepository();
    ServiceLocator.tasksRepository = repository;
    testScope = new TestCoroutineScope();
  }

  @After
  public void cleanupDb() {
    ServiceLocator.resetRepository();
  }

  public void displayTask_whenRepositoryHasData() {
    Task task = new Task("TITLE1", "DESCRIPTION1");
    repository.saveTaskBlocking(task);

    launchScreen();

    onView(withText("TITLE1")).check(matches(isDisplayed()));
  }

  public void displayActiveTask() {
    Task task = new Task("TITLE1", "DESCRIPTION1");
    repository.saveTaskBlocking(task);

    launchScreen();

    onView(withText("TITLE1")).check(matches(isDisplayed()));

    ViewAction clickAction = ViewActions.click();
    onView(withId(R.id.menu_filter)).perform(clickAction);
    onView(withText(R.string.nav_active)).perform(clickAction);
    onView(withText("TITLE1")).check(matches(isDisplayed()));

    onView(withId(R.id.menu_filter)).perform(clickAction);
    onView(withText(R.string.nav_completed)).perform(clickAction);
    onView(withText("TITLE1")).check(doesNotExist());
  }

  public void displayCompletedTask() {
    Task task = new Task("TITLE1", "DESCRIPTION1", true);
    repository.saveTaskBlocking(task);

    launchScreen();

    onView(withText("TITLE1")).check(matches(isDisplayed()));

    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(R.string.nav_active)).perform(click());
    onView(withText("TITLE1")).check(doesNotExist());

    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(R.string.nav_completed)).perform(click());

    onView(withText("TITLE1")).check(matches(isDisplayed()));
  }

  public void deleteOneTask() {
    Task task = new Task("TITLE1", "DESCRIPTION1");
    repository.saveTaskBlocking(task);

    launchScreen();

    // Open it in details view
    onView(withText("TITLE1")).perform(click());

    // Click delete task in menu
    onView(withId(R.id.menu_delete)).perform(click());

    // Verify it was deleted
    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(R.string.nav_all)).perform(click());
    onView(withText("TITLE1")).check(doesNotExist());
  }

  public void deleteOneOfTwoTasks() {
    Task task1 = new Task("TITLE1", "DESCRIPTION1");
    Task task2 = new Task("TITLE2", "DESCRIPTION2");
    repository.saveTaskBlocking(task1);
    repository.saveTaskBlocking(task2);

    launchScreen();

    // Open it in details view
    onView(withText("TITLE1")).perform(click());

    // Click delete task in menu
    onView(withId(R.id.menu_delete)).perform(click());

    // Verify it was deleted
    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(R.string.nav_all)).perform(click());
    onView(withText("TITLE1")).check(doesNotExist());
    // but not the other one
    onView(withText("TITLE2")).check(matches(isDisplayed()));
  }

  public void markTaskAsComplete() {
    Task task = new Task("TITLE1", "DESCRIPTION1");
    repository.saveTaskBlocking(task);

    launchScreen();

    // Mark the task as complete
    onView(withContentDescription("Toggle complete")).perform(click());

    // Verify task is shown as complete
    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(R.string.nav_all)).perform(click());
    onView(withText("TITLE1")).check(matches(isDisplayed()));
    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(R.string.nav_active)).perform(click());
    onView(withText("TITLE1")).check(doesNotExist());
    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(R.string.nav_completed)).perform(click());
    onView(withText("TITLE1")).check(matches(isDisplayed()));
  }

  public void markTaskAsActive() {
    Task task = new Task("TITLE1", "DESCRIPTION1", true);
    repository.saveTaskBlocking(task);

    launchScreen();

    // Mark the task as active
    onView(withContentDescription("Toggle complete")).perform(click());

    // Verify task is shown as active
    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(R.string.nav_all)).perform(click());
    onView(withText("TITLE1")).check(matches(isDisplayed()));
    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(R.string.nav_active)).perform(click());
    onView(withText("TITLE1")).check(matches(isDisplayed()));
    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(R.string.nav_completed)).perform(click());
    onView(withText("TITLE1")).check(doesNotExist());
  }

  public void showAllTasks() {
    // Add one active task and one completed task
    Task task1 = new Task("TITLE1", "DESCRIPTION1");
    Task task2 = new Task("TITLE2", "DESCRIPTION2", true);
    repository.saveTaskBlocking(task1);
    repository.saveTaskBlocking(task2);

    launchScreen();

    // Verify that both of our tasks are shown
    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(R.string.nav_all)).perform(click());
    onView(withText("TITLE1")).check(matches(isDisplayed()));
    onView(withText("TITLE2")).check(matches(isDisplayed()));
  }

  public void showActiveTasks() {
    // Add 2 active tasks and one completed task
    Task task1 = new Task("TITLE1", "DESCRIPTION1");
    Task task2 = new Task("TITLE2", "DESCRIPTION2");
    Task task3 = new Task("TITLE3", "DESCRIPTION3", true);
    repository.saveTaskBlocking(task1);
    repository.saveTaskBlocking(task2);
    repository.saveTaskBlocking(task3);

    launchScreen();

    // Verify that the active tasks (but not the completed task) are shown
    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(R.string.nav_active)).perform(click());
    onView(withText("TITLE1")).check(matches(isDisplayed()));
    onView(withText("TITLE2")).check(matches(isDisplayed()));
    onView(withText("TITLE3")).check(doesNotExist());
  }

  public void showCompletedTasks() {
    // Add one active task and 2 completed tasks
    Task task1 = new Task("TITLE1", "DESCRIPTION1");
    Task task2 = new Task("TITLE2", "DESCRIPTION2", true);
    Task task3 = new Task("TITLE3", "DESCRIPTION3", true);
    repository.saveTaskBlocking(task1);
    repository.saveTaskBlocking(task2);
    repository.saveTaskBlocking(task3);

    launchScreen();

    // Verify that the completed tasks (but not the active task) are shown
    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(R.string.nav_completed)).perform(click());
    onView(withText("TITLE1")).check(doesNotExist());
    onView(withText("TITLE2")).check(matches(isDisplayed()));
    onView(withText("TITLE3")).check(matches(isDisplayed()));
  }

  public void clearCompletedTasks() {
    // Add one active task and one completed task
    Task task1 = new Task("TITLE1", "DESCRIPTION1");
    Task task2 = new Task("TITLE2", "DESCRIPTION2", true);
    repository.saveTaskBlocking(task1);
    repository.saveTaskBlocking(task2);

    launchScreen();

    // Click clear completed in menu
    Espresso.openActionBarOverflowOrOptionsMenu(
      ApplicationProvider.getApplicationContext()
    );
    onView(withText(R.string.menu_clear)).perform(click());

    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(R.string.nav_all)).perform(click());
    // Verify that only the active task is shown
    onView(withText("TITLE1")).check(matches(isDisplayed()));
    onView(withText("TITLE2")).check(doesNotExist());
  }

  public void noTasks_AllTasksFilter_AddTaskViewVisible() {
    launchScreen();

    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(R.string.nav_all)).perform(click());

    // Verify the "You have no tasks!" text is shown
    onView(withText("You have no tasks!"))
      .inRoot(RootMatchers.isPlatformPopup())
      .check(matches(isDisplayed()));
  }

  public void noTasks_CompletedTasksFilter_AddTaskViewNotVisible() {
    launchScreen();

    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(R.string.nav_completed)).perform(click());

    // Verify the "You have no completed tasks!" text is shown
    onView(withText("You have no completed tasks!"))
      .inRoot(RootMatchers.isPlatformPopup())
      .check(matches(isDisplayed()));
  }

  public void noTasks_ActiveTasksFilter_AddTaskViewNotVisible() {
    launchScreen();

    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(R.string.nav_active)).perform(click());

    // Verify the "You have no active tasks!" text is shown
    onView(withText("You have no active tasks!"))
      .inRoot(RootMatchers.isPlatformPopup())
      .check(matches(isDisplayed()));
  }

  public void clickAddTaskButton_navigateToAddEditFragment() {
    // GIVEN - On the home screen
    launchScreen();

    // WHEN - Click on the "+" button
    onView(
      withContentDescription(
        InstrumentationRegistry
          .getInstrumentation()
          .getTargetContext()
          .getString(R.string.add_task)
      )
    )
      .perform(click());

    // THEN - Verify that we navigate to the add screen
    NavController navController = composeTestRule
      .getActivity()
      .findNavController(R.id.nav_host_fragment);
    assertEquals(
      navController.getCurrentDestination().getId(),
      R.id.add_edit_task_fragment_dest
    );
  }

  private void launchScreen() {
    composeTestRule
      .getActivityRule()
      .getActivity()
      .runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            navController.setGraph(R.navigation.nav_graph);
          }
        }
      );
  }
}
