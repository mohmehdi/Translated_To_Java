package com.example.android.architecture.blueprints.todoapp.taskdetail;

import android.arch.lifecycle.Event;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Transformations;
import android.arch.lifecycle.ViewModel;
import android.support.annotation.StringRes;
import android.util.Log;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Result;
import com.example.android.architecture.blueprints.todoapp.data.Result.Success;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import com.example.android.architecture.blueprints.todoapp.util.EspressoIdlingResource;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TaskDetailViewModel extends ViewModel {

  private static final String TAG = TaskDetailViewModel.class.getSimpleName();

  private final Executor executor = Executors.newSingleThreadExecutor();
  private final TasksRepository tasksRepository;

  private final MutableLiveData<Task> _task = new MutableLiveData<>();
  public LiveData<Task> task = _task;

  private final MutableLiveData<Boolean> _isDataAvailable = new MutableLiveData<>();
  public LiveData<Boolean> isDataAvailable = _isDataAvailable;

  private final MutableLiveData<Boolean> _dataLoading = new MutableLiveData<>();
  public LiveData<Boolean> dataLoading = _dataLoading;

  private final MutableLiveData<Event<Unit>> _editTaskCommand = new MutableLiveData<>();
  public LiveData<Event<Unit>> editTaskCommand = _editTaskCommand;

  private final MutableLiveData<Event<Unit>> _deleteTaskCommand = new MutableLiveData<>();
  public LiveData<Event<Unit>> deleteTaskCommand = _deleteTaskCommand;

  private final MutableLiveData<Event<Integer>> _snackbarText = new MutableLiveData<>();
  public LiveData<Event<Integer>> snackbarMessage = _snackbarText;

  private String taskId;

  public final LiveData<Boolean> completed = Transformations.map(
    _task,
    input -> {
      if (input == null) {
        return false;
      }
      return input.isCompleted;
    }
  );

  public TaskDetailViewModel(TasksRepository tasksRepository) {
    this.tasksRepository = tasksRepository;
  }

  public void deleteTask() {
    executor.execute(() -> {
      if (taskId != null) {
        tasksRepository.deleteTask(taskId);
        _deleteTaskCommand.postValue(new Event<>(Unit.INSTANCE));
      }
    });
  }

  public void editTask() {
    _editTaskCommand.postValue(new Event<>(Unit.INSTANCE));
  }

  public void setCompleted(boolean completed) {
    executor.execute(() -> {
      Task task = _task.getValue();
      if (task == null) {
        return;
      }
      if (completed) {
        tasksRepository.completeTask(task);
        showSnackbarMessage(R.string.task_marked_complete);
      } else {
        tasksRepository.activateTask(task);
        showSnackbarMessage(R.string.task_marked_active);
      }
    });
  }

  public void start(String taskId, boolean forceRefresh) {
    if (
      _isDataAvailable.getValue() == true &&
      !forceRefresh ||
      _dataLoading.getValue() == true
    ) {
      return;
    }

    _dataLoading.setValue(true);

    EspressoIdlingResource.increment();

    executor.execute(() -> {
      if (taskId != null) {
        Result<Task> result = tasksRepository.getTask(taskId, false);
        if (result instanceof Success) {
          onTaskLoaded(((Success<Task>) result).getData());
        } else {
          onDataNotAvailable(result);
        }
      }
      _dataLoading.postValue(false);
      EspressoIdlingResource.decrement();
    });
  }

  private void setTask(Task task) {
    this._task.postValue(task);
    _isDataAvailable.postValue(task != null);
  }

  private void onTaskLoaded(Task task) {
    setTask(task);
  }

  private void onDataNotAvailable(Result<Task> result) {
    _task.postValue(null);
    _isDataAvailable.postValue(false);
  }

  public void refresh() {
    if (taskId != null) {
      start(taskId, true);
    }
  }

  private void showSnackbarMessage(@StringRes int message) {
    _snackbarText.postValue(new Event<>(message));
  }

  public void setTaskId(String taskId) {
    this.taskId = taskId;
  }

  public String getTaskId() {
    return taskId;
  }
}
