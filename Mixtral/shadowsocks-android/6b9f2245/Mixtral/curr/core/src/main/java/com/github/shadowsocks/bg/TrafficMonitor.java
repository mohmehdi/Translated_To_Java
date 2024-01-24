package com.github.shadowsocks.bg;

import android.net.LocalSocket;
import android.os.SystemClock;
import com.github.shadowsocks.aidl.TrafficStats;
import com.github.shadowsocks.net.LocalSocketListener;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TrafficMonitor {
    private final LocalSocketListener thread;
    private final TrafficStats current;
    private TrafficStats out;
    private long timestampLast;
    private boolean dirty;

    public TrafficMonitor(File statFile) {
        thread = new LocalSocketListener("TrafficMonitor-" + statFile.getName(), statFile) {
            private final byte[] buffer = new byte[16];
            private final ByteBuffer stat = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

            @Override
            protected void acceptInternal(LocalSocket socket) throws IOException {
                if (socket.getInputStream().read(buffer) != 16) {
                    throw new IOException("Unexpected traffic stat length");
                }
                long tx = stat.getLong(0);
                long rx = stat.getLong(8);
                if (current.txTotal != tx) {
                    current.txTotal = tx;
                    dirty = true;
                }
                if (current.rxTotal != rx) {
                    current.rxTotal = rx;
                    dirty = true;
                }
            }
        };
        thread.start();
        current = new TrafficStats();
        out = new TrafficStats();
        timestampLast = 0;
        dirty = false;
    }

    public Pair<TrafficStats, Boolean> requestUpdate() {
        long now = SystemClock.elapsedRealtime();
        long delta = now - timestampLast;
        timestampLast = now;
        boolean updated = false;
        if (delta != 0) {
            if (dirty) {
                out = new TrafficStats(current);
                out.txRate = (current.txTotal - out.txTotal) * 1000 / delta;
                out.rxRate = (current.rxTotal - out.rxTotal) * 1000 / delta;
                dirty = false;
                updated = true;
            } else {
                if (out.txRate != 0) {
                    out.txRate = 0;
                    updated = true;
                }
                if (out.rxRate != 0) {
                    out.rxRate = 0;
                    updated = true;
                }
            }
        }
        return new Pair<>(out, updated);
    }
}