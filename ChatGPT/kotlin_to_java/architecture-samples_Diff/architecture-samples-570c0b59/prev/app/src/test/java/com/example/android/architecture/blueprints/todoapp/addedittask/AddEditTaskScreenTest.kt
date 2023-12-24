
package com.example.android.architecture.blueprints.todoapp.addedittask;

import android.content.Intent;
import android.content.res.Resources;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.BoundedMatcher;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.FakeTasksRemoteDataSource;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.DefaultTasksRepository;
import com.example.android.architecture.blueprints.todoapp.util.rotateOrientation;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AddEditTaskScreenTest {

    @Test
    @Ignore("hangs in robolectric, see issue #4724")
    public void emptyTask_isNotSaved() {
        ActivityScenario<AddEditTaskActivity> activityScenario = ActivityScenario.launch(AddEditTaskActivity.class);

        Espresso.onView(ViewMatchers.withId(R.id.add_task_title)).perform(ViewActions.clearText());
        Espresso.onView(ViewMatchers.withId(R.id.add_task_description)).perform(ViewActions.clearText());

        Espresso.onView(ViewMatchers.withId(R.id.fab_edit_task_done)).perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.add_task_title)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void toolbarTitle_newTask_persistsRotation() {
        ActivityScenario<AddEditTaskActivity> activityScenario = ActivityScenario.launch(AddEditTaskActivity.class);

        Espresso.onView(ViewMatchers.withId(R.id.toolbar)).check(ViewAssertions.matches(withToolbarTitle(R.string.add_task)));

        activityScenario.onActivity(activity -> {
            rotateOrientation(activity);
        });

        Espresso.onView(ViewMatchers.withId(R.id.toolbar)).check(ViewAssertions.matches(withToolbarTitle(R.string.add_task)));
    }

    @Test
    public void toolbarTitle_editTask_persistsRotation() {
        String TASK_ID = "1";

        DefaultTasksRepository.destroyInstance();
        FakeTasksRemoteDataSource.addTasks(
                new Task("title", "description", TASK_ID) {{
                    setCompleted(false);
                }}
        );

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                AddEditTaskActivity.class)
                .putExtra(AddEditTaskFragment.ARGUMENT_EDIT_TASK_ID, TASK_ID);
        ActivityScenario<AddEditTaskActivity> activityScenario = ActivityScenario.launch(intent);

        Espresso.onView(ViewMatchers.withId(R.id.toolbar)).check(ViewAssertions.matches(withToolbarTitle(R.string.edit_task)));

        activityScenario.onActivity(activity -> {
            rotateOrientation(activity);
        });

        Espresso.onView(ViewMatchers.withId(R.id.toolbar)).check(ViewAssertions.matches(withToolbarTitle(R.string.edit_task)));
    }

    private Matcher<View> withToolbarTitle(int resourceId) {
        return new BoundedMatcher<View, Toolbar>(Toolbar.class) {
            @Override
            public void describeTo(Description description) {
                description.appendText("with toolbar title from resource id: ");
                description.appendValue(resourceId);
            }

            @Override
            protected boolean matchesSafely(Toolbar toolbar) {
                String expectedText = "";
                try {
                    expectedText = toolbar.getResources().getString(resourceId);
                } catch (Resources.NotFoundException ignored) {

                }
                String actualText = toolbar.getTitle().toString();
                return expectedText.equals(actualText);
            }
        };
    }
}
