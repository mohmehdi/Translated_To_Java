
package com.example.android.architecture.blueprints.todoapp.taskdetail;

import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewModelScope;

import com.example.android.architecture.blueprints.todoapp.Event;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Result;
import com.example.android.architecture.blueprints.todoapp.data.Result.Success;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import com.example.android.architecture.blueprints.todoapp.util.EspressoIdlingResource;

import kotlinx.coroutines.launch;

public class TaskDetailViewModel extends ViewModel {

    private MutableLiveData<Task> _task = new MutableLiveData<>();
    public LiveData<Task> task = _task;

    private MutableLiveData<Boolean> _isDataAvailable = new MutableLiveData<>();
    public LiveData<Boolean> isDataAvailable = _isDataAvailable;

    private MutableLiveData<Boolean> _dataLoading = new MutableLiveData<>();
    public LiveData<Boolean> dataLoading = _dataLoading;

    private MutableLiveData<Event<Unit>> _editTaskCommand = new MutableLiveData<>();
    public LiveData<Event<Unit>> editTaskCommand = _editTaskCommand;

    private MutableLiveData<Event<Unit>> _deleteTaskCommand = new MutableLiveData<>();
    public LiveData<Event<Unit>> deleteTaskCommand = _deleteTaskCommand;

    private MutableLiveData<Event<Int>> _snackbarText = new MutableLiveData<>();
    public LiveData<Event<Int>> snackbarMessage = _snackbarText;

    private String taskId() {
        return _task.getValue().getId();
    }

    public LiveData<Boolean> completed = Transformations.map(_task, input -> {
        return input != null ? input.isCompleted() : false;
    });

    public void deleteTask() {
        viewModelScope.launch {
            String taskId = taskId();
            if (taskId != null) {
                tasksRepository.deleteTask(taskId);
                _deleteTaskCommand.setValue(new Event<>(Unit.INSTANCE));
            }
        }
    }

    public void editTask() {
        _editTaskCommand.setValue(new Event<>(Unit.INSTANCE));
    }

    public void setCompleted(boolean completed) {
        viewModelScope.launch {
            Task task = _task.getValue();
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
    }

    public void start(String taskId, boolean forceRefresh) {
        if (_isDataAvailable.getValue() == true && !forceRefresh || _dataLoading.getValue() == true) {
            return;
        }

        _dataLoading.setValue(true);

        EspressoIdlingResource.wrapEspressoIdlingResource(() -> {
            viewModelScope.launch {
                if (taskId != null) {
                    Result<Task> result = tasksRepository.getTask(taskId, false);
                    if (result instanceof Success) {
                        onTaskLoaded(((Success<Task>) result).getData());
                    } else {
                        onDataNotAvailable(result);
                    }
                }
                _dataLoading.setValue(false);
            }
        });
    }

    private void setTask(Task task) {
        this._task.setValue(task);
        _isDataAvailable.setValue(task != null);
    }

    private void onTaskLoaded(Task task) {
        setTask(task);
    }

    private void onDataNotAvailable(Result<Task> result) {
        _task.setValue(null);
        _isDataAvailable.setValue(false);
    }

    public void refresh() {
        String taskId = taskId();
        if (taskId != null) {
            start(taskId, true);
        }
    }

    private void showSnackbarMessage(@StringRes int message) {
        _snackbarText.setValue(new Event<>(message));
    }
}
