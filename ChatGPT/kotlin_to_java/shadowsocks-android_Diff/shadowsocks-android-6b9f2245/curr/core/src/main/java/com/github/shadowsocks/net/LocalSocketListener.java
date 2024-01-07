package com.github.shadowsocks.net;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import com.github.shadowsocks.utils.printLog;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.channels.Channel;
import kotlinx.coroutines.channels.sendBlocking;
import kotlinx.coroutines.launch;
import java.io.File;
import java.io.IOException;

public abstract class LocalSocketListener extends Thread {
    private LocalSocket localSocket;
    private LocalServerSocket serverSocket;
    private Channel<Unit> closeChannel;
    protected volatile boolean running;

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
        closeChannel = new Channel<>(1);
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
        closeChannel.sendBlocking(Unit);
    }

    public void shutdown(CoroutineScope scope) {
        running = false;
        try {
            Os.shutdown(localSocket.getFileDescriptor(), OsConstants.SHUT_RDWR);
        } catch (ErrnoException e) {
            if (e.errno != OsConstants.EBADF && e.errno != OsConstants.ENOTCONN) throw new IOException(e);
        }
        scope.launch(() -> closeChannel.receive());
    }
}