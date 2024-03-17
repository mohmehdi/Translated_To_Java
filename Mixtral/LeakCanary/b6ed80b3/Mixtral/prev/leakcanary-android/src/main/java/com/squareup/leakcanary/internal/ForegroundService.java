

package com.squareup.leakcanary.internal;

import android.app.IntentService;
import android.app.Notification;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;

public abstract class ForegroundService extends IntentService {
    private final int notificationContentTitleResId;
    private final int notificationId;

    public ForegroundService(String name, int notificationContentTitleResId) {
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
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setContentTitle(getString(notificationContentTitleResId))
                .setContentText(contentText)
                .setProgress(max, progress, indeterminate);
        Notification notification = LeakCanaryInternals.buildNotification(this, builder.build());
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