

package leakcanary.internal;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import androidx.core.app.NotificationCompat;
import com.squareup.leakcanary.core.R;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.SharkLog;
import com.squareup.leakcanary.SharkLog.LogLevel;
import com.squareup.leakcanary.analyzer.AnalysisResult;
import com.squareup.leakcanary.analyzer.HeapAnalyzer;
import com.squareup.leakcanary.analyzer.HeapAnalyzer.HeapAnalysis;
import com.squareup.leakcanary.analyzer.HeapAnalyzer.OnAnalysisProgressListener;
import com.squareup.leakcanary.analyzer.ReferenceMatcher;
import com.squareup.leakcanary.analyzer.RetainedHeapSizeComputer;
import com.squareup.leakcanary.analyzer.TimeoutConfiguration;
import com.squareup.leakcanary.excludes.Excludes;
import com.squareup.leakcanary.excludes.Excludes.MatchType;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class HeapAnalyzerService extends ForegroundService {

  public static final String HEAPDUMP_FILE_EXTRA = "HEAPDUMP_FILE_EXTRA";

  public HeapAnalyzerService() {
    super(HeapAnalyzerService.class.getName(),
        R.string.leak_canary_notification_analysing,
        R.id.leak_canary_notification_analyzing_heap);
  }

  @Override
  protected void onHandleIntentInForeground(Intent intent) {
    if (intent == null) {
      SharkLog.d("HeapAnalyzerService received a null intent, ignoring.");
      return;
    }

    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
    File heapDumpFile = (File) intent.getSerializableExtra(HEAPDUMP_FILE_EXTRA);

    if (!heapDumpFile.exists()) {
      throw new IllegalStateException(
          "Hprof file missing due to: [" + LeakDirectoryProvider.hprofDeleteReason(heapDumpFile) + "] " + heapDumpFile);
    }

    HeapAnalyzer heapAnalyzer = new HeapAnalyzer(this);
    com.squareup.leakcanary.LeakCanary.Config config = LeakCanary.config;

    List<ReferenceMatcher> referenceMatchers = config.referenceMatchers;
    RetainedHeapSizeComputer retainedHeapSizeComputer = config.retainedHeapSizeComputer;
    List<com.squareup.leakcanary.analyzer.ObjectInspector> objectInspectors = config.objectInspectors;
    List<com.squareup.leakcanary.analyzer.ObjectInspector> experimentalObjectInspectors =
        config.useExperimentalLeakFinders
            ? config.objectInspectors
            : new ArrayList<com.squareup.leakcanary.analyzer.ObjectInspector>() {
              {
                add(new com.squareup.leakcanary.analyzer.ObjectInspectors.KeyedWeakReference());
              }
            };

    HeapAnalysis heapAnalysis =
        heapAnalyzer.checkForLeaks(
            heapDumpFile,
            referenceMatchers,
            retainedHeapSizeComputer,
            objectInspectors,
            experimentalObjectInspectors,
            new TimeoutConfiguration(config.analysisTimeoutMillis, config.analysisTimeoutWarningMillis));

    AnalysisResult analysisResult = new AnalysisResult(heapAnalysis);
    config.onHeapAnalyzedListener.onHeapAnalyzed(analysisResult);
  }

  @Override
  public void onAnalysisProgress(OnAnalysisProgressListener.Step step) {
    float percent = (100f * step.ordinal / OnAnalysisProgressListener.Step.values().length);
    SharkLog.d("Analysis in progress, working on: %s", step.name);
    String lowercase = step.name.replace("_", " ").toLowerCase();
    String message = lowercase.substring(0, 1).toUpperCase() + lowercase.substring(1);
    showForegroundNotification(100, Math.round(percent), false, message);
  }

  public static void runAnalysis(Context context, File heapDumpFile) {
    Intent intent = new Intent(context, HeapAnalyzerService.class);
    intent.putExtra(HEAPDUMP_FILE_EXTRA, heapDumpFile);
    startForegroundService(context, intent);
  }

  public static void startForegroundService(Context context, Intent intent) {
    if (Build.VERSION.SDK_INT >= 26) {
      context.startForegroundService(intent);
    } else {
      context.startService(intent);
    }
  }
}