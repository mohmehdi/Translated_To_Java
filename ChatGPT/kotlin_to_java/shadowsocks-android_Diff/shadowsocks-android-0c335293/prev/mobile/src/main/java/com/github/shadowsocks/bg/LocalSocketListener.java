package com.github.shadowsocks.bg;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;
import com.github.shadowsocks.App;

import java.io.File;
import java.io.IOException;

public abstract class LocalSocketListener extends Thread {
    protected String tag;

    public LocalSocketListener(String tag) {
        this.tag = tag;
        setUncaughtExceptionHandler(App.app::track);
    }

    protected abstract File getSocketFile();
    private volatile boolean running = true;

    protected abstract void accept(LocalSocket socket);

    @Override
    public final void run() {
        LocalSocket localSocket = new LocalSocket();
        try {
            localSocket.bind(new LocalSocketAddress(getSocketFile().getAbsolutePath(), LocalSocketAddress.Namespace.FILESYSTEM));
            LocalServerSocket serverSocket = new LocalServerSocket(localSocket.getFileDescriptor());
            while (running) {
                try {
                    LocalSocket socket = serverSocket.accept();
                    if (socket != null) {
                        accept(socket);
                        socket.close();
                    }
                } catch (IOException e) {
                    Log.e(tag, "Error when accept socket", e);
                    App.app.track(e);
                }
            }
        } catch (IOException e) {
            Log.e(tag, "unable to bind", e);
            return;
        } finally {
            try {
                localSocket.close();
            } catch (IOException e) {
                Log.e(tag, "Error when closing socket", e);
                App.app.track(e);
            }
        }
    }

    public void stopThread() {
        running = false;
        interrupt();
    }
}