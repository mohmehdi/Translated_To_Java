
package com.example.android.architecture.blueprints.todoapp.tasks;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;
import com.example.android.architecture.blueprints.todoapp.MainCoroutineRule;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.assertLiveDataEventTriggered;
import com.example.android.architecture.blueprints.todoapp.assertSnackbarMessage;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.FakeRepository;
import com.example.android.architecture.blueprints.todoapp.getOrAwaitValue;
import com.example.android.architecture.blueprints.todoapp.observeForTesting;
import com.google.common.truth.Truth;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import kotlinx.coroutines.ExperimentalCoroutinesApi;
import kotlinx.coroutines.test.runBlockingTest;

@ExperimentalCoroutinesApi
public class TasksViewModelTest {

    private TasksViewModel tasksViewModel;
    private FakeRepository tasksRepository;

    @Rule
    public MainCoroutineRule mainCoroutineRule = new MainCoroutineRule();

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    @Before
    public void setupViewModel() {
        tasksRepository = new FakeRepository();
        Task task1 = new Task("Title1", "Description1");
        Task task2 = new Task("Title2", "Description2", true);
        Task task3 = new Task("Title3", "Description3", true);
        tasksRepository.addTasks(task1, task2, task3);

        tasksViewModel = new TasksViewModel(tasksRepository, new SavedStateHandle());
    }

    @Test
    public void loadAllTasksFromRepository_loadingTogglesAndDataLoaded() {
        mainCoroutineRule.pauseDispatcher();

        tasksViewModel.setFiltering(TasksFilterType.ALL_TASKS);

        tasksViewModel.loadTasks(true);

        tasksViewModel.getItems().observeForTesting(items -> {
            Truth.assertThat(tasksViewModel.getDataLoading().getOrAwaitValue()).isTrue();

            mainCoroutineRule.resumeDispatcher();

            Truth.assertThat(tasksViewModel.getDataLoading().getOrAwaitValue()).isFalse();

            Truth.assertThat(tasksViewModel.getItems().getOrAwaitValue()).hasSize(3);
        });
    }

    @Test
    public void loadActiveTasksFromRepositoryAndLoadIntoView() {
        tasksViewModel.setFiltering(TasksFilterType.ACTIVE_TASKS);

        tasksViewModel.loadTasks(true);

        tasksViewModel.getItems().observeForTesting(items -> {
            Truth.assertThat(tasksViewModel.getDataLoading().getOrAwaitValue()).isFalse();

            Truth.assertThat(tasksViewModel.getItems().getOrAwaitValue()).hasSize(1);
        });
    }

    @Test
    public void loadCompletedTasksFromRepositoryAndLoadIntoView() {
        tasksViewModel.setFiltering(TasksFilterType.COMPLETED_TASKS);

        tasksViewModel.loadTasks(true);

        tasksViewModel.getItems().observeForTesting(items -> {
            Truth.assertThat(tasksViewModel.getDataLoading().getOrAwaitValue()).isFalse();

            Truth.assertThat(tasksViewModel.getItems().getOrAwaitValue()).hasSize(2);
        });
    }

    @Test
    public void loadTasks_error() {
        tasksRepository.setReturnError(true);

        tasksViewModel.loadTasks(true);

        tasksViewModel.getItems().observeForTesting(items -> {
            Truth.assertThat(tasksViewModel.getDataLoading().getOrAwaitValue()).isFalse();

            Truth.assertThat(tasksViewModel.getItems().getOrAwaitValue()).isEmpty();

            assertSnackbarMessage(tasksViewModel.getSnackbarText(), R.string.loading_tasks_error);
        });
    }

    @Test
    public void clickOnFab_showsAddTaskUi() {
        tasksViewModel.addNewTask();

        LiveData<Event<Object>> newTaskEvent = tasksViewModel.getNewTaskEvent();
        Truth.assertThat(newTaskEvent.getOrAwaitValue().getContentIfNotHandled()).isNotNull();
    }

    @Test
    public void clickOnOpenTask_setsEvent() {
        String taskId = "42";
        tasksViewModel.openTask(taskId);

        assertLiveDataEventTriggered(tasksViewModel.getOpenTaskEvent(), taskId);
    }

    @Test
    public void clearCompletedTasks_clearsTasks() {
        mainCoroutineRule.runBlockingTest(() -> {
            tasksViewModel.clearCompletedTasks();

            tasksViewModel.loadTasks(true);

            List<Task> allTasks = tasksViewModel.getItems().getOrAwaitValue();
            List<Task> completedTasks = allTasks.stream().filter(Task::isCompleted).collect(Collectors.toList());

            Truth.assertThat(completedTasks).isEmpty();

            Truth.assertThat(allTasks).hasSize(1);

            assertSnackbarMessage(tasksViewModel.getSnackbarText(), R.string.completed_tasks_cleared);
        });
    }

    @Test
    public void showEditResultMessages_editOk_snackbarUpdated() {
        tasksViewModel.showEditResultMessage(EDIT_RESULT_OK);

        assertSnackbarMessage(tasksViewModel.getSnackbarText(), R.string.successfully_saved_task_message);
    }

    @Test
    public void showEditResultMessages_addOk_snackbarUpdated() {
        tasksViewModel.showEditResultMessage(ADD_EDIT_RESULT_OK);

        assertSnackbarMessage(tasksViewModel.getSnackbarText(), R.string.successfully_added_task_message);
    }

    @Test
    public void showEditResultMessages_deleteOk_snackbarUpdated() {
        tasksViewModel.showEditResultMessage(DELETE_RESULT_OK);

        assertSnackbarMessage(tasksViewModel.getSnackbarText(), R.string.successfully_deleted_task_message);
    }

    @Test
    public void completeTask_dataAndSnackbarUpdated() {
        Task task = new Task("Title", "Description");
        tasksRepository.addTasks(task);

        tasksViewModel.completeTask(task, true);

        Truth.assertThat(tasksRepository.getTasksServiceData().get(task.getId()).isCompleted()).isTrue();

        assertSnackbarMessage(tasksViewModel.getSnackbarText(), R.string.task_marked_complete);
    }

    @Test
    public void activateTask_dataAndSnackbarUpdated() {
        Task task = new Task("Title", "Description", true);
        tasksRepository.addTasks(task);

        tasksViewModel.completeTask(task, false);

        Truth.assertThat(tasksRepository.getTasksServiceData().get(task.getId()).isActive()).isTrue();

        assertSnackbarMessage(tasksViewModel.getSnackbarText(), R.string.task_marked_active);
    }

    @Test
    public void getTasksAddViewVisible() {
        tasksViewModel.setFiltering(TasksFilterType.ALL_TASKS);

        Truth.assertThat(tasksViewModel.getTasksAddViewVisible().getOrAwaitValue()).isTrue();
    }
}
