package com.example.android.architecture.blueprints.todoapp.tasks;

import android.view.View;
import android.widget.ListView;
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
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import com.example.android.architecture.blueprints.todoapp.util.EspressoIdlingResource;
import com.example.android.architecture.blueprints.todoapp.util.ServiceLocator;
import kotlinx.coroutines.runBlocking;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class TasksActivityTest {

    private final String TITLE1 = "TITLE1";
    private final String TITLE2 = "TITLE2";
    private final String TITLE3 = "TITLE3";
    private final String DESCRIPTION = "DESCR";

    private TasksRepository repository;

    @UiThreadTest
    @Before
    public void resetState() {
        repository = ServiceLocator.provideTasksRepository(ApplicationProvider.getApplicationContext());
        runBlocking(() -> repository.deleteAllTasks());
    }

    @Before
    public void registerIdlingResource() {
        IdlingRegistry.getInstance().register(EspressoIdlingResource.countingIdlingResource);
    }

    @After
    public void unregisterIdlingResource() {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.countingIdlingResource);
    }

    private Matcher<View> withItemText(String itemText) {
        checkArgument(itemText != null && !itemText.isEmpty(), "itemText cannot be null or empty");
        return new TypeSafeMatcher<View>() {
            @Override
            protected boolean matchesSafely(View item) {
                return Matchers.allOf(
                        ViewMatchers.isDescendantOfA(ViewMatchers.isAssignableFrom(ListView.class)),
                        ViewMatchers.withText(itemText)
                ).matches(item);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("is isDescendantOfA LV with text ").appendText(itemText);
            }
        };
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

        Espresso.onView(withItemText("title")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void editTask() {
        repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION));

        ActivityScenario<TaskActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);

        Espresso.onView(ViewMatchers.withText(TITLE1)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withId(R.id.fab_edit_task)).perform(ViewActions.click());

        String editTaskTitle = TITLE2;
        String editTaskDescription = "New Description";

        Espresso.onView(ViewMatchers.withId(R.id.add_task_title))
                .perform(ViewActions.replaceText(editTaskTitle), ViewActions.closeSoftKeyboard());
        Espresso.onView(ViewMatchers.withId(R.id.add_task_description))
                .perform(ViewActions.replaceText(editTaskDescription), ViewActions.closeSoftKeyboard());

        Espresso.onView(ViewMatchers.withId(R.id.fab_save_task)).perform(ViewActions.click());
        Espresso.onView(withItemText(editTaskTitle)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(withItemText(TITLE1)).check(ViewAssertions.doesNotExist());
    }

    @Test
    public void markTaskAsComplete() {
        repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION));

        ActivityScenario<TaskActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);

        clickCheckBoxForTask(TITLE1);

        viewAllTasks();
        Espresso.onView(withItemText(TITLE1)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        viewActiveTasks();
        Espresso.onView(withItemText(TITLE1)).check(ViewAssertions.matches(ViewMatchers.not(ViewMatchers.isDisplayed())));
        viewCompletedTasks();
        Espresso.onView(withItemText(TITLE1)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void markTaskAsActive() {
        repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION, true));

        ActivityScenario<TaskActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);

        clickCheckBoxForTask(TITLE1);

        viewAllTasks();
        Espresso.onView(withItemText(TITLE1)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        viewActiveTasks();
        Espresso.onView(withItemText(TITLE1)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        viewCompletedTasks();
        Espresso.onView(withItemText(TITLE1)).check(ViewAssertions.matches(ViewMatchers.not(ViewMatchers.isDisplayed())));
    }

    @Test
    public void showAllTasks() {
        repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION));
        repository.saveTaskBlocking(new Task(TITLE2, DESCRIPTION, true));

        ActivityScenario<TaskActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);

        viewAllTasks();
        Espresso.onView(withItemText(TITLE1)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(withItemText(TITLE2)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void showActiveTasks() {
        repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION));
        repository.saveTaskBlocking(new Task(TITLE2, DESCRIPTION));
        repository.saveTaskBlocking(new Task(TITLE3, DESCRIPTION, true));

        ActivityScenario<TaskActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);

        viewActiveTasks();
        Espresso.onView(withItemText(TITLE1)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(withItemText(TITLE2)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(withItemText(TITLE3)).check(ViewAssertions.doesNotExist());
    }

    @Test
    public void showCompletedTasks() {
        repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION));
        repository.saveTaskBlocking(new Task(TITLE2, DESCRIPTION, true));
        repository.saveTaskBlocking(new Task(TITLE3, DESCRIPTION, true));

        ActivityScenario<TaskActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);

        viewCompletedTasks();
        Espresso.onView(withItemText(TITLE1)).check(ViewAssertions.doesNotExist());
        Espresso.onView(withItemText(TITLE2)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(withItemText(TITLE3)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }
@Test
    public void clearCompletedTasks() {

        repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION));
        repository.saveTaskBlocking(new Task(TITLE2, DESCRIPTION, true));

        ActivityScenario.launch(TasksActivity.class);

        viewAllTasks();

        Espresso.openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext());
        Espresso.onView(ViewMatchers.withText(string.menu_clear)).perform(ViewActions.click());

        viewAllTasks();

        Espresso.onView(withItemText(TITLE1)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(withItemText(TITLE2)).check(ViewAssertions.doesNotExist());
    }

    @Test
    public void createOneTask_deleteTask() {
        ActivityScenario.launch(TasksActivity.class);
        viewAllTasks();

        createTask(TITLE1, DESCRIPTION);

        Espresso.onView(ViewMatchers.withText(TITLE1)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_delete)).perform(ViewActions.click());

        viewAllTasks();
        Espresso.onView(ViewMatchers.withText(TITLE1)).check(ViewAssertions.doesNotExist());
    }

    @Test
    public void createTwoTasks_deleteOneTask() {
        ActivityScenario.launch(TasksActivity.class);

        createTask(TITLE1, DESCRIPTION);
        createTask(TITLE2, DESCRIPTION);

        Espresso.onView(ViewMatchers.withText(TITLE2)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.menu_delete)).perform(ViewActions.click());

        viewAllTasks();
        Espresso.onView(ViewMatchers.withText(TITLE1)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        Espresso.onView(ViewMatchers.withText(TITLE2)).check(ViewAssertions.doesNotExist());
    }

    @Test
    public void markTaskAsCompleteOnDetailScreen_taskIsCompleteInList() {

        repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION));

        ActivityScenario.launch(TasksActivity.class);
        viewAllTasks();

        Espresso.onView(ViewMatchers.withText(TITLE1)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).perform(ViewActions.click());

        Espresso.pressBack();

        Espresso.onView(Matchers.allOf(ViewMatchers.withId(R.id.complete),
                ViewMatchers.hasSibling(ViewMatchers.withText(TITLE1))))
                .check(ViewAssertions.matches(ViewMatchers.isChecked()));
    }

    @Test
    public void markTaskAsActiveOnDetailScreen_taskIsActiveInList() {

        repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION, true));

        ActivityScenario.launch(TasksActivity.class);
        viewAllTasks();

        Espresso.onView(ViewMatchers.withText(TITLE1)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).perform(ViewActions.click());

        Espresso.pressBack();

        Espresso.onView(Matchers.allOf(ViewMatchers.withId(R.id.complete),
                ViewMatchers.hasSibling(ViewMatchers.withText(TITLE1))))
                .check(ViewAssertions.matches(IsNot.not(ViewMatchers.isChecked())));
    }

    @Test
    public void markTaskAsCompleteAndActiveOnDetailScreen_taskIsActiveInList() {

        repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION));

        ActivityScenario.launch(TasksActivity.class);
        viewAllTasks();

        Espresso.onView(ViewMatchers.withText(TITLE1)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).perform(ViewActions.click());

        Espresso.pressBack();

        Espresso.onView(Matchers.allOf(ViewMatchers.withId(R.id.complete),
                ViewMatchers.hasSibling(ViewMatchers.withText(TITLE1))))
                .check(ViewAssertions.matches(IsNot.not(ViewMatchers.isChecked())));
    }

    @Test
    public void markTaskAsActiveAndCompleteOnDetailScreen_taskIsCompleteInList() {

        repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION, true));

        ActivityScenario.launch(TasksActivity.class);
        viewAllTasks();

        Espresso.onView(ViewMatchers.withText(TITLE1)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).perform(ViewActions.click());

        Espresso.pressBack();

        Espresso.onView(Matchers.allOf(ViewMatchers.withId(R.id.complete),
                ViewMatchers.hasSibling(ViewMatchers.withText(TITLE1))))
                .check(ViewAssertions.matches(ViewMatchers.isChecked()));
    }

    @Test
    public void noTasks_AllTasksFilter_AddTaskViewVisible() {
        ActivityScenario.launch(TasksActivity.class);
        viewAllTasks();

        Espresso.onView(ViewMatchers.withText("You have no TO-DOs!")).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void noTasks_CompletedTasksFilter_AddTaskViewNotVisible() {
        ActivityScenario.launch(TasksActivity.class);
        viewCompletedTasks();

        Espresso.onView(ViewMatchers.withText("You have no completed TO-DOs!")).check(ViewAssertions.matches((ViewMatchers.isDisplayed())));
    }

    @Test
    public void noTasks_ActiveTasksFilter_AddTaskViewNotVisible() {
        ActivityScenario.launch(TasksActivity.class);
        viewActiveTasks();

        Espresso.onView(ViewMatchers.withText("You have no active TO-DOs!")).check(ViewAssertions.matches((ViewMatchers.isDisplayed())));
    }

    private void viewAllTasks() {
        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(string.nav_all)).perform(ViewActions.click());
    }

    private void viewActiveTasks() {
        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(string.nav_active)).perform(ViewActions.click());
    }

    private void viewCompletedTasks() {
        Espresso.onView(ViewMatchers.withId(R.id.menu_filter)).perform(ViewActions.click());
        Espresso.onView(ViewMatchers.withText(string.nav_completed)).perform(ViewActions.click());
    }

    private void createTask(String title, String description) {
        Espresso.onView(ViewMatchers.withId(R.id.fab_add_task)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.add_task_title)).perform(
                ViewActions.typeText(title),
                ViewActions.closeSoftKeyboard()
        );
        Espresso.onView(ViewMatchers.withId(R.id.add_task_description)).perform(
                ViewActions.typeText(description),
                ViewActions.closeSoftKeyboard()
        );

        Espresso.onView(ViewMatchers.withId(R.id.fab_save_task)).perform(ViewActions.click());
    }
    private void clickCheckBoxForTask(String title) {
        Espresso.onView(Matchers.allOf(ViewMatchers.withId(R.id.complete),
                ViewMatchers.hasSibling(ViewMatchers.withText(title)))).perform(ViewActions.click());
    }
}
