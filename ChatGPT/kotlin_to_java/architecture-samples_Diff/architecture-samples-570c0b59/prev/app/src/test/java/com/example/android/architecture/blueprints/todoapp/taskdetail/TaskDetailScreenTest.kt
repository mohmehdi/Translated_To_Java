
package com.example.android.architecture.blueprints.todoapp.taskdetail;

import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.ServiceLocator;
import com.example.android.architecture.blueprints.todoapp.data.FakeTasksRemoteDataSource;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.util.rotateOrientation;
import kotlinx.coroutines.runBlocking;
import org.hamcrest.core.IsNot;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class TaskDetailScreenTest {

    private ActivityScenario<TaskDetailActivity> activityScenario;

    @Before
    public void clearTaskRepository() {
        ServiceLocator.provideTasksRepository(ApplicationProvider.getApplicationContext()).apply {
            runBlocking {
                deleteAllTasks();
            }
        }
    }

    @Test
    public void activeTaskDetails_DisplayedInUi() {
        FakeTasksRemoteDataSource.addTasks(ACTIVE_TASK);

        Intent startIntent = new Intent(ApplicationProvider.getApplicationContext(),
            TaskDetailActivity.class).apply {
            putExtra(TaskDetailActivity.EXTRA_TASK_ID, ACTIVE_TASK.getId());
        }

        activityScenario = ActivityScenario.launch(startIntent);

        activityScenario.onActivity(activity -> {
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_title)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_title)).check(ViewAssertions.matches(ViewMatchers.withText(TASK_TITLE)));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_description)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_description)).check(ViewAssertions.matches(ViewMatchers.withText(TASK_DESCRIPTION)));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).check(ViewAssertions.matches(IsNot.not(ViewMatchers.isChecked())));
        });
    }

    @Test
    public void completedTaskDetails_DisplayedInUi() {
        FakeTasksRemoteDataSource.addTasks(COMPLETED_TASK);

        Intent startIntent = new Intent(ApplicationProvider.getApplicationContext(),
            TaskDetailActivity.class).apply {
            putExtra(TaskDetailActivity.EXTRA_TASK_ID, COMPLETED_TASK.getId());
        }

        activityScenario = ActivityScenario.launch(startIntent);

        activityScenario.onActivity(activity -> {
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_title)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_title)).check(ViewAssertions.matches(ViewMatchers.withText(TASK_TITLE)));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_description)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_description)).check(ViewAssertions.matches(ViewMatchers.withText(TASK_DESCRIPTION)));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).check(ViewAssertions.matches(ViewMatchers.isChecked()));
        });
    }

    @Test
    public void orientationChange_menuAndTaskPersist() {
        FakeTasksRemoteDataSource.addTasks(ACTIVE_TASK);

        Intent startIntent = new Intent(ApplicationProvider.getApplicationContext(),
            TaskDetailActivity.class).apply {
            putExtra(TaskDetailActivity.EXTRA_TASK_ID, ACTIVE_TASK.getId());
        }

        activityScenario = ActivityScenario.launch(startIntent);

        activityScenario.onActivity(activity -> {
            Espresso.onView(ViewMatchers.withId(R.id.menu_delete)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            activity.rotateOrientation();
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_title)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_title)).check(ViewAssertions.matches(ViewMatchers.withText(TASK_TITLE)));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_description)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_description)).check(ViewAssertions.matches(ViewMatchers.withText(TASK_DESCRIPTION)));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).check(ViewAssertions.matches(IsNot.not(ViewMatchers.isChecked())));
            Espresso.onView(ViewMatchers.withId(R.id.menu_delete)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
        });
    }

    private static final String TASK_TITLE = "AndroidX Test";
    private static final String TASK_DESCRIPTION = "Rocks";
    private static final Task ACTIVE_TASK = new Task(TASK_TITLE, TASK_DESCRIPTION);
    private static final Task COMPLETED_TASK = new Task(TASK_TITLE, TASK_DESCRIPTION);

    static {
        ACTIVE_TASK.setCompleted(false);
        COMPLETED_TASK.setCompleted(true);
    }
}
