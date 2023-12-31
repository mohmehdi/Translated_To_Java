package com.example.android.architecture.blueprints.todoapp.addedittask;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.example.android.architecture.blueprints.todoapp.MainCoroutineRule;
import com.example.android.architecture.blueprints.todoapp.R.string;
import com.example.android.architecture.blueprints.todoapp.assertSnackbarMessage;
import com.example.android.architecture.blueprints.todoapp.awaitNextValue;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.FakeRepository;
import com.google.common.truth.Truth;
import kotlinx.coroutines.ExperimentalCoroutinesApi;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@ExperimentalCoroutinesApi
public class AddEditTaskViewModelTest {

    private AddEditTaskViewModel addEditTaskViewModel;
    private FakeRepository tasksRepository;

    @Rule
    public MainCoroutineRule mainCoroutineRule = new MainCoroutineRule();

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    private Task task = new Task("Title1", "Description1");

    @Before
    public void setupViewModel() {
        tasksRepository = new FakeRepository();
        addEditTaskViewModel = new AddEditTaskViewModel(tasksRepository);
    }

    @Test
    public void saveNewTaskToRepository_showsSuccessMessageUi() {
        String newTitle = "New Task Title";
        String newDescription = "Some Task Description";
        addEditTaskViewModel.getTitle().setValue(newTitle);
        addEditTaskViewModel.getDescription().setValue(newDescription);
        addEditTaskViewModel.saveTask();

        Task newTask = tasksRepository.getTasksServiceData().values().iterator().next();

        Truth.assertThat(newTask.getTitle()).isEqualTo(newTitle);
        Truth.assertThat(newTask.getDescription()).isEqualTo(newDescription);
    }

    @Test
    public void loadTasks_loading() {
        mainCoroutineRule.pauseDispatcher();
        addEditTaskViewModel.start(task.getId());
        Truth.assertThat(addEditTaskViewModel.getDataLoading().awaitNextValue()).isTrue();
        mainCoroutineRule.resumeDispatcher();
        Truth.assertThat(addEditTaskViewModel.getDataLoading().awaitNextValue()).isFalse();
    }

    @Test
    public void loadTasks_taskShown() {
        tasksRepository.addTasks(task);
        addEditTaskViewModel.start(task.getId());
        Truth.assertThat(addEditTaskViewModel.getTitle().awaitNextValue()).isEqualTo(task.getTitle());
        Truth.assertThat(addEditTaskViewModel.getDescription().awaitNextValue()).isEqualTo(task.getDescription());
        Truth.assertThat(addEditTaskViewModel.getDataLoading().awaitNextValue()).isFalse();
    }

    @Test
    public void saveNewTaskToRepository_emptyTitle_error() {
        saveTaskAndAssertSnackbarError("", "Some Task Description");
    }

    @Test
    public void saveNewTaskToRepository_nullTitle_error() {
        saveTaskAndAssertSnackbarError(null, "Some Task Description");
    }

    @Test
    public void saveNewTaskToRepository_emptyDescription_error() {
        saveTaskAndAssertSnackbarError("Title", "");
    }

    @Test
    public void saveNewTaskToRepository_nullDescription_error() {
        saveTaskAndAssertSnackbarError("Title", null);
    }

    @Test
    public void saveNewTaskToRepository_nullDescriptionNullTitle_error() {
        saveTaskAndAssertSnackbarError(null, null);
    }

    @Test
    public void saveNewTaskToRepository_emptyDescriptionEmptyTitle_error() {
        saveTaskAndAssertSnackbarError("", "");
    }

    private void saveTaskAndAssertSnackbarError(String title, String description) {
        addEditTaskViewModel.getTitle().setValue(title);
        addEditTaskViewModel.getDescription().setValue(description);
        addEditTaskViewModel.saveTask();
        assertSnackbarMessage(addEditTaskViewModel.getSnackbarMessage(), string.empty_task_message);
    }
}