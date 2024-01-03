package com.github.shadowsocks.bg;

import android.net.LocalSocket;
import android.util.Log;
import com.github.shadowsocks.App;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TrafficMonitorThread extends LocalSocketListener {
    private File socketFile;

    public TrafficMonitorThread() {
        super("TrafficMonitorThread");
        socketFile = new File(App.Companion.getApp().getDeviceContext().getFilesDir(), "stat_path");
    }

    @Override
    public void accept(LocalSocket socket) {
        try {
            byte[] buffer = new byte[16];
            if (socket.getInputStream().read(buffer) != 16) throw new IOException("Unexpected traffic stat length");
            ByteBuffer stat = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
            TrafficMonitor.update(stat.getLong(0), stat.getLong(8));
            socket.getOutputStream().write(0);
        } catch (Exception e) {
            Log.e(getTag(), "Error when recv traffic stat", e);
            App.Companion.getApp().track(e);
        }
    }
}