
package com.example.android.architecture.blueprints.todoapp.data.source;

import androidx.lifecycle.LiveData;
import com.example.android.architecture.blueprints.todoapp.data.Result;
import com.example.android.architecture.blueprints.todoapp.data.Result.Success;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.coroutineScope;
import kotlinx.coroutines.launch;
import kotlinx.coroutines.withContext;

public class DefaultTasksRepository implements TasksRepository {

    private TasksDataSource tasksRemoteDataSource;
    private TasksDataSource tasksLocalDataSource;
    private CoroutineDispatcher ioDispatcher;

    public DefaultTasksRepository(TasksDataSource tasksRemoteDataSource, TasksDataSource tasksLocalDataSource) {
        this.tasksRemoteDataSource = tasksRemoteDataSource;
        this.tasksLocalDataSource = tasksLocalDataSource;
        this.ioDispatcher = Dispatchers.IO;
    }

    public DefaultTasksRepository(TasksDataSource tasksRemoteDataSource, TasksDataSource tasksLocalDataSource, CoroutineDispatcher ioDispatcher) {
        this.tasksRemoteDataSource = tasksRemoteDataSource;
        this.tasksLocalDataSource = tasksLocalDataSource;
        this.ioDispatcher = ioDispatcher;
    }

    @Override
    public LiveData<Result<List<Task>>> observeTasks() {
        return tasksLocalDataSource.observeTasks();
    }

    @Override
    public LiveData<Result<Task>> observeTask(String taskId) {
        return tasksLocalDataSource.observeTask(taskId);
    }

    @Override
    public void refreshTasks() {
        updateTasksFromRemoteDataSource();
    }

    @Override
    public void refreshTask(String taskId) {
        updateTaskFromRemoteDataSource(taskId);
    }

    @Override
    public void saveTask(Task task) {
        coroutineScope {
            launch { tasksRemoteDataSource.saveTask(task); }
            launch { tasksLocalDataSource.saveTask(task); }
        }
    }

    @Override
    public void completeTask(Task task) {
        coroutineScope {
            launch { tasksRemoteDataSource.completeTask(task); }
            launch { tasksLocalDataSource.completeTask(task); }
        }
    }

    @Override
    public void completeTask(String taskId) {
        withContext(ioDispatcher, () -> {
            Result<Task> result = getTaskWithId(taskId);
            if (result instanceof Success) {
                completeTask(((Success<Task>) result).getData());
            }
        });
    }

    @Override
    public void activateTask(Task task) {
        withContext(ioDispatcher, () -> {
            coroutineScope {
                launch { tasksRemoteDataSource.activateTask(task); }
                launch { tasksLocalDataSource.activateTask(task); }
            }
        });
    }

    @Override
    public void activateTask(String taskId) {
        withContext(ioDispatcher, () -> {
            Result<Task> result = getTaskWithId(taskId);
            if (result instanceof Success) {
                activateTask(((Success<Task>) result).getData());
            }
        });
    }

    @Override
    public void clearCompletedTasks() {
        coroutineScope {
            launch { tasksRemoteDataSource.clearCompletedTasks(); }
            launch { tasksLocalDataSource.clearCompletedTasks(); }
        }
    }

    @Override
    public void deleteAllTasks() {
        withContext(ioDispatcher, () -> {
            coroutineScope {
                launch { tasksRemoteDataSource.deleteAllTasks(); }
                launch { tasksLocalDataSource.deleteAllTasks(); }
            }
        });
    }

    @Override
    public void deleteTask(String taskId) {
        coroutineScope {
            launch { tasksRemoteDataSource.deleteTask(taskId); }
            launch { tasksLocalDataSource.deleteTask(taskId); }
        }
    }

    @Override
    public Result<List<Task>> getTasks(boolean forceUpdate) {
        if (forceUpdate) {
            try {
                updateTasksFromRemoteDataSource();
            } catch (Exception ex) {
                return new Result.Error(ex);
            }
        }
        return tasksLocalDataSource.getTasks();
    }

    @Override
    public Result<Task> getTask(String taskId, boolean forceUpdate) {
        if (forceUpdate) {
            updateTaskFromRemoteDataSource(taskId);
        }
        return tasksLocalDataSource.getTask(taskId);
    }

    private void updateTasksFromRemoteDataSource() {
        Result<List<Task>> remoteTasks = tasksRemoteDataSource.getTasks();

        if (remoteTasks instanceof Success) {
            tasksLocalDataSource.deleteAllTasks();
            List<Task> data = ((Success<List<Task>>) remoteTasks).getData();
            for (Task task : data) {
                tasksLocalDataSource.saveTask(task);
            }
        } else if (remoteTasks instanceof Result.Error) {
            throw ((Result.Error<List<Task>>) remoteTasks).getException();
        }
    }

    private void updateTaskFromRemoteDataSource(String taskId) {
        Result<Task> remoteTask = tasksRemoteDataSource.getTask(taskId);

        if (remoteTask instanceof Success) {
            tasksLocalDataSource.saveTask(((Success<Task>) remoteTask).getData());
        }
    }

    private Result<Task> getTaskWithId(String id) {
        return tasksLocalDataSource.getTask(id);
    }
}
