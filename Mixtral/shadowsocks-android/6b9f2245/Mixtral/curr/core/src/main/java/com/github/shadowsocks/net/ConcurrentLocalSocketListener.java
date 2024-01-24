package com.github.shadowsocks.net;

import android.net.LocalSocket;
import com.github.shadowsocks.utils.Log;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.Dispatchers;
import kotlin.coroutines.SupervisorJob;
import kotlin.coroutines.intrinsics.SupervisorJobKt;
import kotlin.coroutines.intrinsics.coroutineContext;
import kotlin.jvm.Throws;
import kotlin.jvm.internal.Intrinsics;
import kotlinx.coroutines.CoroutineExceptionHandler;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.DispatchersKt;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.channels.Channel;
import kotlinx.coroutines.channels.ReceiveChannel;

public abstract class ConcurrentLocalSocketListener implements LocalSocketListener, CoroutineScope {
    private final SupervisorJob job = SupervisorJobKt.supervisorJob();
    private final CoroutineExceptionHandler exceptionHandler = (parentJob, throwable) -> Log.printLog(throwable);
    private final Channel<LocalSocket> socketChannel = Channel.RENDEZVOUS;
    private volatile boolean running = true;

    public ConcurrentLocalSocketListener(String name, File socketFile) {
        super(name, socketFile);
        this.launch(new Runnable() {
            @Override
            public void run() {
                accept(socketChannel.receive());
            }
        });
    }

    @Override
    public CoroutineContext coroutineContext() {
        return DispatchersKt.IO + job + exceptionHandler;
    }

    @Override
    public void accept(LocalSocket socket) {
        this.launch(new Runnable() {
            @Override
            public void run() {
                try {
                    super.accept(socket);
                } catch (Throwable throwable) {
                    Log.printLog(throwable);
                }
            }
        });
    }

    @Throws({InterruptedException.class})
    @Override
    public void shutdown(CoroutineScope scope) {
        this.running = false;
        this.job.cancel();
        super.shutdown(scope);
        scope.launch(new Runnable() {
            @Override
            public void run() {
                try {
                    job.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}