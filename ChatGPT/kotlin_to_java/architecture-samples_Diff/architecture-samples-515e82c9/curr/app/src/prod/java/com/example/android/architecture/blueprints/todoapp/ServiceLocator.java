package com.example.android.architecture.blueprints.todoapp;

import android.content.Context;
import androidx.annotation.VisibleForTesting;
import androidx.room.Room;
import com.example.android.architecture.blueprints.todoapp.data.source.DefaultTasksRepository;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksDataSource;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import com.example.android.architecture.blueprints.todoapp.data.source.local.TasksLocalDataSource;
import com.example.android.architecture.blueprints.todoapp.data.source.local.ToDoDatabase;
import com.example.android.architecture.blueprints.todoapp.data.source.remote.TasksRemoteDataSource;
import kotlinx.coroutines.runBlocking;

public class ServiceLocator {

    private static final Object lock = new Object();
    private static ToDoDatabase database = null;
    private static volatile TasksRepository tasksRepository = null;

    @VisibleForTesting
    public static void setTasksRepository(TasksRepository repository) {
        tasksRepository = repository;
    }

    public static TasksRepository provideTasksRepository(Context context) {
        synchronized (lock) {
            if (tasksRepository != null) {
                return tasksRepository;
            } else {
                return createTasksRepository(context);
            }
        }
    }

    private static TasksRepository createTasksRepository(Context context) {
        return new DefaultTasksRepository(TasksRemoteDataSource.INSTANCE, createTaskLocalDataSource(context));
    }

    private static TasksDataSource createTaskLocalDataSource(Context context) {
        ToDoDatabase database = getDatabase(context);
        return new TasksLocalDataSource(database.taskDao());
    }

    private static ToDoDatabase getDatabase(Context context) {
        if (database == null) {
            database = createDataBase(context);
        }
        return database;
    }

    private static ToDoDatabase createDataBase(Context context) {
        ToDoDatabase result = Room.databaseBuilder(
                context.getApplicationContext(),
                ToDoDatabase.class, "Tasks.db"
        ).build();
        database = result;
        return result;
    }

    @VisibleForTesting
    public static void resetRepository() {
        synchronized (lock) {
            runBlocking(() -> {
                TasksRemoteDataSource.INSTANCE.deleteAllTasks();
            });

            if (database != null) {
                database.clearAllTables();
                database.close();
            }
            tasksRepository = null;
        }
    }
}