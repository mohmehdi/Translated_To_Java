

package com.squareup.leakcanary.internal;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.format.Formatter;
import android.util.Log;
import com.squareup.leakcanary.AnalysisResult;
import com.squareup.leakcanary.AndroidDebuggerControl;
import com.squareup.leakcanary.AndroidHeapDumper;
import com.squareup.leakcanary.BuildConfig;
import com.squareup.leakcanary.DisplayLeakService;
import com.squareup.leakcanary.GcTrigger;
import com.squareup.leakcanary.HeapDump;
import com.squareup.leakcanary.HeapDumpTrigger;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.LeakSentry;
import com.squareup.leakcanary.RefWatcher;
import com.squareup.leakcanary.ServiceHeapDumpListener;
import com.squareup.leakcanary.internal.LeakCanaryInternals;
import java.lang.reflect.Field;
import java.util.List;

public class InternalLeakCanary {

  private static HeapDumpTrigger heapDumpTrigger;

  public static void onLeakSentryInstalled(Application application) {
    if (isInAnalyzerProcess(application)) {
      return;
    }
    ServiceHeapDumpListener heapDumpListener =
        new ServiceHeapDumpListener(application, DisplayLeakService.class);

    AndroidDebuggerControl debuggerControl = new AndroidDebuggerControl();

    LeakDirectoryProvider leakDirectoryProvider =
        LeakCanaryInternals.getLeakDirectoryProvider(application);
    AndroidHeapDumper heapDumper = new AndroidHeapDumper(application, leakDirectoryProvider);

    GcTrigger gcTrigger = GcTrigger.DEFAULT;

    ConfigProvider configProvider = () -> LeakCanary.config;

    HandlerThread handlerThread = new HandlerThread(HeapDumpTrigger.LEAK_CANARY_THREAD_NAME);
    handlerThread.start();
    Handler backgroundHandler = new Handler(handlerThread.getLooper());

    heapDumpTrigger =
        new HeapDumpTrigger(
            application,
            backgroundHandler,
            debuggerControl,
            LeakSentry.refWatcher,
            leakDirectoryProvider,
            gcTrigger,
            heapDumper,
            heapDumpListener,
            configProvider);
    heapDumpTrigger.registerToVisibilityChanges();
    LeakCanaryInternals.setEnabledAsync(
        application, DisplayLeakActivity.class, true);
  }

  public static void onReferenceRetained() {
    if (heapDumpTrigger != null) {
      heapDumpTrigger.onReferenceRetained();
    }
  }

  public static String leakInfo(
      Context context, HeapDump heapDump, AnalysisResult result, boolean detailed) {
    PackageManager packageManager = context.getPackageManager();
    String packageName = context.getPackageName();
    PackageInfo packageInfo;
    try {
      packageInfo = packageManager.getPackageInfo(packageName, 0);
    } catch (PackageManager.NameNotFoundException e) {
      throw new RuntimeException(e);
    }

    String versionName = packageInfo.versionName;
    int versionCode = packageInfo.versionCode;
    StringBuilder info = new StringBuilder("In ").append(packageName).append(":").append(versionName)
        .append(":").append(versionCode).append(".\n");
    StringBuilder detailedString = new StringBuilder();
    if (result.leakFound) {
      if (result.excludedLeak) {
        info.append("* EXCLUDED LEAK.\n");
      }
      info.append("* ").append(result.className).append(" has leaked:\n")
          .append(result.leakTrace.toString()).append("\n");
      if (result.retainedHeapSize != AnalysisResult.RETAINED_HEAP_SKIPPED) {
        info.append("* Retaining: ")
            .append(Formatter.formatShortFileSize(context, result.retainedHeapSize)).append(".\n");
      }
      if (detailed) {
        detailedString.append("\n* Details:\n").append(result.leakTrace.toDetailedString());
      }
    } else if (result.failure != null) {
      info.append("* FAILURE in ")
          .append(BuildConfig.LIBRARY_VERSION)
          .append(" ")
          .append(BuildConfig.GIT_SHA)
          .append(":")
          .append(Log.getStackTraceString(result.failure))
          .append("\n");
    } else {
      info.append("* NO LEAK FOUND.\n\n");
    }
    if (detailed) {
      detailedString.append("* Excluded Refs:\n").append(heapDump.excludedRefs);
    }

    info.append("* Reference Key: ")
        .append(result.referenceKey)
        .append("\n")
        .append("* Device: ")
        .append(Build.MANUFACTURER)
        .append(" ")
        .append(Build.BRAND)
        .append(" ")
        .append(Build.MODEL)
        .append(" ")
        .append(Build.PRODUCT)
        .append("\n")
        .append("* Android Version: ")
        .append(Build.VERSION.RELEASE)
        .append(" API: ")
        .append(Build.VERSION.SDK_INT)
        .append(" LeakCanary: ")
        .append(BuildConfig.LIBRARY_VERSION)
        .append(" ")
        .append(BuildConfig.GIT_SHA)
        .append("\n")
        .append("* Durations: watch=")
        .append(result.watchDurationMs)
        .append("ms, gc=")
        .append(heapDump.gcDurationMs)
        .append("ms, heap dump=")
        .append(heapDump.heapDumpDurationMs)
        .append("ms, analysis=")
        .append(result.analysisDurationMs)
        .append("ms")
        .append("\n")
        .append(detailedString);

    return info.toString();
  }

  public static boolean isInAnalyzerProcess(Context context) {
    Boolean isInAnalyzerProcess = null;
    try {
      Field isInAnalyzerProcessField = LeakCanaryInternals.class.getDeclaredField("sIsInAnalyzerProcess");
      isInAnalyzerProcessField.setAccessible(true);
      isInAnalyzerProcess = (Boolean) isInAnalyzerProcessField.get(null);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      // Ignore
    }

    if (isInAnalyzerProcess == null) {
      isInAnalyzerProcess =
          LeakCanaryInternals.isInServiceProcess(context, HeapAnalyzerService.class);
      try {
        Field isInAnalyzerProcessField =
            LeakCanaryInternals.class.getDeclaredField("sIsInAnalyzerProcess");
        isInAnalyzerProcessField.setAccessible(true);
        isInAnalyzerProcessField.set(null, isInAnalyzerProcess);
      } catch (NoSuchFieldException | IllegalAccessException e) {
        // Ignore
      }
    }
    return isInAnalyzerProcess;
  }
}