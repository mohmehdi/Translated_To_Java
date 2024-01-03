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
        getSocketFile().delete();
        try (LocalSocket localSocket = new LocalSocket()) {
            LocalServerSocket serverSocket;
            try {
                localSocket.bind(new LocalSocketAddress(getSocketFile().getAbsolutePath(), LocalSocketAddress.Namespace.FILESYSTEM));
                serverSocket = new LocalServerSocket(localSocket.getFileDescriptor());
            } catch (IOException e) {
                Log.e(tag, "unable to bind", e);
                return;
            }
            while (running) {
                try (LocalSocket socket = serverSocket.accept()) {
                    if (socket != null) {
                        accept(socket);
                    }
                } catch (IOException e) {
                    Log.e(tag, "Error when accept socket", e);
                    App.app.track(e);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopThread() {
        running = false;
        interrupt();
    }
}