package com.example.android.architecture.blueprints.todoapp.tasks;

import android.view.View;
import android.widget.ListView;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.Espresso.onView;
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import androidx.test.espresso.Espresso.pressBack;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.action.ViewActions.click;
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import androidx.test.espresso.action.ViewActions.replaceText;
import androidx.test.espresso.action.ViewActions.typeText;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import androidx.test.espresso.matcher.ViewMatchers.isChecked;
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import androidx.test.espresso.matcher.ViewMatchers.withId;
import androidx.test.espresso.matcher.ViewMatchers.withText;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.R.string;
import com.example.android.architecture.blueprints.todoapp.ServiceLocator;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import com.example.android.architecture.blueprints.todoapp.util.EspressoIdlingResource;
import com.example.android.architecture.blueprints.todoapp.util.saveTaskBlocking;
import com.google.common.base.Preconditions;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutionException;

import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class TasksActivityTest {


private static final String TITLE1 = "TITLE1";
private static final String TITLE2 = "TITLE2";
private static final String TITLE3 = "TITLE3";
private static final String DESCRIPTION = "DESCR";

private TasksRepository repository;

@UiThreadTest
@Before
public void resetState() {
    repository = ServiceLocator.provideTasksRepository(ApplicationProvider.getApplicationContext());
    new Thread(() -> {
        try {
            repository.deleteAllTasks().get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }).start();
}

@BeforeClass
public static void registerIdlingResource() {
    IdlingRegistry.getInstance().register(EspressoIdlingResource.countingIdlingResource);
}

@After
public static void unregisterIdlingResource() {
    IdlingRegistry.getInstance().unregister(EspressoIdlingResource.countingIdlingResource);
}

public static Matcher<View> withItemText(final String itemText) {
    Preconditions.checkArgument(!itemText.isEmpty(), "itemText cannot be null or empty");
    return new TypeSafeMatcher<View>() {
        @Override
        protected boolean matchesSafely(View item) {
            return allOf(
                    ViewMatchers.isDescendantOfA(ViewMatchers.isAssignableFrom(ListView.class)),
                    ViewMatchers.withText(itemText)
            ).matches(item);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("is isDescendantOfA LV with text " + itemText);
        }
    };
}

@Test
public void createTask() {

    ActivityScenario.launch(TasksActivity.class);


    onView(withId(R.id.fab_add_task)).perform(click());
    onView(withId(R.id.add_task_title))
            .perform(typeText("title"), closeSoftKeyboard());
    onView(withId(R.id.add_task_description))
            .perform(typeText("description"), closeSoftKeyboard());
    onView(withId(R.id.fab_save_task)).perform(click());


    onView(withItemText("title")).check(matches(isDisplayed()));
}

@Test
public void editTask() {
    repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION));

    ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);


    onView(withText(TITLE1)).perform(click());


    onView(withId(R.id.fab_edit_task)).perform(click());

    String editTaskTitle = TITLE2;
    String editTaskDescription = "New Description";


    onView(withId(R.id.add_task_title))
            .perform(replaceText(editTaskTitle), closeSoftKeyboard());
    onView(withId(R.id.add_task_description)).perform(
            replaceText(editTaskDescription),
            closeSoftKeyboard()
    );


    onView(withId(R.id.fab_save_task)).perform(click());


    onView(withItemText(editTaskTitle)).check(matches(isDisplayed()));


    onView(withItemText(TITLE1)).check(doesNotExist());
}


@Test
public void markTaskAsComplete() {
    repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION));

    ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);


    clickCheckBoxForTask(TITLE1);


    viewAllTasks();
    onView(withItemText(TITLE1)).check(matches(isDisplayed()));
    viewActiveTasks();
    onView(withItemText(TITLE1)).check(matches(not(isDisplayed())));
    viewCompletedTasks();
    onView(withItemText(TITLE1)).check(matches(isDisplayed()));
}


@Test
public void markTaskAsActive() {
    repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION, true));

    ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);


    clickCheckBoxForTask(TITLE1);


    viewAllTasks();
    onView(withItemText(TITLE1)).check(matches(isDisplayed()));
    viewActiveTasks();
    onView(withItemText(TITLE1)).check(matches(isDisplayed()));
    viewCompletedTasks();
    onView(withItemText(TITLE1)).check(matches(not(isDisplayed())));
}


@Test
public void showAllTasks() {

    repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION));
    repository.saveTaskBlocking(new Task(TITLE2, DESCRIPTION, true));

    ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);


    viewAllTasks();
    onView(withItemText(TITLE1)).check(matches(isDisplayed()));
    onView(withItemText(TITLE2)).check(matches(isDisplayed()));
}


@Test
public void showActiveTasks() {

    repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION));
    repository.saveTaskBlocking(new Task(TITLE2, DESCRIPTION));
    repository.saveTaskBlocking(new Task(TITLE3, DESCRIPTION, true));

    ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);


    viewActiveTasks();
    onView(withItemText(TITLE1)).check(matches(isDisplayed()));
    onView(withItemText(TITLE2)).check(matches(isDisplayed()));
    onView(withItemText(TITLE3)).check(doesNotExist());
}


@Test
public void showCompletedTasks() {

    repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION));
    repository.saveTaskBlocking(new Task(TITLE2, DESCRIPTION, true));
    repository.saveTaskBlocking(new Task(TITLE3, DESCRIPTION, true));

    ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);


    viewCompletedTasks();
    onView(withItemText(TITLE1)).check(doesNotExist());
    onView(withItemText(TITLE2)).check(matches(isDisplayed()));
    onView(withItemText(TITLE3)).check(matches(isDisplayed()));
}


@Test
public void clearCompletedTasks() {

    repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION));
    repository.saveTaskBlocking(new Task(TITLE2, DESCRIPTION, true));

    ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);

    viewAllTasks();


    openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext());
    onView(withText(string.menu_clear)).perform(click());

    viewAllTasks();


    onView(withItemText(TITLE1)).check(matches(isDisplayed()));
    onView(withItemText(TITLE2)).check(doesNotExist());
}

@Test
public void createOneTask_deleteTask() {
    ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);
    viewAllTasks();


    createTask(TITLE1, DESCRIPTION);


    onView(withText(TITLE1)).perform(click());


    onView(withId(R.id.menu_delete)).perform(click());


    viewAllTasks();
    onView(withText(TITLE1)).check(doesNotExist());
}

@Test
public void createTwoTasks_deleteOneTask() {
    ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);

    createTask(TITLE1, DESCRIPTION);
    createTask(TITLE2, DESCRIPTION);


    onView(withText(TITLE2)).perform(click());


    onView(withId(R.id.menu_delete)).perform(click());


    viewAllTasks();
    onView(withText(TITLE1)).check(matches(isDisplayed()));
    onView(withText(TITLE2)).check(doesNotExist());
}

@Test
public void markTaskAsCompleteOnDetailScreen_taskIsCompleteInList() {

    repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION));

    ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);
    viewAllTasks();


    onView(withText(TITLE1)).perform(click());


    onView(withId(R.id.task_detail_complete)).perform(click());


    pressBack();


    onView(allOf(withId(R.id.complete), hasSibling(withText(TITLE1)))).check(matches(isChecked()));
}

@Test
public void markTaskAsActiveOnDetailScreen_taskIsActiveInList() {

    repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION, true));

    ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);
    viewAllTasks();


    onView(withText(TITLE1)).perform(click());


    onView(withId(R.id.task_detail_complete)).perform(click());


    pressBack();


    onView(allOf(withId(R.id.complete), hasSibling(withText(TITLE1)))).check(matches(not(isChecked())));
}

@Test
public void markTaskAsCompleteAndActiveOnDetailScreen_taskIsActiveInList() {

    repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION));

    ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);
    viewAllTasks();


    onView(withText(TITLE1)).perform(click());


    onView(withId(R.id.task_detail_complete)).perform(click());


    onView(withId(R.id.task_detail_complete)).perform(click());


    pressBack();


    onView(allOf(withId(R.id.complete), hasSibling(withText(TITLE1)))).check(matches(not(isChecked())));
}

@Test
public void markTaskAsActiveAndCompleteOnDetailScreen_taskIsCompleteInList() {

    repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION, true));

    ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);
    viewAllTasks();


    onView(withText(TITLE1)).perform(click());


    onView(withId(R.id.task_detail_complete)).perform(click());


    onView(withId(R.id.task_detail_complete)).perform(click());


    pressBack();


    onView(allOf(withId(R.id.complete), hasSibling(withText(TITLE1)))).check(matches(isChecked()));
}


@Test
public void noTasks_AllTasksFilter_AddTaskViewVisible() {
    ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);
    viewAllTasks();


    onView(withText("You have no TO-DOs!")).check(matches(isDisplayed()));
}


@Test
public void noTasks_CompletedTasksFilter_AddTaskViewNotVisible() {
    ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);
    viewCompletedTasks();


    onView(withText("You have no completed TO-DOs!")).check(matches((isDisplayed())));
}


@Test
public void noTasks_ActiveTasksFilter_AddTaskViewNotVisible() {
    ActivityScenario<TasksActivity> activityScenario = ActivityScenario.launch(TasksActivity.class);
    viewActiveTasks();


    onView(withText("You have no active TO-DOs!")).check(matches((isDisplayed())));
}

private void viewAllTasks() {
    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(string.nav_all)).perform(click());
}

private void viewActiveTasks() {
    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(string.nav_active)).perform(click());
}

private void viewCompletedTasks() {
    onView(withId(R.id.menu_filter)).perform(click());
    onView(withText(string.nav_completed)).perform(click());
}

private void createTask(String title, String description) {

    onView(withId(R.id.fab_add_task)).perform(click());


    onView(withId(R.id.add_task_title)).perform(
            typeText(title),
            closeSoftKeyboard()
    );
    onView(withId(R.id.add_task_description)).perform(
            typeText(description),
            closeSoftKeyboard()
    );


    onView(withId(R.id.fab_save_task)).perform(click());
}

private void clickCheckBoxForTask(String title) {
    onView(allOf(withId(R.id.complete), hasSibling(withText(title)))).perform(click());
}
}