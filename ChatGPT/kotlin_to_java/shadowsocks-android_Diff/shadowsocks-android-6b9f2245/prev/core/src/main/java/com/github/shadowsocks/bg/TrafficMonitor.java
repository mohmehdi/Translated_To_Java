package com.github.shadowsocks.bg;

import android.net.LocalSocket;
import android.os.SystemClock;
import com.github.shadowsocks.aidl.TrafficStats;
import com.github.shadowsocks.net.LocalSocketListener;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TrafficMonitor implements AutoCloseable {
    private LocalSocketListener thread;
    private TrafficStats current;
    private TrafficStats out;
    private long timestampLast;
    private boolean dirty;

    public TrafficMonitor(File statFile) {
        thread = new LocalSocketListener("TrafficMonitor-" + statFile.getName(), statFile) {
            private byte[] buffer = new byte[16];
            private ByteBuffer stat = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);

            @Override
            protected void acceptInternal(LocalSocket socket) throws IOException {
                if (socket.getInputStream().read(buffer) != 16) throw new IOException("Unexpected traffic stat length");
                long tx = stat.getLong(0);
                long rx = stat.getLong(8);
                if (current.getTxTotal() != tx) {
                    current.setTxTotal(tx);
                    dirty = true;
                }
                if (current.getRxTotal() != rx) {
                    current.setRxTotal(rx);
                    dirty = true;
                }
            }
        };
        thread.start();

        current = new TrafficStats();
        out = new TrafficStats();
        timestampLast = 0L;
        dirty = false;
    }

    public Pair<TrafficStats, Boolean> requestUpdate() {
        long now = SystemClock.elapsedRealtime();
        long delta = now - timestampLast;
        timestampLast = now;
        boolean updated = false;
        if (delta != 0L) {
            if (dirty) {
                out = current.copy();
                out.setTxRate((current.getTxTotal() - out.getTxTotal()) * 1000 / delta);
                out.setRxRate((current.getRxTotal() - out.getRxTotal()) * 1000 / delta);
                dirty = false;
                updated = true;
            } else {
                if (out.getTxRate() != 0L) {
                    out.setTxRate(0);
                    updated = true;
                }
                if (out.getRxRate() != 0L) {
                    out.setRxRate(0);
                    updated = true;
                }
            }
        }
        return new Pair<>(out, updated);
    }

    @Override
    public void close() throws Exception {
        thread.close();
    }
}