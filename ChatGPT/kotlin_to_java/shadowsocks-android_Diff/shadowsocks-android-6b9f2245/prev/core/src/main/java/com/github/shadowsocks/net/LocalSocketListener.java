package com.github.shadowsocks.net;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import com.github.shadowsocks.utils.printLog;
import java.io.File;
import java.io.IOException;

public abstract class LocalSocketListener extends SocketListener {
    private LocalSocket localSocket;
    private LocalServerSocket serverSocket;

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
    }

    @Override
    public int getFileDescriptor() {
        return localSocket.getFileDescriptor();
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
}