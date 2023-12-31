package com.example.android.architecture.blueprints.todoapp.taskdetail;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.example.android.architecture.blueprints.todoapp.LiveDataTestUtil;
import com.example.android.architecture.blueprints.todoapp.MainCoroutineRule;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.assertSnackbarMessage;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.FakeRepository;
import com.google.common.truth.Truth;
import kotlinx.coroutines.ExperimentalCoroutinesApi;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@ExperimentalCoroutinesApi
public class TaskDetailViewModelTest {

    private TaskDetailViewModel taskDetailViewModel;
    private FakeRepository tasksRepository;

    @Rule
    public MainCoroutineRule mainCoroutineRule = new MainCoroutineRule();

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    private Task task = new Task("Title1", "Description1");

    @Before
    public void setupViewModel() {
        tasksRepository = new FakeRepository();
        tasksRepository.addTasks(task);

        taskDetailViewModel = new TaskDetailViewModel(tasksRepository);
    }

    @Test
    public void getActiveTaskFromRepositoryAndLoadIntoView() {
        taskDetailViewModel.start(task.getId());

        Truth.assertThat(LiveDataTestUtil.getValue(taskDetailViewModel.getTask()).getTitle()).isEqualTo(task.getTitle());
        Truth.assertThat(LiveDataTestUtil.getValue(taskDetailViewModel.getTask()).getDescription()).isEqualTo(task.getDescription());
    }

    @Test
    public void completeTask() {
        taskDetailViewModel.start(task.getId());

        Truth.assertThat(tasksRepository.getTasksServiceData().get(task.getId()).isCompleted()).isFalse();

        taskDetailViewModel.setCompleted(true);

        Truth.assertThat(tasksRepository.getTasksServiceData().get(task.getId()).isCompleted()).isTrue();
        assertSnackbarMessage(taskDetailViewModel.getSnackbarMessage(), R.string.task_marked_complete);
    }

    @Test
    public void activateTask() {
        task.setCompleted(true);

        taskDetailViewModel.start(task.getId());

        Truth.assertThat(tasksRepository.getTasksServiceData().get(task.getId()).isCompleted()).isTrue();

        taskDetailViewModel.setCompleted(false);

        Truth.assertThat(tasksRepository.getTasksServiceData().get(task.getId()).isCompleted()).isFalse();
        assertSnackbarMessage(taskDetailViewModel.getSnackbarMessage(), R.string.task_marked_active);
    }

    @Test
    public void taskDetailViewModel_repositoryError() {
        tasksRepository.setReturnError(true);

        taskDetailViewModel.start(task.getId());

        Truth.assertThat(LiveDataTestUtil.getValue(taskDetailViewModel.isDataAvailable())).isFalse();
    }

    @Test
    public void updateSnackbar_nullValue() {
        String snackbarText = taskDetailViewModel.getSnackbarMessage().getValue();

        Truth.assertThat(snackbarText).isNull();
    }

    @Test
    public void clickOnEditTask_SetsEvent() {
        taskDetailViewModel.editTask();

        TaskDetailViewModel.Event value = LiveDataTestUtil.getValue(taskDetailViewModel.getEditTaskCommand());
        Truth.assertThat(value.getContentIfNotHandled()).isNotNull();
    }

    @Test
    public void deleteTask() {
        Truth.assertThat(tasksRepository.getTasksServiceData().containsValue(task)).isTrue();
        taskDetailViewModel.start(task.getId());

        taskDetailViewModel.deleteTask();

        Truth.assertThat(tasksRepository.getTasksServiceData().containsValue(task)).isFalse();
    }

    @Test
    public void loadTask_loading() {
        mainCoroutineRule.pauseDispatcher();

        taskDetailViewModel.start(task.getId());

        Truth.assertThat(LiveDataTestUtil.getValue(taskDetailViewModel.isDataLoading())).isTrue();

        mainCoroutineRule.resumeDispatcher();

        Truth.assertThat(LiveDataTestUtil.getValue(taskDetailViewModel.isDataLoading())).isFalse();
    }
}