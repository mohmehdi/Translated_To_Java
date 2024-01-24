package com.github.shadowsocks.net;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.File;
import java.io.IOException;

public abstract class LocalSocketListener implements SocketListener {
    private final LocalSocket localSocket;
    private final LocalServerSocket serverSocket;

    public LocalSocketListener(String name, File socketFile) {
        super(name);
        localSocket = new LocalSocket();
        socketFile.delete();
        LocalSocketAddress address = new LocalSocketAddress(socketFile.getAbsolutePath(), LocalSocketAddress.Namespace.FILESYSTEM);
        localSocket.bind(address);
        serverSocket = new LocalServerSocket(localSocket.getFileDescriptor());
    }

    @Override
    public final FileDescriptor getFileDescriptor() {
        return localSocket.getFileDescriptor();
    }

    protected abstract void acceptInternal(LocalSocket socket);

    protected final void accept(LocalSocket socket) throws IOException {
        try (LocalSocket localSocket1 = socket) {
            acceptInternal(localSocket1);
        }
    }

    @Override
    public final void run() {
        try (LocalSocket localSocket1 = localSocket) {
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
    }
}