
package com.example.android.architecture.blueprints.todoapp.statistics;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.ServiceLocator;
import com.example.android.architecture.blueprints.todoapp.data.Task;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import kotlinx.coroutines.runBlocking;

@RunWith(AndroidJUnit4.class)
public class StatisticsScreenTest {

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        TaskRepository tasksRepository = ServiceLocator.provideTasksRepository(context);
        runBlocking {
            tasksRepository.deleteAllTasks();
            tasksRepository.saveTask(new Task("Title1").apply { isCompleted = false });
            tasksRepository.saveTask(new Task("Title2").apply { isCompleted = true });
        }
    }

    @Test
    public void tasks_showsNonEmptyMessage() {
        ActivityScenario<StatisticsActivity> activityScenario = ActivityScenario.launch(StatisticsActivity.class);
        Context context = ApplicationProvider.getApplicationContext();
        String expectedActiveTaskText = context.getString(R.string.statistics_active_tasks, 1);
        String expectedCompletedTaskText = context.getString(R.string.statistics_completed_tasks, 1);
        activityScenario.onActivity(activity -> {
            Espresso.onView(ViewMatchers.withId(R.id.stats_active_text)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            Espresso.onView(ViewMatchers.withId(R.id.stats_active_text)).check(ViewAssertions.matches(ViewMatchers.withText(expectedActiveTaskText)));
            Espresso.onView(ViewMatchers.withId(R.id.stats_completed_text)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
            Espresso.onView(ViewMatchers.withId(R.id.stats_completed_text)).check(ViewAssertions.matches(ViewMatchers.withText(expectedCompletedTaskText)));
        });
    }
}
