

package leakcanary.internal;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.format.Formatter;
import android.util.Log;
import java.io.PrintWriter;
import leakcanary.AnalysisResult;
import leakcanary.AndroidDebuggerControl;
import leakcanary.AndroidHeapDumper;
import leakcanary.BuildConfig;
import leakcanary.DisplayLeakService;
import leakcanary.GcTrigger;
import leakcanary.HeapDump;
import leakcanary.HeapDumpTrigger;
import leakcanary.LeakCanary;
import leakcanary.LeakSentry;
import leakcanary.LeakTrace;
import leakcanary.LeakWatcher;
import leakcanary.LeakWatcherRefWatcher;
import leakcanary.LeakDirectoryProvider;
import leakcanary.OnHeapDumpedListener;
import leakcanary.OnHeapDumpTriggeredListener;
import leakcanary.OnServiceConnectedListener;
import leakcanary.OnServiceDisconnectedListener;
import leakcanary.WatchDurationNanoWatch;

public class InternalLeakCanary {

  private static HeapDumpTrigger heapDumpTrigger;

  public static void onLeakSentryInstalled(Application application) {
    if (isInAnalyzerProcess(application)) {
      return;
    }
    OnHeapDumpedListener heapDumpListener =
      new ServiceHeapDumpListener(application, DisplayLeakService.class);

    AndroidDebuggerControl debuggerControl = new AndroidDebuggerControl();

    LeakDirectoryProvider leakDirectoryProvider =
      LeakCanaryInternals.getLeakDirectoryProvider(application);
    AndroidHeapDumper heapDumper = new AndroidHeapDumper(application, leakDirectoryProvider);

    GcTrigger gcTrigger = GcTrigger.DEFAULT;

    OnHeapDumpTriggeredListener configProvider = () -> LeakCanary.config;

    HandlerThread handlerThread = new HandlerThread(HeapDumpTrigger.LEAK_CANARY_THREAD_NAME);
    handlerThread.start();
    Handler backgroundHandler = new Handler(handlerThread.getLooper());

    heapDumpTrigger = new HeapDumpTrigger(
        application, backgroundHandler, debuggerControl, new LeakSentry.RefWatcher() {},
        leakDirectoryProvider, gcTrigger, heapDumper, heapDumpListener, configProvider
    );
    heapDumpTrigger.registerToVisibilityChanges();
    LeakCanaryInternals.setEnabledAsync(
        application, DisplayLeakActivity.class, true
    );
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
    int versionCode = packageInfo.versionCode;
    StringBuilder info = new StringBuilder("In ").append(packageName).append(":")
        .append(versionName).append(":").append(versionCode).append(".\n");
    StringBuilder detailedString = new StringBuilder();
    if (result.leakFound) {
      if (result.excludedLeak) {
        info.append("* EXCLUDED LEAK.\n");
      }
      info.append("* ").append(result.className).append(" has leaked:\n")
          .append(result.leakTrace.toString()).append("\n");
      if (result.retainedHeapSize != AnalysisResult.RETAINED_HEAP_SKIPPED) {
        info.append("* Retaining: ").append(Formatter.formatShortFileSize(
            context, result.retainedHeapSize
        )).append(".\n");
      }
      if (detailed) {
        detailedString.append("\n* Details:\n").append(result.leakTrace.toDetailedString());
      }
    } else if (result.failure != null) {
      info.append("* FAILURE in ").append(BuildConfig.LIBRARY_VERSION)
          .append(" ").append(BuildConfig.GIT_SHA).append(":")
          .append(Log.getStackTraceString(result.failure)).append("\n");
    } else {
      info.append("* NO LEAK FOUND.\n\n");
    }
    if (detailed) {
      detailedString.append("* Excluded Refs:\n").append(heapDump.excludedRefs);
    }

    info.append("* Reference Key: ").append(result.referenceKey)
        .append("\n* Device: ")
        .append(Build.MANUFACTURER)
        .append(" ")
        .append(Build.BRAND)
        .append(" ")
        .append(Build.MODEL)
        .append(" ")
        .append(Build.PRODUCT)
        .append("\n* Android Version: ")
        .append(Build.VERSION.RELEASE)
        .append(" API: ")
        .append(Build.VERSION.SDK_INT)
        .append(" LeakCanary: ")
        .append(BuildConfig.LIBRARY_VERSION)
        .append(" ")
        .append(BuildConfig.GIT_SHA)
        .append("\n* Durations: watch=")
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
    Boolean isInAnalyzerProcess = LeakCanaryInternals.isInAnalyzerProcess;

    if (isInAnalyzerProcess == null) {
      isInAnalyzerProcess =
        LeakCanaryInternals.isInServiceProcess(
            context, HeapAnalyzerService.class
        );
      LeakCanaryInternals.isInAnalyzerProcess = isInAnalyzerProcess;
    }
    return isInAnalyzerProcess;
  }
}