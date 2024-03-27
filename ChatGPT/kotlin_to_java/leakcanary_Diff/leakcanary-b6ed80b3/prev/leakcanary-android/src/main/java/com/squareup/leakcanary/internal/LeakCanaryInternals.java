
package com.squareup.leakcanary.internal;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import com.squareup.leakcanary.CanaryLog;
import com.squareup.leakcanary.DefaultLeakDirectoryProvider;
import com.squareup.leakcanary.LeakDirectoryProvider;
import com.squareup.leakcanary.R;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class LeakCanaryInternals {

  private LeakCanaryInternals() {
    throw new AssertionError();
  }

  public static final String SAMSUNG = "samsung";
  public static final String MOTOROLA = "motorola";
  public static final String LENOVO = "LENOVO";
  public static final String LG = "LGE";
  public static final String NVIDIA = "NVIDIA";
  public static final String MEIZU = "Meizu";
  public static final String HUAWEI = "HUAWEI";
  public static final String VIVO = "vivo";

  private static volatile LeakDirectoryProvider leakDirectoryProvider;

  public static LeakDirectoryProvider getLeakDirectoryProvider(Context context) {
    LeakDirectoryProvider leakDirectoryProvider = LeakCanaryInternals.leakDirectoryProvider;
    if (leakDirectoryProvider == null) {
      leakDirectoryProvider = new DefaultLeakDirectoryProvider(context);
    }
    return leakDirectoryProvider;
  }

  private static final String NOTIFICATION_CHANNEL_ID = "leakcanary";

  public static volatile Boolean isInAnalyzerProcess;

  public static String classSimpleName(String className) {
    int separator = className.lastIndexOf('.');
    return separator == -1 ? className : className.substring(separator + 1);
  }

  public static void setEnabledAsync(Context context, Class<?> componentClass, boolean enabled) {
    Context appContext = context.getApplicationContext();
    AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> setEnabledBlocking(appContext, componentClass, enabled));
  }

  public static void setEnabledBlocking(Context appContext, Class<?> componentClass, boolean enabled) {
    ComponentName component = new ComponentName(appContext, componentClass);
    PackageManager packageManager = appContext.getPackageManager();
    int newState = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    packageManager.setComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP);
  }

  public static boolean isInServiceProcess(Context context, Class<? extends Service> serviceClass) {
    PackageManager packageManager = context.getPackageManager();
    PackageInfo packageInfo;
    try {
      packageInfo = packageManager.getPackageInfo(context.getPackageName(), PackageManager.GET_SERVICES);
    } catch (Exception e) {
      CanaryLog.d(e, "Could not get package info for %s", context.getPackageName());
      return false;
    }

    String mainProcess = packageInfo.applicationInfo.processName;

    ComponentName component = new ComponentName(context, serviceClass);
    ServiceInfo serviceInfo;
    try {
      serviceInfo = packageManager.getServiceInfo(component, PackageManager.GET_DISABLED_COMPONENTS);
    } catch (PackageManager.NameNotFoundException ignored) {
      return false;
    }

    if (serviceInfo.processName == null) {
      CanaryLog.d("Did not expect service %s to have a null process name", serviceClass);
      return false;
    } else if (serviceInfo.processName.equals(mainProcess)) {
      CanaryLog.d("Did not expect service %s to run in main process %s", serviceClass, mainProcess);
      return false;
    }

    int myPid = android.os.Process.myPid();
    ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    ActivityManager.RunningAppProcessInfo myProcess = null;
    List<ActivityManager.RunningAppProcessInfo> runningProcesses;
    try {
      runningProcesses = activityManager.getRunningAppProcesses();
    } catch (SecurityException exception) {
      CanaryLog.d("Could not get running app processes %d", exception);
      return false;
    }

    if (runningProcesses != null) {
      for (ActivityManager.RunningAppProcessInfo process : runningProcesses) {
        if (process.pid == myPid) {
          myProcess = process;
          break;
        }
      }
    }
    if (myProcess == null) {
      CanaryLog.d("Could not find running process for %d", myPid);
      return false;
    }

    return myProcess.processName.equals(serviceInfo.processName);
  }

  public static void showNotification(Context context, CharSequence contentTitle, CharSequence contentText, PendingIntent pendingIntent, int notificationId) {
    Notification.Builder builder = new Notification.Builder(context)
        .setContentText(contentText)
        .setContentTitle(contentTitle)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent);

    Notification notification = buildNotification(context, builder);
    NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    notificationManager.notify(notificationId, notification);
  }

  public static Notification buildNotification(Context context, Notification.Builder builder) {
    builder.setSmallIcon(R.drawable.leak_canary_notification)
        .setWhen(System.currentTimeMillis())
        .setOnlyAlertOnce(true);

    if (VERSION.SDK_INT >= VERSION_CODES.O) {
      NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      NotificationChannel notificationChannel = notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID);
      if (notificationChannel == null) {
        String channelName = context.getString(R.string.leak_canary_notification_channel);
        notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT);
        notificationManager.createNotificationChannel(notificationChannel);
      }
      builder.setChannelId(NOTIFICATION_CHANNEL_ID);
    }

    return VERSION.SDK_INT < VERSION_CODES.JELLY_BEAN ? builder.getNotification() : builder.build();
  }

  public static Executor newSingleThreadExecutor(String threadName) {
    return Executors.newSingleThreadExecutor(new LeakCanarySingleThreadFactory(threadName));
  }
}