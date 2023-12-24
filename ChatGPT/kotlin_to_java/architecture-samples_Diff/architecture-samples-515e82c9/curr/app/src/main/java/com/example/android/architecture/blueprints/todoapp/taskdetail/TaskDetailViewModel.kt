
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

import java.util.Objects;

import kotlin.Pair;
import kotlinx.coroutines.launch;

public class TaskDetailViewModel extends ViewModel {

    private MutableLiveData<Pair<String, Boolean>> _params = new MutableLiveData<>();

    private LiveData<Task> _task = Transformations.switchMap(_params, (Pair<String, Boolean> params) -> {
        if (params.getSecond()) {
            _dataLoading.setValue(true);
            viewModelScope.launch(() -> {
                tasksRepository.refreshTasks();
                _dataLoading.setValue(false);
            });
        }
        return tasksRepository.observeTask(params.getFirst()).switchMap(this::computeResult);
    });

    public LiveData<Task> task = _task;

    private MutableLiveData<Boolean> _isDataAvailable = new MutableLiveData<>();
    public LiveData<Boolean> isDataAvailable = _isDataAvailable;

    private MutableLiveData<Boolean> _dataLoading = new MutableLiveData<>();
    public LiveData<Boolean> dataLoading = _dataLoading;

    private MutableLiveData<Event<Unit>> _editTaskCommand = new MutableLiveData<>();
    public LiveData<Event<Unit>> editTaskCommand = _editTaskCommand;

    private MutableLiveData<Event<Unit>> _deleteTaskCommand = new MutableLiveData<>();
    public LiveData<Event<Unit>> deleteTaskCommand = _deleteTaskCommand;

    private MutableLiveData<Event<Integer>> _snackbarText = new MutableLiveData<>();
    public LiveData<Event<Integer>> snackbarMessage = _snackbarText;

    public LiveData<Boolean> completed = Transformations.map(_task, (Task input) -> {
        return input != null ? input.isCompleted() : false;
    });

    public void deleteTask() {
        viewModelScope.launch(() -> {
            String taskId = Objects.requireNonNull(_params.getValue()).getFirst();
            if (taskId != null) {
                tasksRepository.deleteTask(taskId);
                _deleteTaskCommand.setValue(new Event<>(Unit.INSTANCE));
            }
        });
    }

    public void editTask() {
        _editTaskCommand.setValue(new Event<>(Unit.INSTANCE));
    }

    public void setCompleted(boolean completed) {
        viewModelScope.launch(() -> {
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
        });
    }

    public void start(String taskId) {
        if (_dataLoading.getValue() != null && _dataLoading.getValue() && taskId.equals(Objects.requireNonNull(_params.getValue()).getFirst())) {
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
            result.setValue(((Success<Task>) taskResult).getData());
            _isDataAvailable.setValue(true);
        } else {
            result.setValue(null);
            showSnackbarMessage(R.string.loading_tasks_error);
            _isDataAvailable.setValue(false);
        }
        return result;
    }

    public void refresh() {
        _params.setValue(new Pair<>(_params.getValue().getFirst(), true));
    }

    private void showSnackbarMessage(@StringRes int message) {
        _snackbarText.setValue(new Event<>(message));
    }
}
