package com.example.android.architecture.blueprints.todoapp.data.source;

import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.android.architecture.blueprints.todoapp.data.Result;
import com.example.android.architecture.blueprints.todoapp.data.Task;

import java.util.LinkedHashMap;
import java.util.List;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.coroutines.jvm.internal.ContinuationImpl;
import kotlin.coroutines.jvm.internal.DebugMetadata;
import kotlin.coroutines.jvm.internal.RestrictedSuspendLambda;

public class FakeRepository implements TasksRepository {

    private LinkedHashMap<String, Task> tasksServiceData = new LinkedHashMap<>();

    private boolean shouldReturnError = false;

    private MutableLiveData<Result<List<Task>>> observableTasks = new MutableLiveData<>();

    public void setReturnError(boolean value) {
        shouldReturnError = value;
    }

    @Override
    public void refreshTasks() {
        observableTasks.setValue(getTasks());
    }

    @Override
    public void refreshTask(String taskId) {
        refreshTasks();
    }

    @Override
    public LiveData<Result<List<Task>>> observeTasks() {
        refreshTasks();
        return observableTasks;
    }

    @Override
    public LiveData<Result<Task>> observeTask(String taskId) {
        refreshTasks();
        return Transformations.map(observableTasks, tasks -> {
            if (tasks instanceof Result.Loading) {
                return Result.Loading.INSTANCE;
            } else if (tasks instanceof Result.Error) {
                return new Result.Error(((Result.Error) tasks).getException());
            } else if (tasks instanceof Result.Success) {
                List<Task> data = ((Result.Success<List<Task>>) tasks).getData();
                Task task = null;
                for (Task t : data) {
                    if (t.getId().equals(taskId)) {
                        task = t;
                        break;
                    }
                }
                if (task == null) {
                    return new Result.Error(new Exception("Not found"));
                } else {
                    return new Result.Success<>(task);
                }
            }
            return null;
        });
    }

    @Override
    public Result<Task> getTask(String taskId, boolean forceUpdate) {
        if (shouldReturnError) {
            return new Result.Error(new Exception("Test exception"));
        }
        Task task = tasksServiceData.get(taskId);
        if (task != null) {
            return new Result.Success<>(task);
        } else {
            return new Result.Error(new Exception("Could not find task"));
        }
    }

    @Override
    public Result<List<Task>> getTasks(boolean forceUpdate) {
        if (shouldReturnError) {
            return new Result.Error(new Exception("Test exception"));
        }
        return new Result.Success<>(tasksServiceData.values().toList());
    }

    @Override
    public void saveTask(Task task) {
        tasksServiceData.put(task.getId(), task);
    }

    @Override
    public void completeTask(Task task) {
        Task completedTask = new Task(task.getTitle(), task.getDescription(), true, task.getId());
        tasksServiceData.put(task.getId(), completedTask);
    }

    @Override
    public void completeTask(String taskId) {
        throw new NotImplementedError();
    }

    @Override
    public void activateTask(Task task) {
        Task activeTask = new Task(task.getTitle(), task.getDescription(), false, task.getId());
        tasksServiceData.put(task.getId(), activeTask);
    }

    @Override
    public void activateTask(String taskId) {
        throw new NotImplementedError();
    }

    @Override
    public void clearCompletedTasks() {
        tasksServiceData = tasksServiceData.entrySet().stream()
                .filter(entry -> !entry.getValue().isCompleted())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, LinkedHashMap::new));
    }

    @Override
    public void deleteTask(String taskId) {
        tasksServiceData.remove(taskId);
        refreshTasks();
    }

    @Override
    public void deleteAllTasks() {
        tasksServiceData.clear();
        refreshTasks();
    }

    @VisibleForTesting
    public void addTasks(Task... tasks) {
        for (Task task : tasks) {
            tasksServiceData.put(task.getId(), task);
        }
        refreshTasks();
    }
}