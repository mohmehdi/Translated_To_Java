
package com.example.android.architecture.blueprints.todoapp.tasks;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.example.android.architecture.blueprints.todoapp.LiveDataTestUtil;
import com.example.android.architecture.blueprints.todoapp.MainCoroutineRule;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.assertLiveDataEventTriggered;
import com.example.android.architecture.blueprints.todoapp.assertSnackbarMessage;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.FakeRepository;
import com.google.common.truth.Truth;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import kotlin.jvm.JvmField;
import kotlinx.coroutines.ExperimentalCoroutinesApi;
import kotlinx.coroutines.test.runBlockingTest;

import static com.example.android.architecture.blueprints.todoapp.tasks.TasksFilterType.ALL_TASKS;
import static com.example.android.architecture.blueprints.todoapp.tasks.TasksFilterType.ACTIVE_TASKS;
import static com.example.android.architecture.blueprints.todoapp.tasks.TasksFilterType.COMPLETED_TASKS;
import static com.example.android.architecture.blueprints.todoapp.tasks.TasksViewModel.EDIT_RESULT_OK;
import static com.example.android.architecture.blueprints.todoapp.tasks.TasksViewModel.ADD_EDIT_RESULT_OK;
import static com.example.android.architecture.blueprints.todoapp.tasks.TasksViewModel.DELETE_RESULT_OK;

@ExperimentalCoroutinesApi
public class TasksViewModelTest {

    private TasksViewModel tasksViewModel;

    private FakeRepository tasksRepository;

    @JvmField
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

        tasksViewModel = new TasksViewModel(tasksRepository);
    }

    @Test
    public void loadAllTasksFromRepository_loadingTogglesAndDataLoaded() {
        mainCoroutineRule.pauseDispatcher();

        tasksViewModel.setFiltering(ALL_TASKS);

        tasksViewModel.loadTasks(true);

        Truth.assertThat(LiveDataTestUtil.getValue(tasksViewModel.getDataLoading())).isTrue();

        mainCoroutineRule.resumeDispatcher();

        Truth.assertThat(LiveDataTestUtil.getValue(tasksViewModel.getDataLoading())).isFalse();

        List<Task> tasks = LiveDataTestUtil.getValue(tasksViewModel.getItems());
        Truth.assertThat(tasks).hasSize(3);
    }

    @Test
    public void loadActiveTasksFromRepositoryAndLoadIntoView() {
        tasksViewModel.setFiltering(ACTIVE_TASKS);

        tasksViewModel.loadTasks(true);

        Truth.assertThat(LiveDataTestUtil.getValue(tasksViewModel.getDataLoading())).isFalse();

        List<Task> tasks = LiveDataTestUtil.getValue(tasksViewModel.getItems());
        Truth.assertThat(tasks).hasSize(1);
    }

    @Test
    public void loadCompletedTasksFromRepositoryAndLoadIntoView() {
        tasksViewModel.setFiltering(COMPLETED_TASKS);

        tasksViewModel.loadTasks(true);

        Truth.assertThat(LiveDataTestUtil.getValue(tasksViewModel.getDataLoading())).isFalse();

        List<Task> tasks = LiveDataTestUtil.getValue(tasksViewModel.getItems());
        Truth.assertThat(tasks).hasSize(2);
    }

    @Test
    public void loadTasks_error() {
        tasksRepository.setReturnError(true);

        tasksViewModel.loadTasks(true);

        Truth.assertThat(LiveDataTestUtil.getValue(tasksViewModel.getDataLoading())).isFalse();

        List<Task> tasks = LiveDataTestUtil.getValue(tasksViewModel.getItems());
        Truth.assertThat(tasks).isEmpty();

        assertSnackbarMessage(tasksViewModel.getSnackbarMessage(), R.string.loading_tasks_error);
    }

    @Test
    public void clickOnFab_showsAddTaskUi() {
        tasksViewModel.addNewTask();

        LiveDataTestUtil.Event<Task> value = LiveDataTestUtil.getValue(tasksViewModel.getNewTaskEvent());
        Truth.assertThat(value.getContentIfNotHandled()).isNotNull();
    }

    @Test
    public void clickOnOpenTask_setsEvent() {
        String taskId = "42";
        tasksViewModel.openTask(taskId);

        assertLiveDataEventTriggered(tasksViewModel.getOpenTaskEvent(), taskId);
    }

    @Test
    public void clearCompletedTasks_clearsTasks() throws InterruptedException {
        tasksViewModel.clearCompletedTasks();

        tasksViewModel.loadTasks(true);

        List<Task> allTasks = LiveDataTestUtil.getValue(tasksViewModel.getItems());
        List<Task> completedTasks = allTasks.stream().filter(Task::isCompleted).collect(Collectors.toList());

        Truth.assertThat(completedTasks).isEmpty();
        Truth.assertThat(allTasks).hasSize(1);

        assertSnackbarMessage(tasksViewModel.getSnackbarMessage(), R.string.completed_tasks_cleared);
    }

    @Test
    public void showEditResultMessages_editOk_snackbarUpdated() {
        tasksViewModel.showEditResultMessage(EDIT_RESULT_OK);

        assertSnackbarMessage(tasksViewModel.getSnackbarMessage(), R.string.successfully_saved_task_message);
    }

    @Test
    public void showEditResultMessages_addOk_snackbarUpdated() {
        tasksViewModel.showEditResultMessage(ADD_EDIT_RESULT_OK);

        assertSnackbarMessage(tasksViewModel.getSnackbarMessage(), R.string.successfully_added_task_message);
    }

    @Test
    public void showEditResultMessages_deleteOk_snackbarUpdated() {
        tasksViewModel.showEditResultMessage(DELETE_RESULT_OK);

        assertSnackbarMessage(tasksViewModel.getSnackbarMessage(), R.string.successfully_deleted_task_message);
    }

    @Test
    public void completeTask_dataAndSnackbarUpdated() {
        Task task = new Task("Title", "Description");
        tasksRepository.addTasks(task);

        tasksViewModel.completeTask(task, true);

        Truth.assertThat(tasksRepository.getTasksServiceData().get(task.getId()).isCompleted()).isTrue();

        assertSnackbarMessage(tasksViewModel.getSnackbarMessage(), R.string.task_marked_complete);
    }

    @Test
    public void activateTask_dataAndSnackbarUpdated() {
        Task task = new Task("Title", "Description", true);
        tasksRepository.addTasks(task);

        tasksViewModel.completeTask(task, false);

        Truth.assertThat(tasksRepository.getTasksServiceData().get(task.getId()).isActive()).isTrue();

        assertSnackbarMessage(tasksViewModel.getSnackbarMessage(), R.string.task_marked_active);
    }

    @Test
    public void getTasksAddViewVisible() {
        tasksViewModel.setFiltering(ALL_TASKS);

        Truth.assertThat(LiveDataTestUtil.getValue(tasksViewModel.getTasksAddViewVisible())).isTrue();
    }
}
