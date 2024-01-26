package com.github.shadowsocks.net;

import com.github.shadowsocks.utils.shutdown;
import java.io.FileDescriptor;

public abstract class SocketListener extends Thread implements AutoCloseable {
    protected abstract FileDescriptor fileDescriptor;
    protected volatile boolean running = true;

    @Override
    public void close() {
        running = false;
        fileDescriptor.shutdown();
        try {
            join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}