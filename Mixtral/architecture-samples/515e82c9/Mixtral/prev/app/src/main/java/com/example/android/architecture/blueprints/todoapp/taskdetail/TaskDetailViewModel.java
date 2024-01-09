package com.example.android.architecture.blueprints.todoapp.taskdetail;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Transformations;
import android.arch.lifecycle.ViewModel;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.event.Event;
import android.support.v4.util.Pair;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Result;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import java.util.concurrent.Executor;

public class TaskDetailViewModel extends ViewModel {

  private static final Executor EXECUTOR = new Executor() {
    @Override
    public void execute(Runnable command) {
      command.run();
    }
  };

  private final TasksRepository tasksRepository;

  private final MutableLiveData<Pair<String, Boolean>> _params = new MutableLiveData<>();

  private final LiveData<Task> _task;
  public LiveData<Task> task;

  private final MutableLiveData<Boolean> _isDataAvailable = new MutableLiveData<>();
  public LiveData<Boolean> isDataAvailable;

  private final MutableLiveData<Boolean> _dataLoading = new MutableLiveData<>();
  public LiveData<Boolean> dataLoading;

  private final MutableLiveData<Event<Unit>> _editTaskCommand = new MutableLiveData<>();
  public LiveData<Event<Unit>> editTaskCommand;

  private final MutableLiveData<Event<Unit>> _deleteTaskCommand = new MutableLiveData<>();
  public LiveData<Event<Unit>> deleteTaskCommand;

  private final MutableLiveData<Event<Integer>> _snackbarText = new MutableLiveData<>();
  public LiveData<Event<Integer>> snackbarMessage;

  public final LiveData<Boolean> completed;

  public TaskDetailViewModel(TasksRepository tasksRepository) {
    this.tasksRepository = tasksRepository;

    _task =
      Transformations.switchMap(
        _params,
        input -> {
          final boolean forceUpdate = input.second;
          if (forceUpdate) {
            EXECUTOR.execute(() -> tasksRepository.refreshTasks());
          }
          return Transformations.switchMap(
            tasksRepository.observeTask(input.first),
            taskResult -> computeResult(taskResult)
          );
        }
      );

    task = _task;

    isDataAvailable = _isDataAvailable;
    dataLoading = _dataLoading;
    editTaskCommand = _editTaskCommand;
    deleteTaskCommand = _deleteTaskCommand;
    snackbarMessage = _snackbarText;
    completed =
      Transformations.map(
        _task,
        input -> {
          if (input == null) {
            return false;
          }
          return input.isCompleted();
        }
      );
  }

  public void deleteTask() {
    final String taskId = _params.getValue().first;
    EXECUTOR.execute(() -> tasksRepository.deleteTask(taskId));
    _deleteTaskCommand.postValue(new Event<>(Unit.INSTANCE));
  }

  public void editTask() {
    _editTaskCommand.postValue(new Event<>(Unit.INSTANCE));
  }

  public void setCompleted(boolean completed) {
    final Task task = _task.getValue();
    if (task == null) {
      return;
    }
    EXECUTOR.execute(() -> {
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
      _isDataAvailable.getValue() && !forceRefresh || _dataLoading.getValue()
    ) {
      return;
    }
    if (taskId == null) {
      _isDataAvailable.postValue(false);
      return;
    }

    _dataLoading.postValue(true);

    _params.postValue(new Pair<>(taskId, forceRefresh));
  }

  private LiveData<Task> computeResult(Result<Task> taskResult) {
    final MutableLiveData<Task> result = new MutableLiveData<>();

    EXECUTOR.execute(() -> {
      if (taskResult instanceof Success) {
        final Task data = taskResult.getData();
        result.postValue(data);
        _isDataAvailable.postValue(true);
      } else {
        result.postValue(null);
        showSnackbarMessage(R.string.loading_tasks_error);
        _isDataAvailable.postValue(false);
      }
      _dataLoading.postValue(false);
    });

    return result;
  }

  public void refresh() {
    _params.postValue(_params.getValue().copy(second, true));
  }

  private void showSnackbarMessage(@StringRes int message) {
    _snackbarText.postValue(new Event<>(message));
  }
}
