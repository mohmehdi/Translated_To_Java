package com.github.shadowsocks.bg;

import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import com.github.shadowsocks.App;
import com.github.shadowsocks.BuildConfig;
import com.github.shadowsocks.JniHelper;
import com.github.shadowsocks.utils.Commandline;
import com.github.shadowsocks.utils.thread;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Semaphore;

public class GuardedProcess {
    private static final String TAG = "GuardedProcess";

    private Thread guardThread;
    private volatile boolean isDestroyed = false;
    private volatile Process process;

    private void streamLogger(InputStream input, Logger logger) {
        thread(() -> {
            try {
                input.bufferedReader().lines().forEach(line -> logger.log(TAG, line));
            } catch (IOException ignored) {
            }
        });
    }

    public GuardedProcess(List<String> cmd) {
        this.cmd = cmd;
    }

    public GuardedProcess start(Runnable onRestartCallback) throws IOException {
        Semaphore semaphore = new Semaphore(1);
        semaphore.acquire();
        IOException ioException = null;
        guardThread = thread(() -> {
            try {
                Runnable callback = null;
                while (!isDestroyed) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "start process: " + Commandline.toString(cmd));
                    long startTime = SystemClock.elapsedRealtime();

                    ProcessBuilder processBuilder = new ProcessBuilder(cmd);
                    processBuilder.redirectErrorStream(true);
                    processBuilder.directory(App.Companion.getApp().getDeviceContext().getFilesDir());
                    process = processBuilder.start();

                    streamLogger(process.getInputStream(), Log::i);
                    streamLogger(process.getErrorStream(), Log::e);

                    if (callback == null) {
                        callback = onRestartCallback;
                    } else {
                        callback.run();
                    }

                    semaphore.release();
                    process.waitFor();

                    synchronized (this) {
                        if (SystemClock.elapsedRealtime() - startTime < 1000) {
                            Log.w(TAG, "process exit too fast, stop guard: " + Commandline.toString(cmd));
                            isDestroyed = true;
                        }
                    }
                }
            } catch (InterruptedException ignored) {
                if (BuildConfig.DEBUG) Log.d(TAG, "thread interrupt, destroy process: " + Commandline.toString(cmd));
                destroyProcess();
            } catch (IOException e) {
                ioException = e;
            } finally {
                semaphore.release();
            }
        });
        semaphore.acquire();
        if (ioException != null) throw ioException;
        return this;
    }

    public void destroy() {
        isDestroyed = true;
        guardThread.interrupt();
        destroyProcess();
        try {
            guardThread.join();
        } catch (InterruptedException ignored) {
        }
    }

    private void destroyProcess() {
        if (Build.VERSION.SDK_INT < 24) {
            @SuppressWarnings("deprecation")
            JniHelper.sigtermCompat(process);
            JniHelper.waitForCompat(process, 500);
        }
        process.destroy();
    }

    private interface Logger {
        void log(String tag, String message);
    }
}