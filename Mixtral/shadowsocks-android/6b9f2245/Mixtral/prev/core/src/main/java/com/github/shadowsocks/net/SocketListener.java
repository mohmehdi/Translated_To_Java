package com.github.shadowsocks.net;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.Thread;

public abstract class SocketListener implements AutoCloseable, Runnable {
    protected abstract FileDescriptor fileDescriptor();
    protected volatile boolean running = true;

    protected void FileDescriptor.shutdown() {
        if (valid()) {
            try {
                Os.shutdown(this, OsConstants.SHUT_RDWR);
            } catch (ErrnoException e) {
                if (e.errno != OsConstants.EBADF && e.errno != OsConstants.ENOTCONN) {
                    throw new IOException(e);
                }
            }
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            fileDescriptor().shutdown();
        } catch (IOException e) {
            // Handle exception
        }
        try {
            join();
        } catch (InterruptedException e) {
            // Handle exception
        }
    }
}