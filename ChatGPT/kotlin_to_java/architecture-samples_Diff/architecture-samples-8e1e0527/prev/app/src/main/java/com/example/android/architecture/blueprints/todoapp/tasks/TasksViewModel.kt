
package com.example.android.architecture.blueprints.todoapp.tasks;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewModelScope;

import com.example.android.architecture.blueprints.todoapp.Event;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Result;
import com.example.android.architecture.blueprints.todoapp.data.Result.Success;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksDataSource;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;

import java.util.ArrayList;
import java.util.List;

import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.launch;

public class TasksViewModel extends ViewModel {

    private MutableLiveData<Boolean> _forceUpdate = new MutableLiveData<>(false);

    private LiveData<List<Task>> _items = Transformations.switchMap(_forceUpdate, forceUpdate -> {
        if (forceUpdate) {
            _dataLoading.setValue(true);
            viewModelScope.launch((CoroutineContext) () -> {
                tasksRepository.refreshTasks();
                _dataLoading.setValue(false);
            });
        }
        return Transformations.switchMap(tasksRepository.observeTasks(), this::filterTasks);
    });

    public LiveData<List<Task>> items = _items;

    private MutableLiveData<Boolean> _dataLoading = new MutableLiveData<>();
    public LiveData<Boolean> dataLoading = _dataLoading;

    private MutableLiveData<Integer> _currentFilteringLabel = new MutableLiveData<>();
    public LiveData<Integer> currentFilteringLabel = _currentFilteringLabel;

    private MutableLiveData<Integer> _noTasksLabel = new MutableLiveData<>();
    public LiveData<Integer> noTasksLabel = _noTasksLabel;

    private MutableLiveData<Integer> _noTaskIconRes = new MutableLiveData<>();
    public LiveData<Integer> noTaskIconRes = _noTaskIconRes;

    private MutableLiveData<Boolean> _tasksAddViewVisible = new MutableLiveData<>();
    public LiveData<Boolean> tasksAddViewVisible = _tasksAddViewVisible;

    private MutableLiveData<Event<Integer>> _snackbarText = new MutableLiveData<>();
    public LiveData<Event<Integer>> snackbarText = _snackbarText;

    private MutableLiveData<Boolean> isDataLoadingError = new MutableLiveData<>();

    private MutableLiveData<Event<String>> _openTaskEvent = new MutableLiveData<>();
    public LiveData<Event<String>> openTaskEvent = _openTaskEvent;

    private MutableLiveData<Event<Unit>> _newTaskEvent = new MutableLiveData<>();
    public LiveData<Event<Unit>> newTaskEvent = _newTaskEvent;

    private boolean resultMessageShown = false;

    public LiveData<Boolean> empty = Transformations.map(_items, it -> it.isEmpty());

    private TasksRepository tasksRepository;
    private SavedStateHandle savedStateHandle;

    public TasksViewModel(TasksRepository tasksRepository, SavedStateHandle savedStateHandle) {
        this.tasksRepository = tasksRepository;
        this.savedStateHandle = savedStateHandle;
        setFiltering(getSavedFilterType());
        loadTasks(true);
    }

    public void setFiltering(TasksFilterType requestType) {
        savedStateHandle.set(TASKS_FILTER_SAVED_STATE_KEY, requestType);

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

        loadTasks(false);
    }

    private void setFilter(
            @StringRes int filteringLabelString,
            @StringRes int noTasksLabelString,
            @DrawableRes int noTaskIconDrawable,
            boolean tasksAddVisible
    ) {
        _currentFilteringLabel.setValue(filteringLabelString);
        _noTasksLabel.setValue(noTasksLabelString);
        _noTaskIconRes.setValue(noTaskIconDrawable);
        _tasksAddViewVisible.setValue(tasksAddVisible);
    }

    public void clearCompletedTasks() {
        viewModelScope.launch((CoroutineContext) () -> {
            tasksRepository.clearCompletedTasks();
            showSnackbarMessage(R.string.completed_tasks_cleared);
        });
    }

    public void completeTask(Task task, boolean completed) {
        viewModelScope.launch((CoroutineContext) () -> {
            if (completed) {
                tasksRepository.completeTask(task);
                showSnackbarMessage(R.string.task_marked_complete);
            } else {
                tasksRepository.activateTask(task);
                showSnackbarMessage(R.string.task_marked_active);
            }
        });
    }

    public void addNewTask() {
        _newTaskEvent.setValue(new Event<>(Unit.INSTANCE));
    }

    public void openTask(String taskId) {
        _openTaskEvent.setValue(new Event<>(taskId));
    }

    public void showEditResultMessage(int result) {
        if (resultMessageShown) return;
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
        resultMessageShown = true;
    }

    private void showSnackbarMessage(int message) {
        _snackbarText.setValue(new Event<>(message));
    }

    private LiveData<List<Task>> filterTasks(Result<List<Task>> tasksResult) {
        MutableLiveData<List<Task>> result = new MutableLiveData<>();

        if (tasksResult instanceof Success) {
            isDataLoadingError.setValue(false);
            viewModelScope.launch((CoroutineContext) () -> {
                result.setValue(filterItems(((Success<List<Task>>) tasksResult).getData(), getSavedFilterType()));
            });
        } else {
            result.setValue(new ArrayList<>());
            showSnackbarMessage(R.string.loading_tasks_error);
            isDataLoadingError.setValue(true);
        }

        return result;
    }

    public void loadTasks(boolean forceUpdate) {
        _forceUpdate.setValue(forceUpdate);
    }

    private List<Task> filterItems(List<Task> tasks, TasksFilterType filteringType) {
        List<Task> tasksToShow = new ArrayList<>();

        for (Task task : tasks) {
            switch (filteringType) {
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
        return tasksToShow;
    }

    public void refresh() {
        _forceUpdate.setValue(true);
    }

    private TasksFilterType getSavedFilterType() {
        return savedStateHandle.get(TASKS_FILTER_SAVED_STATE_KEY);
    }

}

public static final String TASKS_FILTER_SAVED_STATE_KEY = "TASKS_FILTER_SAVED_STATE_KEY";
