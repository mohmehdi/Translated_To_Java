package com.github.shadowsocks.net;

import android.net.LocalSocket;
import com.github.shadowsocks.utils.Log; // assuming printLog method is moved to Log class
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.SupervisorJob;
import kotlinx.coroutines.channels.Channel;
import kotlinx.coroutines.channels.SendChannel;
import kotlinx.coroutines.CoroutineExceptionHandler;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.channels.Channel;
import kotlinx.coroutines.channels.SendChannel;
import kotlinx.coroutines.CoroutineExceptionHandler;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.SupervisorJob;
import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public abstract class ConcurrentLocalSocketListener implements LocalSocketListener, CoroutineScope {
    private final Job job = new SupervisorJob();
    private final CoroutineExceptionHandler exceptionHandler = (parentJob, throwable) -> Log.printLog(throwable);
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Channel<LocalSocket> socketChannel = Channel.openConstrained(executor, Int.MAX_VALUE);
    private boolean running = true;

    public ConcurrentLocalSocketListener(String name, File socketFile) {
        super(name, socketFile);
    }

    @Override
    public void accept(LocalSocket socket) {
        executor.execute(() -> {
            try {
                super.accept(socket);
            } catch (IOException e) {
                Log.printLog(e);
            }
        });
    }

        @Override
        public void shutdown(CoroutineScope scope) {
            running = false;
            job.cancel();
            close();
            scope.launch(Dispatchers.IO + exceptionHandler, new Continuation<Unit>() {
                @Override
                public Unit resume(Unit result) {
                    job.join();
                    return null;
                }

                @Override
                public Object resumeWithException(Throwable exception) {
                    Log.printLog(exception);
                    return null;
                }
            }
        }
    }
}