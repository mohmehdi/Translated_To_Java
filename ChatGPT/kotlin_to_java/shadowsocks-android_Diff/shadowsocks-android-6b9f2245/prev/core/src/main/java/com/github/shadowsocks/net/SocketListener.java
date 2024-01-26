package com.github.shadowsocks.net;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.FileDescriptor;
import java.io.IOException;

public abstract class SocketListener extends Thread implements AutoCloseable {
    protected abstract FileDescriptor getFileDescriptor();
    protected volatile boolean running = true;

    private void shutdown(FileDescriptor fileDescriptor) {
        if (fileDescriptor.valid()) {
            try {
                Os.shutdown(fileDescriptor, OsConstants.SHUT_RDWR);
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
        shutdown(getFileDescriptor());
        try {
            join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}