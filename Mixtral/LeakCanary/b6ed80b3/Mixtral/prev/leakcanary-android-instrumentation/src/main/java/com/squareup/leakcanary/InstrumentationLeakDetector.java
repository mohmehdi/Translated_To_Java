

package com.squareup.leakcanary;

import android.content.Context;
import android.os.Debug;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import leaksentry.LeakSentry;
import java.io.File;

public class InstrumentationLeakDetector {

  public InstrumentationLeakResults detectLeaks() {
    long leakDetectionTime = SystemClock.uptimeMillis();
    long watchDurationMillis = LeakSentry.config.watchDurationMillis;
    Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    Context context = instrumentation.getTargetContext();
    RefWatcher refWatcher = LeakSentry.refWatcher;

    if (!refWatcher.hasWatchedReferences()) {
      return InstrumentationLeakResults.NONE;
    }

    instrumentation.waitForIdleSync();
    if (!refWatcher.hasWatchedReferences()) {
      return InstrumentationLeakResults.NONE;
    }

    GcTrigger.DEFAULT.runGc();
    if (!refWatcher.hasWatchedReferences()) {
      return InstrumentationLeakResults.NONE;
    }

    SystemClock.sleep(2000);

    if (!refWatcher.hasWatchedReferences()) {
      return InstrumentationLeakResults.NONE;
    }

    SystemClock.sleep(2000);

    long endOfWatchDelay = watchDurationMillis - (SystemClock.uptimeMillis() - leakDetectionTime);
    if (endOfWatchDelay > 0) {
      SystemClock.sleep(endOfWatchDelay);
    }

    GcTrigger.DEFAULT.runGc();

    if (!refWatcher.hasRetainedReferences()) {
      return InstrumentationLeakResults.NONE;
    }

    File heapDumpFile = new File(context.getFilesDir(), "instrumentation_tests_heapdump.hprof");

    retainedKeys = refWatcher.retainedKeys();
    HeapDumpMemoryStore.setRetainedKeysForHeapDump(retainedKeys);
    HeapDumpMemoryStore.heapDumpUptimeMillis = SystemClock.uptimeMillis();

    try {
      Debug.dumpHprofData(heapDumpFile.getAbsolutePath());
    } catch (Exception e) {
      CanaryLog.d(e, "Could not dump heap");
      return InstrumentationLeakResults.NONE;
    }

    refWatcher.removeRetainedKeys(retainedKeys);

    Config config = LeakCanary.config;

    HeapAnalyzer heapAnalyzer = new HeapAnalyzer(
        config.excludedRefs, AnalyzerProgressListener.NONE,
        config.reachabilityInspectorClasses
    );

    AnalysisResults results = heapAnalyzer.checkForLeaks(heapDumpFile, false);

    java.util.List<InstrumentationLeakResults.Result> detectedLeaks = new ArrayList<>();
    java.util.List<InstrumentationLeakResults.Result> excludedLeaks = new ArrayList<>();
    java.util.List<InstrumentationLeakResults.Result> failures = new ArrayList<>();

    for (AnalysisResult analysisResult : results) {
      HeapDump heapDump = new HeapDump.Builder()
          .heapDumpFile(heapDumpFile)
          .excludedRefs(config.excludedRefs)
          .reachabilityInspectorClasses(config.reachabilityInspectorClasses)
          .build();
      InstrumentationLeakResults.Result leakResult = new InstrumentationLeakResults.Result(heapDump, analysisResult);

      if (analysisResult.leakFound) {
        if (!analysisResult.excludedLeak) {
          detectedLeaks.add(leakResult);
        } else {
          excludedLeaks.add(leakResult);
        }
      } else if (analysisResult.failure != null) {
        failures.add(leakResult);
      }
    }

    CanaryLog.d(
        "Found %d proper leaks, %d excluded leaks and %d leak analysis failures",
        detectedLeaks.size(),
        excludedLeaks.size(),
        failures.size()
    );

    return new InstrumentationLeakResults(detectedLeaks, excludedLeaks, failures);
  }

  public static void updateConfig() {
    LeakCanary.config = LeakCanary.config.copy(dumpHeap = false);
  }
}