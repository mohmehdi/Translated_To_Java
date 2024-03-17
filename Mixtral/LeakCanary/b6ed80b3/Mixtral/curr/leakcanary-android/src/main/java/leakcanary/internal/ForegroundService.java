

package leakcanary.internal;

import android.app.IntentService;
import android.app.Notification;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import com.squareup.leakcanary.R;

abstract class ForegroundService extends IntentService {
  private final int notificationId;
  private final int notificationContentTitleResId;

  ForegroundService(String name, int notificationContentTitleResId) {
    super(name);
    this.notificationContentTitleResId = notificationContentTitleResId;
    this.notificationId = (int) SystemClock.uptimeMillis();
  }

  @Override
  public void onCreate() {
    super.onCreate();
    showForegroundNotification(
        100, 0, true,
        getString(R.string.leak_canary_notification_foreground_text)
    );
  }

  protected void showForegroundNotification(
      int max,
      int progress,
      boolean indeterminate,
      String contentText
  ) {
    Notification.Builder builder = new Notification.Builder(this)
        .setContentTitle(getString(notificationContentTitleResId))
        .setContentText(contentText)
        .setProgress(max, progress, indeterminate);
    Notification notification = LeakCanaryInternals.buildNotification(this, builder);
    startForeground(notificationId, notification);
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    onHandleIntentInForeground(intent);
  }

  protected abstract void onHandleIntentInForeground(Intent intent);

  @Override
  public void onDestroy() {
    super.onDestroy();
    stopForeground(true);
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}