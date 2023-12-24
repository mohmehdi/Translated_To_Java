
package com.example.android.architecture.blueprints.todoapp.addedittask;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewModelScope;

import com.example.android.architecture.blueprints.todoapp.Event;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Result.Success;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AddEditTaskViewModel extends ViewModel {

    private TasksRepository tasksRepository;
    private MutableLiveData<String> title = new MutableLiveData<>();
    private MutableLiveData<String> description = new MutableLiveData<>();
    private MutableLiveData<Boolean> _dataLoading = new MutableLiveData<>();
    private LiveData<Boolean> dataLoading = _dataLoading;
    private MutableLiveData<Event<Integer>> _snackbarText = new MutableLiveData<>();
    private LiveData<Event<Integer>> snackbarMessage = _snackbarText;
    private MutableLiveData<Event<Unit>> _taskUpdated = new MutableLiveData<>();
    private LiveData<Event<Unit>> taskUpdatedEvent = _taskUpdated;
    private String taskId;
    private boolean isNewTask;
    private boolean isDataLoaded;
    private boolean taskCompleted;

    public AddEditTaskViewModel(TasksRepository tasksRepository) {
        this.tasksRepository = tasksRepository;
    }

    public MutableLiveData<String> getTitle() {
        return title;
    }

    public MutableLiveData<String> getDescription() {
        return description;
    }

    public LiveData<Boolean> getDataLoading() {
        return dataLoading;
    }

    public LiveData<Event<Integer>> getSnackbarMessage() {
        return snackbarMessage;
    }

    public LiveData<Event<Unit>> getTaskUpdatedEvent() {
        return taskUpdatedEvent;
    }

    public void start(String taskId) {
        Boolean isLoading = _dataLoading.getValue();
        if (isLoading != null && isLoading) {
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

        viewModelScope.launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher(), new Runnable() {
            @Override
            public void run() {
                tasksRepository.getTask(taskId).let(new Function1<Result, Unit>() {
                    @Override
                    public Unit invoke(Result result) {
                        if (result instanceof Success) {
                            onTaskLoaded(((Success) result).getData());
                        } else {
                            onDataNotAvailable();
                        }
                        return null;
                    }
                });
            }
        });
    }

    private void onTaskLoaded(Task task) {
        title.setValue(task.getTitle());
        description.setValue(task.getDescription());
        taskCompleted = task.isCompleted();
        _dataLoading.setValue(false);
        isDataLoaded = true;
    }

    private void onDataNotAvailable() {
        _dataLoading.setValue(false);
    }

    public void saveTask() {
        String currentTitle = title.getValue();
        String currentDescription = description.getValue();

        if (currentTitle == null || currentDescription == null) {
            _snackbarText.setValue(new Event<>(R.string.empty_task_message));
            return;
        }
        if (new Task(currentTitle, currentDescription).isEmpty()) {
            _snackbarText.setValue(new Event<>(R.string.empty_task_message));
            return;
        }

        String currentTaskId = taskId;
        if (isNewTask || currentTaskId == null) {
            createTask(new Task(currentTitle, currentDescription));
        } else {
            Task task = new Task(currentTitle, currentDescription, currentTaskId);
            task.setCompleted(taskCompleted);
            updateTask(task);
        }
    }

    private void createTask(Task newTask) {
        viewModelScope.launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher(), new Runnable() {
            @Override
            public void run() {
                tasksRepository.saveTask(newTask);
                _taskUpdated.setValue(new Event<>(Unit.INSTANCE));
            }
        });
    }

    private void updateTask(Task task) {
        if (isNewTask) {
            throw new RuntimeException("updateTask() was called but task is new.");
        }
        viewModelScope.launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher(), new Runnable() {
            @Override
            public void run() {
                tasksRepository.saveTask(task);
                _taskUpdated.setValue(new Event<>(Unit.INSTANCE));
            }
        });
    }
}
