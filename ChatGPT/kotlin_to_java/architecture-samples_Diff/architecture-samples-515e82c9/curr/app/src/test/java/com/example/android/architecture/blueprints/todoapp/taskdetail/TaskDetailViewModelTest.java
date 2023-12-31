package com.example.android.architecture.blueprints.todoapp.taskdetail;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.example.android.architecture.blueprints.todoapp.MainCoroutineRule;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.assertSnackbarMessage;
import com.example.android.architecture.blueprints.todoapp.awaitNextValue;
import com.example.android.architecture.blueprints.todoapp.data.Result.Success;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.FakeRepository;
import com.google.common.truth.Truth;
import kotlinx.coroutines.ExperimentalCoroutinesApi;
import kotlinx.coroutines.test.runBlockingTest;
import org.junit.Assert;
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

        Truth.assertThat(taskDetailViewModel.getTask().awaitNextValue().getTitle()).isEqualTo(task.getTitle());
        Truth.assertThat(taskDetailViewModel.getTask().awaitNextValue().getDescription())
                .isEqualTo(task.getDescription());
    }

    @Test
    public void completeTask() {
        taskDetailViewModel.start(task.getId());
        taskDetailViewModel.getTask().awaitNextValue();

        Truth.assertThat(tasksRepository.getTasksServiceData().get(task.getId()).isCompleted()).isFalse();

        taskDetailViewModel.setCompleted(true);

        Truth.assertThat(tasksRepository.getTasksServiceData().get(task.getId()).isCompleted()).isTrue();
        assertSnackbarMessage(taskDetailViewModel.getSnackbarMessage(), R.string.task_marked_complete);
    }

    @Test
    public void activateTask() throws InterruptedException {
        task.setCompleted(true);

        taskDetailViewModel.start(task.getId());
        taskDetailViewModel.getTask().awaitNextValue();

        taskDetailViewModel.getTask().observeForever(task -> {});

        Truth.assertThat(tasksRepository.getTasksServiceData().get(task.getId()).isCompleted()).isTrue();

        taskDetailViewModel.setCompleted(false);

        Task newTask = ((Success<Task>) tasksRepository.getTask(task.getId())).getData();
        Assert.assertTrue(newTask.isActive());
        assertSnackbarMessage(taskDetailViewModel.getSnackbarMessage(), R.string.task_marked_active);
    }

    @Test
    public void taskDetailViewModel_repositoryError() {
        tasksRepository.setReturnError(true);

        taskDetailViewModel.start(task.getId());
        taskDetailViewModel.getTask().awaitNextValue();

        Truth.assertThat(taskDetailViewModel.isDataAvailable().awaitNextValue()).isFalse();
    }

    @Test
    public void updateSnackbar_nullValue() {
        String snackbarText = taskDetailViewModel.getSnackbarMessage().getValue();

        Truth.assertThat(snackbarText).isNull();
    }

    @Test
    public void clickOnEditTask_SetsEvent() {
        taskDetailViewModel.editTask();

        TaskDetailViewModel.Event value = taskDetailViewModel.getEditTaskCommand().awaitNextValue();
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
        taskDetailViewModel.getTask().observeForever(task -> {});

        taskDetailViewModel.refresh();

        Truth.assertThat(taskDetailViewModel.isDataLoading().awaitNextValue()).isTrue();

        mainCoroutineRule.resumeDispatcher();

        Truth.assertThat(taskDetailViewModel.isDataLoading().awaitNextValue()).isFalse();
    }
}