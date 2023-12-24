
package com.example.android.architecture.blueprints.todoapp.statistics;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.example.android.architecture.blueprints.todoapp.FakeFailingTasksRemoteDataSource;
import com.example.android.architecture.blueprints.todoapp.MainCoroutineRule;
import com.example.android.architecture.blueprints.todoapp.awaitNextValue;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.DefaultTasksRepository;
import com.example.android.architecture.blueprints.todoapp.data.source.FakeRepository;
import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.ExperimentalCoroutinesApi;
import kotlinx.coroutines.test.runBlockingTest;

@ExperimentalCoroutinesApi
public class StatisticsViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    private StatisticsViewModel statisticsViewModel;

    private FakeRepository tasksRepository;

    @Rule
    public MainCoroutineRule mainCoroutineRule = new MainCoroutineRule();

    @Before
    public void setupStatisticsViewModel() {
        tasksRepository = new FakeRepository();
        statisticsViewModel = new StatisticsViewModel(tasksRepository);
    }

    @Test
    public void loadEmptyTasksFromRepository_EmptyResults() throws InterruptedException {
        mainCoroutineRule.runBlockingTest(() -> {
            statisticsViewModel.start();
            Truth.assertThat(statisticsViewModel.getEmpty().awaitNextValue()).isTrue();
        });
    }

    @Test
    public void loadNonEmptyTasksFromRepository_NonEmptyResults() {
        Task task1 = new Task("Title1", "Description1");
        Task task2 = new Task("Title2", "Description2", true);
        Task task3 = new Task("Title3", "Description3", true);
        Task task4 = new Task("Title4", "Description4", true);
        tasksRepository.addTasks(task1, task2, task3, task4);

        statisticsViewModel.start();

        Truth.assertThat(statisticsViewModel.getEmpty().awaitNextValue()).isFalse();
        Truth.assertThat(statisticsViewModel.getActiveTasksPercent().awaitNextValue()).isEqualTo(25f);
        Truth.assertThat(statisticsViewModel.getCompletedTasksPercent().awaitNextValue()).isEqualTo(75f);
    }

    @Test
    public void loadStatisticsWhenTasksAreUnavailable_CallErrorToDisplay() throws InterruptedException {
        mainCoroutineRule.runBlockingTest(() -> {
            StatisticsViewModel errorViewModel = new StatisticsViewModel(
                    new DefaultTasksRepository(
                            FakeFailingTasksRemoteDataSource.INSTANCE,
                            FakeFailingTasksRemoteDataSource.INSTANCE,
                            Dispatchers.Main
                    )
            );

            errorViewModel.start();

            Truth.assertThat(errorViewModel.getEmpty().awaitNextValue()).isTrue();
            Truth.assertThat(errorViewModel.getError().awaitNextValue()).isTrue();
        });
    }

    @Test
    public void loadTasks_loading() throws InterruptedException {
        mainCoroutineRule.pauseDispatcher();

        statisticsViewModel.start();

        Truth.assertThat(statisticsViewModel.getDataLoading().awaitNextValue()).isTrue();

        mainCoroutineRule.resumeDispatcher();

        Truth.assertThat(statisticsViewModel.getDataLoading().awaitNextValue()).isFalse();
    }
}
