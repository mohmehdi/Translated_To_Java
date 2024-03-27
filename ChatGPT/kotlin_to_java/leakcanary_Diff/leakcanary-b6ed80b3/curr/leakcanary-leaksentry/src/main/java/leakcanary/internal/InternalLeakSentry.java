
package leakcanary.internal;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import leakcanary.AbstractLeakSentryReceiver;
import leakcanary.Clock;
import leakcanary.LeakSentry;
import leakcanary.RefWatcher;

import java.util.concurrent.Executor;

public class InternalLeakSentry {

    public static Application application;

    private static final Clock clock = new Clock() {
        @Override
        public long uptimeMillis() {
            return SystemClock.uptimeMillis();
        }
    };

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final Executor checkRetainedExecutor = new Executor() {

    };

    public static final RefWatcher refWatcher = new RefWatcher(
            clock,
            checkRetainedExecutor
    ) {

    };

    public static void install(Application application) {
        checkMainThread();
        if (InternalLeakSentry.application != null) {
            return;
        }
        InternalLeakSentry.application = application;

        LeakSentry.ConfigProvider configProvider = () -> LeakSentry.config;
        ActivityDestroyWatcher.install(
                application, refWatcher, configProvider
        );
        FragmentDestroyWatcher.install(
                application, refWatcher, configProvider
        );
        AbstractLeakSentryReceiver.sendLeakSentryInstalled();
    }

    private static void checkMainThread() {
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
            throw new UnsupportedOperationException(
                    "Should be called from the main thread, not " + Thread.currentThread()
            );
        }
    }
}