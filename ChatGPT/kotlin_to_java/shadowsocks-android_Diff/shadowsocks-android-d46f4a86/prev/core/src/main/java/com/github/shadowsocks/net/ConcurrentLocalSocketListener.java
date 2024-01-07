package com.github.shadowsocks.net;

import android.net.LocalSocket;
import com.github.shadowsocks.utils.printLog;
import kotlinx.coroutines.*;
import java.io.File;

public abstract class ConcurrentLocalSocketListener extends LocalSocketListener implements CoroutineScope {
    private final SupervisorJob job = new SupervisorJob();
    @Override
    public CoroutineContext getCoroutineContext() {
        return Dispatchers.IO.plus(job).plus(CoroutineExceptionHandler { _, t -> printLog(t) });
    }

    public ConcurrentLocalSocketListener(String name, File socketFile) {
        super(name, socketFile);
    }

    @Override
    public void accept(LocalSocket socket) {
        launch(Dispatchers.Default) { super.accept(socket); }
    }

    public void shutdown() {
        job.cancel();
        close();
        runBlocking { job.join(); }
    }
}