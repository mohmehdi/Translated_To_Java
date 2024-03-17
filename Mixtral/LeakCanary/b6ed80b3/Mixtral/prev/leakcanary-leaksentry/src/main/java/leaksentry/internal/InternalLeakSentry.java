

package leaksentry.internal;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import leaksentry.AbstractLeakSentryReceiver;
import leaksentry.Clock;
import leaksentry.LeakSentry;
import leaksentry.RefWatcher;
import java.util.concurrent.Executor;

public class InternalLeakSentry {

    private static Application application;

    private static final Clock clock = new Clock() {
        @Override
        public long uptimeMillis() {
            return SystemClock.uptimeMillis();
        }
    };

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final Executor checkRetainedExecutor = command -> mainHandler.postDelayed(command, LeakSentry.config.watchDurationMillis);
    private static final RefWatcher refWatcher = new RefWatcher(clock, checkRetainedExecutor, () -> AbstractLeakSentryReceiver.sendReferenceRetained());

    public static void install(Application application) {
        checkMainThread();
        if (InternalLeakSentry.application != null) {
            return;
        }
        InternalLeakSentry.application = application;

        ActivityDestroyWatcher.install(application, refWatcher, () -> LeakSentry.config);
        FragmentDestroyWatcher.install(application, refWatcher, () -> LeakSentry.config);
        AbstractLeakSentryReceiver.sendLeakSentryInstalled();
    }

    private static void checkMainThread() {
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
            throw new UnsupportedOperationException("Should be called from the main thread, not " + Thread.currentThread());
        }
    }
}