
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
import com.squareup.leakcanary.ServiceHeapDumpListener;
import leaksentry.LeakSentry;

public class InternalLeakCanary {

  private static HeapDumpTrigger heapDumpTrigger;

  public static void onLeakSentryInstalled(Application application) {
    if (isInAnalyzerProcess(application)) {
      return;
    }
    ServiceHeapDumpListener heapDumpListener = new ServiceHeapDumpListener(application, DisplayLeakService.class);

    AndroidDebuggerControl debuggerControl = new AndroidDebuggerControl();

    LeakCanaryInternals.LeakDirectoryProvider leakDirectoryProvider = LeakCanaryInternals.getLeakDirectoryProvider(application);
    AndroidHeapDumper heapDumper = new AndroidHeapDumper(application, leakDirectoryProvider);

    GcTrigger gcTrigger = GcTrigger.DEFAULT;

    LeakCanaryInternals.ConfigProvider configProvider = () -> LeakCanary.config;

    HandlerThread handlerThread = new HandlerThread(HeapDumpTrigger.LEAK_CANARY_THREAD_NAME);
    handlerThread.start();
    Handler backgroundHandler = new Handler(handlerThread.getLooper());

    heapDumpTrigger = new HeapDumpTrigger(
        application, backgroundHandler, debuggerControl, LeakSentry.refWatcher,
        leakDirectoryProvider, gcTrigger, heapDumper, heapDumpListener, configProvider
    );
    heapDumpTrigger.registerToVisibilityChanges();
    LeakCanaryInternals.setEnabledAsync(application, DisplayLeakActivity.class, true);
  }

  public static void onReferenceRetained() {
    if (heapDumpTrigger != null) {
      heapDumpTrigger.onReferenceRetained();
    }
  }

  public static String leakInfo(
      Context context,
      HeapDump heapDump,
      AnalysisResult result,
      boolean detailed
  ) {
    PackageManager packageManager = context.getPackageManager();
    String packageName = context.getPackageName();
    PackageInfo packageInfo;
    try {
      packageInfo = packageManager.getPackageInfo(packageName, 0);
    } catch (PackageManager.NameNotFoundException e) {
      throw new RuntimeException(e);
    }

    String versionName = packageInfo.versionName;
    @SuppressWarnings("deprecation")
    int versionCode = packageInfo.versionCode;
    String info = "In " + packageName + ":" + versionName + ":" + versionCode + ".\n";
    String detailedString = "";
    if (result.leakFound) {
      if (result.excludedLeak) {
        info += "* EXCLUDED LEAK.\n";
      }
      info += "* " + result.className;
      if (!result.referenceName.isEmpty()) {
        info += " (" + result.referenceName + ")";
      }
      info += " has leaked:\n" + result.leakTrace.toString() + "\n";
      if (result.retainedHeapSize != AnalysisResult.RETAINED_HEAP_SKIPPED) {
        info += "* Retaining: " + Formatter.formatShortFileSize(
            context, result.retainedHeapSize
        ) + ".\n";
      }
      if (detailed) {
        detailedString = "\n* Details:\n" + result.leakTrace.toDetailedString();
      }
    } else if (result.failure != null) {
      info += "* FAILURE in " + BuildConfig.LIBRARY_VERSION + " " + BuildConfig.GIT_SHA + ":" + Log.getStackTraceString(
          result.failure
      ) + "\n";
    } else {
      info += "* NO LEAK FOUND.\n\n";
    }
    if (detailed) {
      detailedString += "* Excluded Refs:\n" + heapDump.excludedRefs;
    }

    info += ("* Reference Key: "
        + result.referenceKey
        + "\n"
        + "* Device: "
        + Build.MANUFACTURER
        + " "
        + Build.BRAND
        + " "
        + Build.MODEL
        + " "
        + Build.PRODUCT
        + "\n"
        + "* Android Version: "
        + Build.VERSION.RELEASE
        + " API: "
        + Build.VERSION.SDK_INT
        + " LeakCanary: "
        + BuildConfig.LIBRARY_VERSION
        + " "
        + BuildConfig.GIT_SHA
        + "\n"
        + "* Durations: watch="
        + result.watchDurationMs
        + "ms, gc="
        + heapDump.gcDurationMs
        + "ms, heap dump="
        + heapDump.heapDumpDurationMs
        + "ms, analysis="
        + result.analysisDurationMs
        + "ms"
        + "\n"
        + detailedString);

    return info;
  }

  public static boolean isInAnalyzerProcess(Context context) {
    Boolean isInAnalyzerProcess = LeakCanaryInternals.isInAnalyzerProcess;

    if (isInAnalyzerProcess == null) {
      isInAnalyzerProcess =
          LeakCanaryInternals.isInServiceProcess(context, HeapAnalyzerService.class);
      LeakCanaryInternals.isInAnalyzerProcess = isInAnalyzerProcess;
    }
    return isInAnalyzerProcess;
  }
}