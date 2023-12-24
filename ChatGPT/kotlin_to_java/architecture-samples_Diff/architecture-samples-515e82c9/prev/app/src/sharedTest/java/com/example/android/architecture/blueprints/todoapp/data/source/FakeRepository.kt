
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
import kotlin.coroutines.RestrictsSuspension;
import kotlin.coroutines.SuspendFunction;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlin.coroutines.jvm.internal.ContinuationImpl;
import kotlin.coroutines.jvm.internal.DebugMetadata;
import kotlin.coroutines.jvm.internal.RestrictedSuspendLambda;

public final class FakeRepository implements TasksRepository {
    private LinkedHashMap<String, Task> tasksServiceData = new LinkedHashMap<>();
    private boolean shouldReturnError = false;
    private MutableLiveData<Result<List<Task>>> observableTasks = new MutableLiveData<>();

    public final void setReturnError(boolean value) {
        shouldReturnError = value;
    }

    @Override
    public Object refreshTasks(Continuation<? super Unit> $completion) {
        observableTasks.setValue(getTasks());
        return Unit.INSTANCE;
    }

    @Override
    public Object refreshTask(String taskId, Continuation<? super Unit> $completion) {
        refreshTasks(null);
        return Unit.INSTANCE;
    }

    @Override
    public LiveData<Result<List<Task>>> observeTasks() {
        runBlocking(new SuspendLambda(this) {
            public Object invokeSuspend(Object $result, Continuation $completion) {
                this.this$0.refreshTasks($completion);
                return $result;
            }
        });
        return observableTasks;
    }

    @Override
    public LiveData<Result<Task>> observeTask(String taskId) {
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
            throw new IllegalStateException("Unknown result type: " + tasks);
        });
    }

    @Override
    public Object getTask(String taskId, boolean forceUpdate, Continuation<? super Result<Task>> $completion) {
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
    public Object getTasks(boolean forceUpdate, Continuation<? super Result<List<Task>>> $completion) {
        if (shouldReturnError) {
            return new Result.Error(new Exception("Test exception"));
        }
        return new Result.Success<>(tasksServiceData.values().toList());
    }

    @Override
    public Object saveTask(Task task, Continuation<? super Unit> $completion) {
        tasksServiceData.put(task.getId(), task);
        return Unit.INSTANCE;
    }

    @Override
    public Object completeTask(Task task, Continuation<? super Unit> $completion) {
        Task completedTask = new Task(task.getTitle(), task.getDescription(), true, task.getId());
        tasksServiceData.put(task.getId(), completedTask);
        return Unit.INSTANCE;
    }

    @Override
    public Object completeTask(String taskId, Continuation<? super Unit> $completion) {
        throw new NotImplementedError();
    }

    @Override
    public Object activateTask(Task task, Continuation<? super Unit> $completion) {
        Task activeTask = new Task(task.getTitle(), task.getDescription(), false, task.getId());
        tasksServiceData.put(task.getId(), activeTask);
        return Unit.INSTANCE;
    }

    @Override
    public Object activateTask(String taskId, Continuation<? super Unit> $completion) {
        throw new NotImplementedError();
    }

    @Override
    public Object clearCompletedTasks(Continuation<? super Unit> $completion) {
        tasksServiceData = tasksServiceData.filterValues(task -> !task.isCompleted());
        return Unit.INSTANCE;
    }

    @Override
    public Object deleteTask(String taskId, Continuation<? super Unit> $completion) {
        tasksServiceData.remove(taskId);
        refreshTasks(null);
        return Unit.INSTANCE;
    }

    @Override
    public Object deleteAllTasks(Continuation<? super Unit> $completion) {
        tasksServiceData.clear();
        refreshTasks(null);
        return Unit.INSTANCE;
    }

    @VisibleForTesting
    public final void addTasks(Task... tasks) {
        for (Task task : tasks) {
            tasksServiceData.put(task.getId(), task);
        }
        runBlocking(new SuspendLambda(this) {
            public Object invokeSuspend(Object $result, Continuation $completion) {
                this.this$0.refreshTasks($completion);
                return $result;
            }
        });
    }

    @RestrictsSuspension
    @DebugMetadata(c = "com/example/android/architecture/blueprints/todoapp/data/source/FakeRepository$runBlocking$1", f = "FakeRepository.kt", i = {}, l = {47}, m = "invokeSuspend", n = {}, s = {})
    /* renamed from: com.example.android.architecture.blueprints.todoapp.data.source.FakeRepository$runBlocking$1  reason: invalid class name */
    static final class SuspendLambda extends RestrictedSuspendLambda<Object> implements SuspendFunction<Object>, Continuation<Unit> {
        Object L$0;
        int label;
        private FakeRepository p$;
        final /* synthetic */ FakeRepository this$0;

        /* JADX INFO: super call moved to the top of the method (can break code semantics) */
        SuspendLambda(FakeRepository fakeRepository) {
            super(1);
            this.this$0 = fakeRepository;
        }

        public final Continuation<Unit> create(Object $result, Continuation<?> $completion) {
            IntrinsicsKt.getCOROUTINE_SUSPENDED();
            SuspendLambda suspendLambda = new SuspendLambda(this.this$0);
            suspendLambda.p$ = (FakeRepository) $result;
            return suspendLambda;
        }

        public final Object invokeSuspend(Object $result, Continuation $completion) {
            this.p$ = (FakeRepository) $completion.getContext();
            IntrinsicsKt.getCOROUTINE_SUSPENDED();
            int i = this.label;
            if (i == 0) {
                ResultKt.throwOnFailure($result);
                FakeRepository fakeRepository = this.p$;
                Continuation continuation = this;
                fakeRepository.refreshTasks(continuation);
                return Unit.INSTANCE;
            }
            throw new IllegalStateException("call to 'resume' before 'invoke' with coroutine");
        }
    }
}
