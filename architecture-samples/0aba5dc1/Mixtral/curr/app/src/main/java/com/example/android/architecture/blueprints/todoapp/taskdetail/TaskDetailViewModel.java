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
import com.example.android.architecture.blueprints.todoapp.data.SourceOfTruth;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.util.EspressoIdlingResource;
import java.util.concurrent.Executor;
import javax.inject.Inject;

public class TaskDetailViewModel extends ViewModel {

  private static final String TAG = "TaskDetailViewModel";

  private final MutableLiveData _task = new MutableLiveData<>();
  public LiveData task = _task;

  private final MutableLiveData _isDataAvailable = new MutableLiveData<>();
  public LiveData isDataAvailable = _isDataAvailable;

  private final MutableLiveData _dataLoading = new MutableLiveData<>();
  public LiveData dataLoading = _dataLoading;

  private final MutableLiveData<Event> _editTaskEvent = new MutableLiveData<>();
  public LiveData<Event> editTaskEvent = _editTaskEvent;

  private final MutableLiveData<Event> _deleteTaskEvent = new MutableLiveData<>();
  public LiveData<Event> deleteTaskEvent = _deleteTaskEvent;

  private final MutableLiveData<Event> _snackbarText = new MutableLiveData<>();
  public LiveData<Event> snackbarText = _snackbarText;

  private final SourceOfTruth tasksRepository;
  private final Executor executor;

  private String taskId;

  @Inject
  public TaskDetailViewModel(SourceOfTruth tasksRepository, Executor executor) {
    this.tasksRepository = tasksRepository;
    this.executor = executor;
  }

  public LiveData completed = Transformations.map(
    _task,
    input -> {
      if (input == null) {
        return false;
      }
      return input.isCompleted;
    }
  );

  public void deleteTask() {
    executor.execute(() -> {
      if (taskId != null) {
        tasksRepository.deleteTask(taskId);
        _deleteTaskEvent.postValue(new Event<>(Unit.INSTANCE));
      }
    });
  }

  public void editTask() {
    _editTaskEvent.postValue(new Event<>(Unit.INSTANCE));
  }

  public void setCompleted(boolean completed) {
    executor.execute(() -> {
      Task task = _task.getValue();
      if (task == null) {
        Log.d(TAG, "Task is null, cannot update completed status");
        return;
      }

      if (completed) {
        tasksRepository.completeTask(task);
        _snackbarText.postValue(new Event<>(R.string.task_marked_complete));
      } else {
        tasksRepository.activateTask(task);
        _snackbarText.postValue(new Event<>(R.string.task_marked_active));
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
        tasksRepository.getTask(
          taskId,
          false,
          new TasksRepository.GetTaskCallback() {
            @Override
            public void onTaskLoaded(Task task) {
              setTask(task);
              EspressoIdlingResource.decrement();
            }

            @Override
            public void onDataNotAvailable(Result result) {
              setTask(null);
              EspressoIdlingResource.decrement();
            }
          }
        );
      }
      _dataLoading.postValue(false);
    });
  }

  private void setTask(Task task) {
    this._task.postValue(task);
    _isDataAvailable.postValue(task != null);
  }

  public void refresh() {
    if (taskId != null) {
      start(taskId, true);
    }
  }

  public void showSnackbarMessage(@StringRes int message) {
    _snackbarText.postValue(new Event<>(message));
  }

  public void setTaskId(String taskId) {
    this.taskId = taskId;
  }

  public String getTaskId() {
    return taskId;
  }
}
