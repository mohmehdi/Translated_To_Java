
package com.example.android.architecture.blueprints.todoapp.tasks;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewModelScope;

import com.example.android.architecture.blueprints.todoapp.Event;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Result.Success;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksDataSource;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import com.example.android.architecture.blueprints.todoapp.util.wrapEspressoIdlingResource;

import java.util.ArrayList;
import java.util.List;

import kotlinx.coroutines.launch;

public class TasksViewModel extends ViewModel {

    private MutableLiveData<List<Task>> _items = new MutableLiveData<List<Task>>();
    public LiveData<List<Task>> items = _items;

    private MutableLiveData<Boolean> _dataLoading = new MutableLiveData<Boolean>();
    public LiveData<Boolean> dataLoading = _dataLoading;

    private MutableLiveData<Integer> _currentFilteringLabel = new MutableLiveData<Integer>();
    public LiveData<Integer> currentFilteringLabel = _currentFilteringLabel;

    private MutableLiveData<Integer> _noTasksLabel = new MutableLiveData<Integer>();
    public LiveData<Integer> noTasksLabel = _noTasksLabel;

    private MutableLiveData<Integer> _noTaskIconRes = new MutableLiveData<Integer>();
    public LiveData<Integer> noTaskIconRes = _noTaskIconRes;

    private MutableLiveData<Boolean> _tasksAddViewVisible = new MutableLiveData<Boolean>();
    public LiveData<Boolean> tasksAddViewVisible = _tasksAddViewVisible;

    private MutableLiveData<Event<Integer>> _snackbarText = new MutableLiveData<Event<Integer>>();
    public LiveData<Event<Integer>> snackbarMessage = _snackbarText;

    private TasksFilterType _currentFiltering;

    private MutableLiveData<Boolean> isDataLoadingError = new MutableLiveData<Boolean>();

    private MutableLiveData<Event<String>> _openTaskEvent = new MutableLiveData<Event<String>>();
    public LiveData<Event<String>> openTaskEvent = _openTaskEvent;

    private MutableLiveData<Event<Unit>> _newTaskEvent = new MutableLiveData<Event<Unit>>();
    public LiveData<Event<Unit>> newTaskEvent = _newTaskEvent;

    public LiveData<Boolean> empty = Transformations.map(_items, new Function<List<Task>, Boolean>() {
        @Override
        public Boolean apply(List<Task> tasks) {
            return tasks.isEmpty();
        }
    });

    private TasksRepository tasksRepository;

    public TasksViewModel(TasksRepository tasksRepository) {
        this.tasksRepository = tasksRepository;
        setFiltering(TasksFilterType.ALL_TASKS);
        loadTasks(true);
    }

    public void setFiltering(TasksFilterType requestType) {
        _currentFiltering = requestType;

        switch (requestType) {
            case ALL_TASKS:
                setFilter(
                        R.string.label_all, R.string.no_tasks_all,
                        R.drawable.logo_no_fill, true
                );
                break;
            case ACTIVE_TASKS:
                setFilter(
                        R.string.label_active, R.string.no_tasks_active,
                        R.drawable.ic_check_circle_96dp, false
                );
                break;
            case COMPLETED_TASKS:
                setFilter(
                        R.string.label_completed, R.string.no_tasks_completed,
                        R.drawable.ic_verified_user_96dp, false
                );
                break;
        }
    }

    private void setFilter(
            @StringRes int filteringLabelString, @StringRes int noTasksLabelString,
            @DrawableRes int noTaskIconDrawable, boolean tasksAddVisible
    ) {
        _currentFilteringLabel.setValue(filteringLabelString);
        _noTasksLabel.setValue(noTasksLabelString);
        _noTaskIconRes.setValue(noTaskIconDrawable);
        _tasksAddViewVisible.setValue(tasksAddVisible);
    }

    public void clearCompletedTasks() {
        viewModelScope.launch {
            tasksRepository.clearCompletedTasks();
            showSnackbarMessage(R.string.completed_tasks_cleared);
            loadTasks(false);
        }
    }

    public void completeTask(Task task, boolean completed) {
        viewModelScope.launch {
            if (completed) {
                tasksRepository.completeTask(task);
                showSnackbarMessage(R.string.task_marked_complete);
            } else {
                tasksRepository.activateTask(task);
                showSnackbarMessage(R.string.task_marked_active);
            }
            loadTasks(false);
        }
    }

    public void addNewTask() {
        _newTaskEvent.setValue(new Event<>(Unit.INSTANCE));
    }

    public void openTask(String taskId) {
        _openTaskEvent.setValue(new Event<>(taskId));
    }

    public void showEditResultMessage(int result) {
        switch (result) {
            case EDIT_RESULT_OK:
                showSnackbarMessage(R.string.successfully_saved_task_message);
                break;
            case ADD_EDIT_RESULT_OK:
                showSnackbarMessage(R.string.successfully_added_task_message);
                break;
            case DELETE_RESULT_OK:
                showSnackbarMessage(R.string.successfully_deleted_task_message);
                break;
        }
    }

    private void showSnackbarMessage(int message) {
        _snackbarText.setValue(new Event<>(message));
    }

    public void loadTasks(boolean forceUpdate) {
        _dataLoading.setValue(true);

        wrapEspressoIdlingResource(() -> {
            viewModelScope.launch {
                Result<List<Task>> tasksResult = tasksRepository.getTasks(forceUpdate);

                if (tasksResult instanceof Success) {
                    List<Task> tasks = ((Success<List<Task>>) tasksResult).getData();

                    List<Task> tasksToShow = new ArrayList<>();

                    for (Task task : tasks) {
                        switch (_currentFiltering) {
                            case ALL_TASKS:
                                tasksToShow.add(task);
                                break;
                            case ACTIVE_TASKS:
                                if (task.isActive()) {
                                    tasksToShow.add(task);
                                }
                                break;
                            case COMPLETED_TASKS:
                                if (task.isCompleted()) {
                                    tasksToShow.add(task);
                                }
                                break;
                        }
                    }
                    isDataLoadingError.setValue(false);
                    _items.setValue(new ArrayList<>(tasksToShow));
                } else {
                    isDataLoadingError.setValue(false);
                    _items.setValue(new ArrayList<>());
                    showSnackbarMessage(R.string.loading_tasks_error);
                }

                _dataLoading.setValue(false);
            }
        });
    }

    public void refresh() {
        loadTasks(true);
    }
}
