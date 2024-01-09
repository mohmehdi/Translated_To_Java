package com.example.android.architecture.blueprints.todoapp.addedittask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewModelScope;
import com.example.android.architecture.blueprints.todoapp.Event;
import com.example.android.architecture.blueprints.todoapp.R;
import com.example.android.architecture.blueprints.todoapp.data.Result;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.tasks.OnFailureListener;
import kotlinx.coroutines.tasks.OnSuccessListener;

public class AddEditTaskViewModel extends ViewModel {

    private TasksRepository tasksRepository;

    public MutableLiveData<String> title = new MutableLiveData<>();
    public MutableLiveData<String> description = new MutableLiveData<>();

    private MutableLiveData<Boolean> _dataLoading = new MutableLiveData<>();
    public LiveData<Boolean> dataLoading = _dataLoading;

    private MutableLiveData<Event<Integer>> _snackbarText = new MutableLiveData<>();
    public LiveData<Event<Integer>> snackbarText = _snackbarText;

    private MutableLiveData<Event<Void>> _taskUpdatedEvent = new MutableLiveData<>();
    public LiveData<Event<Void>> taskUpdatedEvent = _taskUpdatedEvent;

    private String taskId;

    private boolean isNewTask;

    private boolean isDataLoaded;

    private boolean taskCompleted;

    public AddEditTaskViewModel(TasksRepository tasksRepository) {
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

        viewModelScope.launch(new Job() {
        }, new Runnable() {
            @Override
            public void run() {
                tasksRepository.getTask(taskId).addOnSuccessListener(new OnSuccessListener<Result<Task>>() {
                    @Override
                    public void onSuccess(Result<Task> result) {
                        onTaskLoaded(result.getData());
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        onDataNotAvailable();
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
        if (Task.isEmpty(currentTitle, currentDescription)) {
            _snackbarText.setValue(new Event<>(R.string.empty_task_message));
            return;
        }

        String currentTaskId = taskId;
        if (isNewTask || currentTaskId == null) {
            createTask(new Task(currentTitle, currentDescription));
        } else {
            Task task = new Task(currentTitle, currentDescription, taskCompleted, currentTaskId);
            updateTask(task);
        }
    }

    private void createTask(Task newTask) {
        viewModelScope.launch(new Job() {
        }, new Runnable() {
            @Override
            public void run() {
                tasksRepository.saveTask(newTask);
                _taskUpdatedEvent.setValue(new Event<>(null));
            }
        });
    }

    private void updateTask(Task task) {
        if (isNewTask) {
            throw new RuntimeException("updateTask() was called but task is new.");
        }
        viewModelScope.launch(new Job() {
        }, new Runnable() {
            @Override
            public void run() {
                tasksRepository.saveTask(task);
                _taskUpdatedEvent.setValue(new Event<>(null));
            }
        });
    }
}