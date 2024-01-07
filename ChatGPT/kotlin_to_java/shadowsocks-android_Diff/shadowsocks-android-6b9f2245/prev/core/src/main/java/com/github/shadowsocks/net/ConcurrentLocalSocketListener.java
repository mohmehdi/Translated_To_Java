package com.github.shadowsocks.net;

import android.net.LocalSocket;
import com.github.shadowsocks.utils.printLog;
import kotlinx.coroutines.*;
import java.io.File;

public abstract class ConcurrentLocalSocketListener extends LocalSocketListener implements CoroutineScope {
    private SupervisorJob job;
    
    public ConcurrentLocalSocketListener(String name, File socketFile) {
        super(name, socketFile);
        job = new SupervisorJob();
    }
    
    @Override
    public CoroutineContext getCoroutineContext() {
        return Dispatchers.IO.plus(job).plus(new CoroutineExceptionHandler() {
            @Override
            public void handleException(CoroutineContext coroutineContext, Throwable throwable) {
                printLog(throwable);
            }
        });
    }
    
    @Override
    public void accept(LocalSocket socket) {
        launch(Dispatchers.Default, CoroutineStart.DEFAULT, null, new Function2<CoroutineScope, Continuation<? super Unit>, Object>() {
            @Override
            public Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
                super.accept(socket);
                return null;
            }
        });
    }
    
    public void shutdown(CoroutineScope scope) {
        running = false;
        job.cancel();
        close();
        scope.launch(Dispatchers.Default, CoroutineStart.DEFAULT, null, new Function2<CoroutineScope, Continuation<? super Unit>, Object>() {
            @Override
            public Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
                job.join();
                return null;
            }
        });
    }
}