
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
import com.example.android.architecture.blueprints.todoapp.R.string;
import com.example.android.architecture.blueprints.todoapp.ServiceLocator;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import com.example.android.architecture.blueprints.todoapp.util.EspressoIdlingResource;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.hamcrest.core.IsNot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

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
        repository = ServiceLocator.provideTasksRepository(getApplicationContext());
        try {
            repository.deleteAllTasks();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
        Preconditions.checkArgument(itemText != null && !itemText.isEmpty(), "itemText cannot be null or empty");
        return new TypeSafeMatcher<View>() {
            @Override
            public boolean matchesSafely(View item) {
                return allOf(
                        isDescendantOfA(isAssignableFrom(ListView.class)),
                        withText(itemText)
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
                .perform(typeText("title"));
        onView(withId(R.id.add_task_description))
                .perform(typeText("description"));
        onView(withId(R.id.fab_save_task)).perform(click());

        onView(withItemText("title")).check(matches(isDisplayed()));
    }

    @Test
    public void editTask() {
        repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION));

        ActivityScenario.launch(TasksActivity.class);

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

        ActivityScenario.launch(TasksActivity.class);

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

        ActivityScenario.launch(TasksActivity.class);

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

        ActivityScenario.launch(TasksActivity.class);

        viewAllTasks();
        onView(withItemText(TITLE1)).check(matches(isDisplayed()));
        onView(withItemText(TITLE2)).check(matches(isDisplayed()));
    }

    @Test
    public void showActiveTasks() {
        repository.saveTaskBlocking(new Task(TITLE1, DESCRIPTION));
        repository.saveTaskBlocking(new Task(TITLE2, DESCRIPTION));
        repository.saveTaskBlocking(new Task(TITLE3, DESCRIPTION, true));

        ActivityScenario.launch(TasksActivity.class);

        viewActiveTasks();
       