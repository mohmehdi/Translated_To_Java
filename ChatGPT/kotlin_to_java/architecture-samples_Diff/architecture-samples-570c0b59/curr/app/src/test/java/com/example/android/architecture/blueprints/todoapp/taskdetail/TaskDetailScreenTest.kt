
package com.example.android.architecture.blueprints.todoapp.taskdetail;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.FakeTasksRemoteDataSource;
import com.example.android.architecture.blueprints.todoapp.data.Task;

import org.hamcrest.core.IsNot;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TaskDetailScreenTest {

    @Before
    public void setup() {
        FakeTasksRemoteDataSource.clearTasks();
    }

    @Test
    public void activeTaskDetails_DisplayedInUi() {

        Task activeTask = new Task("Active Task", "AndroidX Rocks");
        activeTask.setCompleted(false);
        FakeTasksRemoteDataSource.addTasks(activeTask);

        TaskDetailFragmentArgs bundle = TaskDetailFragmentArgs.fromBundle(activeTask.getId());
        FragmentScenario<TaskDetailFragment> scenario = FragmentScenario.launchInContainer(TaskDetailFragment.class, bundle.toBundle(), R.style.Theme_AppCompat);

        scenario.onFragment(fragment -> {
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_title)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_title)).check(ViewAssertions.matches(ViewMatchers.withText("Active Task")));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_description)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_description)).check(ViewAssertions.matches(ViewMatchers.withText("AndroidX Rocks")));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).check(ViewAssertions.matches(IsNot.not(ViewMatchers.isChecked())));
        });
    }

    @Test
    public void completedTaskDetails_DisplayedInUi() {

        Task completedTask = new Task("Completed Task", "AndroidX Rocks");
        completedTask.setCompleted(true);
        FakeTasksRemoteDataSource.addTasks(completedTask);

        TaskDetailFragmentArgs bundle = TaskDetailFragmentArgs.fromBundle(completedTask.getId());
        FragmentScenario<TaskDetailFragment> scenario = FragmentScenario.launchInContainer(TaskDetailFragment.class, bundle.toBundle(), R.style.Theme_AppCompat);

        scenario.onFragment(fragment -> {
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_title)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_title)).check(ViewAssertions.matches(ViewMatchers.withText("Completed Task")));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_description)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_description)).check(ViewAssertions.matches(ViewMatchers.withText("AndroidX Rocks")));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            Espresso.onView(ViewMatchers.withId(R.id.task_detail_complete)).check(ViewAssertions.matches(ViewMatchers.isChecked()));
        });
    }
}
