package com.example.android.architecture.blueprints.todoapp.taskdetail;

import android.arch.lifecycle.Event;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Transformations;
import android.arch.lifecycle.ViewModel;
import android.support.annotation.StringRes;

import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Result;
import com.example.android.architecture.blueprints.todoapp.data.Result.Success;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import com.example.android.architecture.blueprints.todoapp.Event;

import java.util.Pair;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewModelScope;

public class TaskDetailViewModel extends ViewModel {


private MutableLiveData<Pair<String, Boolean>> _params;
private LiveData<Task> _task;
private MutableLiveData<Boolean> _isDataAvailable;
private MutableLiveData<Boolean> _dataLoading;
private MutableLiveData<Event<Unit>> _editTaskCommand;
private MutableLiveData<Event<Unit>> _deleteTaskCommand;
private MutableLiveData<Event<Integer>> _snackbarText;

public LiveData<Task> task;
public LiveData<Boolean> completed;
public LiveData<Boolean> isDataAvailable;
public LiveData<Boolean> dataLoading;
public LiveData<Event<Unit>> editTaskCommand;
public LiveData<Event<Unit>> deleteTaskCommand;
public LiveData<Event<Integer>> snackbarMessage;

private final TasksRepository tasksRepository;

public TaskDetailViewModel(TasksRepository tasksRepository) {
    this.tasksRepository = tasksRepository;
    _params = new MutableLiveData<>();
    _isDataAvailable = new MutableLiveData<>();
    _dataLoading = new MutableLiveData<>();
    _editTaskCommand = new MutableLiveData<>();
    _deleteTaskCommand = new MutableLiveData<>();
    _snackbarText = new MutableLiveData<>();

    task = Transformations.switchMap(_params, input -> {
        if (input.second) {
            _dataLoading.setValue(true);
            viewModelScope.launch(() -> {
                tasksRepository.refreshTasks();
                _dataLoading.setValue(false);
            });
        }
        return tasksRepository.observeTask(input.first).switchMap(this::computeResult);
    });

    completed = Transformations.map(task, input -> {
        if (input != null) {
            return input.isCompleted;
        } else {
            return false;
        }
    });

    isDataAvailable = _isDataAvailable;
    dataLoading = _dataLoading;
    editTaskCommand = _editTaskCommand;
    deleteTaskCommand = _deleteTaskCommand;
    snackbarMessage = _snackbarText;
}

public void deleteTask() {
    if (_params.getValue() != null) {
        String taskId = _params.getValue().first;
        tasksRepository.deleteTask(taskId);
        _deleteTaskCommand.setValue(new Event<>(Unit));
    }
}

public void editTask() {
    _editTaskCommand.setValue(new Event<>(Unit));
}

public void setCompleted(boolean completed) {
    Task task = task.getValue();
    if (task != null) {
        if (completed) {
            tasksRepository.completeTask(task);
            showSnackbarMessage(R.string.task_marked_complete);
        } else {
            tasksRepository.activateTask(task);
            showSnackbarMessage(R.string.task_marked_active);
        }
    }
}

public void start(String taskId) {
    if (_dataLoading.getValue() == true || taskId.equals(_params.getValue().first)) {
        return;
    }

    if (taskId == null) {
        _isDataAvailable.setValue(false);
        return;
    }

    _params.setValue(new Pair<>(taskId, false));
}

private LiveData<Task> computeResult(Result<Task> taskResult) {
    MutableLiveData<Task> result = new MutableLiveData<>();

    if (taskResult instanceof Success) {
        Task data = taskResult.getData();
        result.setValue(data);
        _isDataAvailable.setValue(true);
    } else {
        result.setValue(null);
        showSnackbarMessage(R.string.loading_tasks_error);
        _isDataAvailable.setValue(false);
    }

    return result;
}

public void refresh() {
    _params.setValue(_params.getValue().copy(second = true));
}

private void showSnackbarMessage(@StringRes int message) {
    _snackbarText.setValue(new Event<>(message));
}
}