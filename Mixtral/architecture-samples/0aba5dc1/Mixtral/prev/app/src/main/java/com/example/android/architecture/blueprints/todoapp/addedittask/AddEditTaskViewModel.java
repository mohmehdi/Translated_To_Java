package com.example.android.architecture.blueprints.todoapp.addedittask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Event;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Result;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import java.util.concurrent.Executor;

public class AddEditTaskViewModel extends ViewModel {

  private TasksRepository tasksRepository;

  public final MutableLiveData title = new MutableLiveData<>();
  public final MutableLiveData description = new MutableLiveData<>();

  private final MutableLiveData _dataLoading = new MutableLiveData<>();
  public final LiveData dataLoading = _dataLoading;

  private final MutableLiveData<Event> _snackbarText = new MutableLiveData<>();
  public final LiveData<Event> snackbarMessage = _snackbarText;

  private final MutableLiveData<Event> _taskUpdated = new MutableLiveData<>();
  public final LiveData<Event> taskUpdatedEvent = _taskUpdated;

  private String taskId;

  private boolean isNewTask;

  private boolean isDataLoaded;

  private boolean taskCompleted;

  public AddEditTaskViewModel(@NonNull TasksRepository tasksRepository) {
    this.tasksRepository = tasksRepository;
  }

  public void start(@Nullable String taskId) {
    if (_dataLoading.getValue() == true) {
      return;
    }

    this.taskId = taskId;
    if (taskId == null) {
      isNewTask = true;
      return;
    }
    if (isDataLoaded) {
      return;
    }

    isNewTask = false;
    _dataLoading.setValue(true);

    Executor executor = android.os.AsyncTask.THREAD_POOL_EXECUTOR;
    executor.execute(() -> {
      androidx.lifecycle.MutableLiveData<Result> result = tasksRepository.getTask(
        taskId
      );
      result.observeForever(taskResult -> {
        if (taskResult != null && taskResult.status == Result.Status.SUCCESS) {
          onTaskLoaded(taskResult.data);
        } else {
          onDataNotAvailable();
        }
        _dataLoading.postValue(false);
      });
    });
  }

  private void onTaskLoaded(Task task) {
    title.postValue(task.getTitle());
    description.postValue(task.getDescription());
    taskCompleted = task.isCompleted();
    isDataLoaded = true;
  }

  private void onDataNotAvailable() {
    _dataLoading.postValue(false);
  }

  public void saveTask() {
    String currentTitle = title.getValue();
    String currentDescription = description.getValue();

    if (currentTitle == null || currentDescription == null) {
      _snackbarText.postValue(new Event<>(R.string.empty_task_message));
      return;
    }
    if (new Task(currentTitle, currentDescription).isEmpty()) {
      _snackbarText.postValue(new Event<>(R.string.empty_task_message));
      return;
    }

    String currentTaskId = taskId;
    if (isNewTask || currentTaskId == null) {
      createTask(new Task(currentTitle, currentDescription));
    } else {
      Task task = new Task(
        currentTitle,
        currentDescription,
        taskCompleted,
        currentTaskId
      );
      updateTask(task);
    }
  }

  private void createTask(Task newTask) {
    Executor executor = android.os.AsyncTask.THREAD_POOL_EXECUTOR;
    executor.execute(() -> {
      tasksRepository.saveTask(newTask);
      _taskUpdated.postValue(new Event<>(null));
    });
  }

  private void updateTask(Task task) {
    if (isNewTask) {
      throw new RuntimeException("updateTask() was called but task is new.");
    }
    Executor executor = android.os.AsyncTask.THREAD_POOL_EXECUTOR;
    executor.execute(() -> {
      tasksRepository.saveTask(task);
      _taskUpdated.postValue(new Event<>(null));
    });
  }
}

public class AddEditTaskViewModelFactory implements ViewModelProvider.Factory {

  private final TasksRepository tasksRepository;

  public AddEditTaskViewModelFactory(TasksRepository tasksRepository) {
    this.tasksRepository = tasksRepository;
  }

  @NonNull
  @Override
  public T create(@NonNull Class modelClass) {
    if (modelClass == AddEditTaskViewModel.class) {
      return (T) new AddEditTaskViewModel(tasksRepository);
    }
    throw new IllegalArgumentException("Unknown ViewModel class");
  }
}
