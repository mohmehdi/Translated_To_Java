

package leakcanary.internal;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import java.util.concurrent.Executor;
import leakcanary.AbstractLeakSentryReceiver;
import leakcanary.Clock;
import leakcanary.LeakSentry;
import leakcanary.RefWatcher;

public class InternalLeakSentry {

  @NonNull
  private static volatile Application application;

  @VisibleForTesting
  static Clock clock = new Clock() {
    @Override
    public long uptimeMillis() {
      return SystemClock.uptimeMillis();
    }
  };

  @NonNull
  private static final Handler mainHandler = new Handler(Looper.getMainLooper());

  @NonNull
  private static final Executor checkRetainedExecutor = command ->
      mainHandler.postDelayed(command, LeakSentry.config.watchDurationMillis);

  @NonNull
  public static final RefWatcher refWatcher = new RefWatcher(
      clock,
      checkRetainedExecutor,
      () -> AbstractLeakSentryReceiver.sendReferenceRetained()
  );

  public static void install(@NonNull Application application) {
    checkMainThread();
    if (InternalLeakSentry.application != null) {
      return;
    }
    InternalLeakSentry.application = application;

    ActivityDestroyWatcher.install(
        application, refWatcher, () -> LeakSentry.config
    );
    FragmentDestroyWatcher.install(
        application, refWatcher, () -> LeakSentry.config
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