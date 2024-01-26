package com.github.shadowsocks.net;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import com.github.shadowsocks.utils.printLog;
import java.io.File;
import java.io.IOException;

public abstract class LocalSocketListener extends Thread implements AutoCloseable {
    private LocalSocket localSocket;
    private LocalServerSocket serverSocket;
    private volatile boolean running;

    public LocalSocketListener(String name, File socketFile) {
        super(name);
        localSocket = new LocalSocket();
        localSocket.delete();
        try {
            localSocket.bind(new LocalSocketAddress(socketFile.getAbsolutePath(), LocalSocketAddress.Namespace.FILESYSTEM));
        } catch (IOException e) {
            e.printStackTrace();
        }
        serverSocket = new LocalServerSocket(localSocket.getFileDescriptor());
        running = true;
    }

    protected void accept(LocalSocket socket) {
        socket.use(this::acceptInternal);
    }

    protected abstract void acceptInternal(LocalSocket socket);

    @Override
    public void run() {
        localSocket.use(() -> {
            while (running) {
                try {
                    accept(serverSocket.accept());
                } catch (IOException e) {
                    if (running) printLog(e);
                    continue;
                }
            }
        });
    }

    @Override
    public void close() {
        running = false;

        try {
            Os.shutdown(localSocket.getFileDescriptor(), OsConstants.SHUT_RDWR);
        } catch (ErrnoException e) {
            if (e.errno != OsConstants.EBADF) throw e;
        }
        try {
            join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}