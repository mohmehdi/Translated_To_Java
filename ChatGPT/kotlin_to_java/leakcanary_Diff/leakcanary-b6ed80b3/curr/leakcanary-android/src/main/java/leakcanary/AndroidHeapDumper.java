
package leakcanary;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.Toast;
import com.squareup.leakcanary.R;
import com.squareup.leakcanary.R.layout;
import com.squareup.leakcanary.R.string;
import leakcanary.internal.FutureResult;
import leakcanary.internal.LeakCanaryInternals;
import leakcanary.internal.ActivityLifecycleCallbacksAdapter;
import java.io.File;
import java.util.concurrent.TimeUnit;

public class AndroidHeapDumper implements HeapDumper {

  private Context context;
  private Handler mainHandler;
  private Activity resumedActivity;

  public AndroidHeapDumper(Context context, LeakDirectoryProvider leakDirectoryProvider) {
    this.context = context.getApplicationContext();
    this.mainHandler = new Handler(Looper.getMainLooper());
    this.resumedActivity = null;

    Application application = (Application) context.getApplicationContext();
    application.registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacksAdapter() {
      @Override
      public void onActivityResumed(Activity activity) {
        resumedActivity = activity;
      }

      @Override
      public void onActivityPaused(Activity activity) {
        if (resumedActivity == activity) {
          resumedActivity = null;
        }
      }
    });
  }

  @Override
  public File dumpHeap() {
    File heapDumpFile = leakDirectoryProvider.newHeapDumpFile();

    if (heapDumpFile == HeapDumper.RETRY_LATER) {
      return HeapDumper.RETRY_LATER;
    }

    FutureResult<Toast> waitingForToast = new FutureResult<>();
    showToast(waitingForToast);

    if (!waitingForToast.wait(5, TimeUnit.SECONDS)) {
      CanaryLog.d("Did not dump heap, too much time waiting for Toast.");
      return HeapDumper.RETRY_LATER;
    }

    Notification.Builder builder = new Notification.Builder(context)
        .setContentTitle(context.getString(string.leak_canary_notification_dumping));
    Notification notification = LeakCanaryInternals.buildNotification(context, builder);
    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    int notificationId = (int) SystemClock.uptimeMillis();
    notificationManager.notify(notificationId, notification);

    Toast toast = waitingForToast.get();

    try {
      Debug.dumpHprofData(heapDumpFile.getAbsolutePath());
      cancelToast(toast);
      notificationManager.cancel(notificationId);
      return heapDumpFile;
    } catch (Exception e) {
      CanaryLog.d(e, "Could not dump heap");
      return HeapDumper.RETRY_LATER;
    }
  }

  private void showToast(FutureResult<Toast> waitingForToast) {
    mainHandler.post(new Runnable() {
      @Override
      public void run() {
        if (resumedActivity == null) {
          waitingForToast.set(null);
          return;
        }
        Toast toast = new Toast(resumedActivity);
        toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
        toast.setDuration(Toast.LENGTH_LONG);
        LayoutInflater inflater = LayoutInflater.from(resumedActivity);
        toast.setView(inflater.inflate(layout.leak_canary_heap_dump_toast, null));
        toast.show();


        });
      }
    });
  }

  private void cancelToast(Toast toast) {
    if (toast == null) {
      return;
    }
    mainHandler.post(new Runnable() {

    });
  }
}