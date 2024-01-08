package com.example.android.architecture.blueprints.todoapp.tasks;

import android.content.Context
import android.os.Bundle
import androidx.activity.InstrumentationActivityRunner
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.testing.FragmentScenario
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.example.android.architecture.blueprints.todoapp.R
import com.example.android.architecture.blueprints.todoapp.ServiceLocator
import com.example.android.architecture.blueprints.todoapp.data.Task
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository
import com.example.android.architecture.blueprints.todoapp.util.deleteAllTasksBlocking
import com.example.android.architecture.blueprints.todoapp.util.saveTaskBlocking
import org.hamcrest.core.IsNot.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.TextLayoutMode

import static org.robolectric.Shadows.shadowOf;

@RunWith(AndroidJUnit4.class)
@MediumTest
@LooperMode(LooperMode.Mode.PAUSED)
@TextLayoutMode(TextLayoutMode.Mode.REALISTIC)
class TasksFragmentTest {


private static TasksRepository repository;

@Rule
public ActivityTestRule<TasksActivity> activityTestRule =
        new ActivityTestRule<>(TasksActivity.class, true, false);

@Before
public void setup() {
    Context context = ApplicationProvider.getApplicationContext();
    repository = ServiceLocator.provideTasksRepository(context);
    repository.deleteAllTasksBlocking();
}

@After
public void tearDown() {
    repository.deleteAllTasksBlocking();
}

@Test
public void clickAddTaskButton_navigateToAddEditFragment() {
    FragmentScenario<TasksFragment> fragmentScenario =
            FragmentScenario.launchInContainer(TasksFragment.class, new Bundle(), R.style.AppTheme);

    NavController navController = mock(NavController.class);
    fragmentScenario.onFragment(fragment -> {
        Navigation.setViewNavController(fragment.requireView(), navController);
    });

    onView(withId(R.id.fab_add_task)).perform(click());

    verify(navController).navigate(
            TasksFragmentDirections.actionTasksFragmentToAddEditTaskFragment(
                    null, InstrumentationRegistry.getInstrumentation().getTargetContext()
                            .getString(R.string.add_task)));
}

@Test
@MediumTest
public void displayTask_whenRepositoryHasData() {
    repository.saveTaskBlocking(new Task("title", "description"));

    FragmentScenario<TasksFragment> fragmentScenario =
            FragmentScenario.launchInContainer(TasksFragment.class, new Bundle(), R.style.AppTheme);

    onView(withText("title")).check(matches(isDisplayed()));
}

@Test
public void displayActiveTask() {
    repository.saveTaskBlocking(new Task("TITLE1", "DESCRIPTION1"));

    activityTestRule.launchActivity(new InstrumentationActivityRunner.NewIntentCommand(
            InstrumentationRegistry.getInstrumentation().getTargetContext(), TasksActivity.class, null));

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

    activityTestRule.launchActivity(new InstrumentationActivityRunner.NewIntentCommand(
            InstrumentationRegistry.getInstrumentation().getTargetContext(), TasksActivity.class, null));

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

    activityTestRule.launchActivity(new InstrumentationActivityRunner.NewIntentCommand(
            InstrumentationRegistry.getInstrumentation().getTargetContext(), TasksActivity.class, null));

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

    activityTestRule.launchActivity(new InstrumentationActivityRunner.NewIntentCommand(
            InstrumentationRegistry.getInstrumentation().getTargetContext(), TasksActivity.class, null));

    onView(withText("TITLE1")).perform(click());

    onView(withId(R.id.menu_delete)).perform(click());

    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(R.string.nav_all)).perform(click());
    onView(withText("TITLE1")).check(doesNotExist());

    onView(withText("TITLE2")).check(matches(isDisplayed()));
}
}