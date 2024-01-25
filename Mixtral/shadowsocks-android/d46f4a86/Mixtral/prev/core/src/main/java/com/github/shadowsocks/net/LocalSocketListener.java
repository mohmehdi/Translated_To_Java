package com.github.shadowsocks.net;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.File;
import java.io.IOException;
import java.io.FileDescriptor;
import java.lang.Thread;

public abstract class LocalSocketListener extends Thread implements AutoCloseable {
private final LocalSocket localSocket = new LocalSocket();
private final LocalServerSocket serverSocket;
private volatile boolean running;


public LocalSocketListener(String name, File socketFile) {
    super(name);
    localSocket.deleteOnExit();
    LocalSocketAddress address = new LocalSocketAddress(socketFile.getAbsolutePath(), LocalSocketAddress.Namespace.FILESYSTEM);
    try {
        localSocket.bind(address);
    } catch (IOException e) {
        printLog(e);
    }
    try {
        FileDescriptor fd = localSocket.getFileDescriptor();
        serverSocket = new LocalServerSocket(fd);
    } catch (IOException e) {
        printLog(e);
        throw e;
    }
    running = true;
}

protected abstract void acceptInternal(LocalSocket socket) throws IOException;

protected final void accept(LocalSocket socket) {
    try (socket) {
        acceptInternal(socket);
    }
}

@Override
public final void run() {
    while (running) {
        try {
            accept(serverSocket.accept());
        } catch (IOException e) {
            if (running) {
                printLog(e);
            }
            continue;
        }
    }
}

@Override
public void close() throws IOException {
    running = false;

    try {
        Os.shutdown(localSocket.getFileDescriptor(), OsConstants.SHUT_RDWR);
    } catch (ErrnoException e) {
        if (e.errno != OsConstants.EBADF) {
            throw e;
        }
    }
    try {
        join();
    } catch (InterruptedException e) {
        throw new IOException(e);
    }
}
}