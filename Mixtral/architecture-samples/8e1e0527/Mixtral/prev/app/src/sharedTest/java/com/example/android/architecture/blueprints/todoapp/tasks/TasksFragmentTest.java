package com.example.android.architecture.blueprints.todoapp.tasks;

import android.os.Bundle;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.app.ApplicationProvider.getApplicationContext;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.Espresso.onView;
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.assertion.ViewAssertions.matches;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.R.id;
import com.example.android.architecture.blueprints.todoapp.ServiceLocator;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.FakeRepository;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import com.example.android.architecture.blueprints.todoapp.databinding.TasksFragmentBinding;
import com.example.android.architecture.blueprints.todoapp.util.saveTaskBlocking;
import com.google.common.base.Verify;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.TextLayoutMode;
import org.robolectric.shadows.ShadowToast;

@RunWith(AndroidJUnit4.class)
@MediumTest
@LooperMode(LooperMode.Mode.PAUSED)
@TextLayoutMode(TextLayoutMode.Mode.REALISTIC)
public class TasksFragmentTest {

  private TasksRepository repository;

  @Rule
  public ActivityTestRule<TasksActivity> activityTestRule = new ActivityTestRule<>(
    TasksActivity.class
  );

  @Before
  public void initRepository() {
    repository = new FakeRepository();
    ServiceLocator.setTasksRepository(repository);
  }

  @After
  public void cleanupDb() {
    ServiceLocator.resetRepository();
  }

  @Test
  public void displayTask_whenRepositoryHasData() throws InterruptedException {
    repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));

    ActivityScenario<TasksActivity> activityScenario = launchActivity();

    onView(ViewMatchers.withText("TITLE1"))
      .check(matches(ViewMatchers.isDisplayed()));
  }

  @Test
  public void displayActiveTask() throws InterruptedException {
    repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));

    ActivityScenario<TasksActivity> activityScenario = launchActivity();

    onView(ViewMatchers.withText("TITLE1"))
      .check(matches(ViewMatchers.isDisplayed()));

    onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
    onView(ViewMatchers.withText(R.string.nav_active))
      .perform(ViewActions.click());
    onView(ViewMatchers.withText("TITLE1"))
      .check(matches(ViewMatchers.isDisplayed()));

    onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
    onView(ViewMatchers.withText(R.string.nav_completed))
      .perform(ViewActions.click());
    onView(ViewMatchers.withText("TITLE1"))
      .check(matches(ViewMatchers.not(ViewMatchers.isDisplayed())));
  }

  @Test
  public void displayCompletedTask() throws InterruptedException {
    repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1", true));

    ActivityScenario<TasksActivity> activityScenario = launchActivity();

    onView(ViewMatchers.withText("TITLE1"))
      .check(matches(ViewMatchers.isDisplayed()));

    onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
    onView(ViewMatchers.withText(R.string.nav_active))
      .perform(ViewActions.click());
    onView(ViewMatchers.withText("TITLE1"))
      .check(matches(ViewMatchers.not(ViewMatchers.isDisplayed())));

    onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
    onView(ViewMatchers.withText(R.string.nav_completed))
      .perform(ViewActions.click());

    onView(ViewMatchers.withText("TITLE1"))
      .check(matches(ViewMatchers.isDisplayed()));
  }

  @Test
  public void deleteOneTask() throws InterruptedException {
    repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));

    ActivityScenario<TasksActivity> activityScenario = launchActivity();

    onView(ViewMatchers.withText("TITLE1")).perform(ViewActions.click());

    onView(ViewMatchers.withId(R.id.menu_delete)).perform(ViewActions.click());

    onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
    onView(ViewMatchers.withText(R.string.nav_all))
      .perform(ViewActions.click());
    onView(ViewMatchers.withText("TITLE1"))
      .check(ViewAssertions.doesNotExist());
  }

  @Test
  public void deleteOneOfTwoTasks() throws InterruptedException {
    repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));
    repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION2"));

    ActivityScenario<TasksActivity> activityScenario = launchActivity();

    onView(ViewMatchers.withText("TITLE1")).perform(ViewActions.click());

    onView(ViewMatchers.withId(R.id.menu_delete)).perform(ViewActions.click());

    onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
    onView(ViewMatchers.withText(R.string.nav_all))
      .perform(ViewActions.click());
    onView(ViewMatchers.withText("TITLE1"))
      .check(ViewAssertions.doesNotExist());

    onView(ViewMatchers.withText("TITLE2"))
      .check(matches(ViewMatchers.isDisplayed()));
  }

  @Test
  public void markTaskAsComplete() throws InterruptedException {
    repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));

    ActivityScenario<TasksActivity> activityScenario = launchActivity();

    onView(ViewMatchers.withText("TITLE1")).perform(ViewActions.click());

    onView(ViewMatchers.withId(R.id.complete_checkbox))
      .perform(ViewActions.click());

    onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
    onView(ViewMatchers.withText(R.string.nav_all))
      .perform(ViewActions.click());
    onView(ViewMatchers.withText("TITLE1"))
      .check(matches(ViewMatchers.isDisplayed()));
    onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
    onView(ViewMatchers.withText(R.string.nav_active))
      .perform(ViewActions.click());
    onView(ViewMatchers.withText("TITLE1"))
      .check(matches(ViewMatchers.not(ViewMatchers.isDisplayed())));
    onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
    onView(ViewMatchers.withText(R.string.nav_completed))
      .perform(ViewActions.click());
    onView(ViewMatchers.withText("TITLE1"))
      .check(matches(ViewMatchers.isDisplayed()));
  }

  @Test
  public void markTaskAsActive() throws InterruptedException {
    repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1", true));

    ActivityScenario<TasksActivity> activityScenario = launchActivity();

    onView(ViewMatchers.withText("TITLE1")).perform(ViewActions.click());

    onView(ViewMatchers.withId(R.id.complete_checkbox))
      .perform(ViewActions.click());

    onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
    onView(ViewMatchers.withText(R.string.nav_all))
      .perform(ViewActions.click());
    onView(ViewMatchers.withText("TITLE1"))
      .check(matches(ViewMatchers.isDisplayed()));
    onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
    onView(ViewMatchers.withText(R.string.nav_active))
      .perform(ViewActions.click());
    onView(ViewMatchers.withText("TITLE1"))
      .check(matches(ViewMatchers.isDisplayed()));
    onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
    onView(ViewMatchers.withText(R.string.nav_completed))
      .perform(ViewActions.click());
    onView(ViewMatchers.withText("TITLE1"))
      .check(matches(ViewMatchers.not(ViewMatchers.isDisplayed())));
  }

  @Test
  public void showAllTasks() throws InterruptedException {
    repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));
    repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION2", true));

    ActivityScenario<TasksActivity> activityScenario = launchActivity();

    onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
    onView(ViewMatchers.withText(R.string.nav_all))
      .perform(ViewActions.click());
    onView(ViewMatchers.withText("TITLE1"))
      .check(matches(ViewMatchers.isDisplayed()));
    onView(ViewMatchers.withText("TITLE2"))
      .check(matches(ViewMatchers.isDisplayed()));
  }

  @Test
  public void showActiveTasks() throws InterruptedException {
    repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));
    repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION2"));
    repository.saveTaskBlocking(new Task("TITLE3", "DESCRIPTION3", true));

    ActivityScenario<TasksActivity> activityScenario = launchActivity();

    onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
    onView(ViewMatchers.withText(R.string.nav_active))
      .perform(ViewActions.click());
    onView(ViewMatchers.withText("TITLE1"))
      .check(matches(ViewMatchers.isDisplayed()));
    onView(ViewMatchers.withText("TITLE2"))
      .check(matches(ViewMatchers.isDisplayed()));
    onView(ViewMatchers.withText("TITLE3"))
      .check(ViewAssertions.doesNotExist());
  }

  @Test
  public void showCompletedTasks() throws InterruptedException {
    repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));
    repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION2", true));
    repository.saveTaskBlocking(new Task("TITLE3", "DESCRIPTION3", true));

    ActivityScenario<TasksActivity> activityScenario = launchActivity();

    onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
    onView(ViewMatchers.withText(R.string.nav_completed))
      .perform(ViewActions.click());
    onView(ViewMatchers.withText("TITLE1"))
      .check(ViewAssertions.doesNotExist());
    onView(ViewMatchers.withText("TITLE2"))
      .check(matches(ViewMatchers.isDisplayed()));
    onView(ViewMatchers.withText("TITLE3"))
      .check(matches(ViewMatchers.isDisplayed()));
  }

  @Test
  public void clearCompletedTasks() throws InterruptedException {
    repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));
    repository.saveTaskBlocking(new Task("TITLE2", "DESCRIPTION2", true));

    ActivityScenario<TasksActivity> activityScenario = launchActivity();

    openActionBarOverflowOrOptionsMenu(getApplicationContext());
    onView(ViewMatchers.withText(R.string.menu_clear))
      .perform(ViewActions.click());

    onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
    onView(ViewMatchers.withText(R.string.nav_all))
      .perform(ViewActions.click());

    onView(ViewMatchers.withText("TITLE1"))
      .check(matches(ViewMatchers.isDisplayed()));
    onView(ViewMatchers.withText("TITLE2"))
      .check(ViewAssertions.doesNotExist());
  }

  @Test
  public void noTasks_AllTasksFilter_AddTaskViewVisible()
    throws InterruptedException {
    ActivityScenario<TasksActivity> activityScenario = launchActivity();

    onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
    onView(ViewMatchers.withText(R.string.nav_all))
      .perform(ViewActions.click());

    onView(ViewMatchers.withText("You have no tasks!"))
      .check(matches(ViewMatchers.isDisplayed()));
  }

  @Test
  public void noTasks_CompletedTasksFilter_AddTaskViewNotVisible()
    throws InterruptedException {
    ActivityScenario<TasksActivity> activityScenario = launchActivity();

    onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
    onView(ViewMatchers.withText(R.string.nav_completed))
      .perform(ViewActions.click());

    onView(ViewMatchers.withText("You have no completed tasks!"))
      .check(matches(ViewMatchers.isDisplayed()));
  }

  @Test
  public void noTasks_ActiveTasksFilter_AddTaskViewNotVisible()
    throws InterruptedException {
    ActivityScenario<TasksActivity> activityScenario = launchActivity();

    onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
    onView(ViewMatchers.withText(R.string.nav_active))
      .perform(ViewActions.click());

    onView(ViewMatchers.withText("You have no active tasks!"))
      .check(matches(ViewMatchers.isDisplayed()));
  }

  @Test
  public void clickAddTaskButton_navigateToAddEditFragment()
    throws InterruptedException {
    ActivityScenario<TasksActivity> activityScenario = launchActivity();

    onView(ViewMatchers.withId(R.id.add_task_fab)).perform(ViewActions.click());

    // Add a small delay to allow the navigation to complete
    Thread.sleep(500);
    // You can add additional checks here to verify the destination of the navigation
  }

  private ActivityScenario<TasksActivity> launchActivity()
    throws InterruptedException {
    ActivityScenario<TasksActivity> activityScenario = launch(
      TasksActivity.class
    );
    activityScenario.onActivity(activity -> {
      RecyclerView tasksList = activity.findViewById(R.id.tasks_list);
      tasksList.setItemAnimator(null);
    });
    return activityScenario;
  }

  private Matcher<View> checkboxWithText(String text) {
    return allOf(withId(R.id.complete_checkbox), hasSibling(withText(text)));
  }
}
