package com.example.android.architecture.blueprints.todoapp.statistics;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.example.android.architecture.blueprints.todoapp.FakeFailingTasksRemoteDataSource;
import com.example.android.architecture.blueprints.todoapp.LiveDataTestUtil;
import com.example.android.architecture.blueprints.todoapp.MainCoroutineRule;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.DefaultTasksRepository;
import com.example.android.architecture.blueprints.todoapp.data.source.FakeRepository;
import com.google.common.truth.Truth;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.ExperimentalCoroutinesApi;
import kotlinx.coroutines.test.runBlockingTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@ExperimentalCoroutinesApi
public class StatisticsViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    private StatisticsViewModel statisticsViewModel;

    private FakeRepository tasksRepository = new FakeRepository();

    @ExperimentalCoroutinesApi
    @Rule
    public MainCoroutineRule mainCoroutineRule = new MainCoroutineRule();

    @Before
    public void setupStatisticsViewModel() {
        statisticsViewModel = new StatisticsViewModel(tasksRepository);
    }

    @Test
    public void loadEmptyTasksFromRepository_EmptyResults() throws InterruptedException {
        mainCoroutineRule.runBlockingTest(() -> {
            statisticsViewModel.start();
            Truth.assertThat(LiveDataTestUtil.getValue(statisticsViewModel.getEmpty())).isTrue();
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

        Truth.assertThat(LiveDataTestUtil.getValue(statisticsViewModel.getEmpty())).isFalse();
        Truth.assertThat(LiveDataTestUtil.getValue(statisticsViewModel.getActiveTasksPercent())).isEqualTo(25f);
        Truth.assertThat(LiveDataTestUtil.getValue(statisticsViewModel.getCompletedTasksPercent())).isEqualTo(75f);
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

            Truth.assertThat(LiveDataTestUtil.getValue(errorViewModel.getEmpty())).isTrue();
            Truth.assertThat(LiveDataTestUtil.getValue(errorViewModel.getError())).isTrue();
        });
    }

    @Test
    public void loadTasks_loading() {
        mainCoroutineRule.pauseDispatcher();

        statisticsViewModel.start();

        Truth.assertThat(LiveDataTestUtil.getValue(statisticsViewModel.getDataLoading())).isTrue();

        mainCoroutineRule.resumeDispatcher();

        Truth.assertThat(LiveDataTestUtil.getValue(statisticsViewModel.getDataLoading())).isFalse();
    }
}