

package leakcanary;

import android.os.Debug;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import leakcanary.InstrumentationLeakResults.Result;
import leakcanary.internal.HeapDumpMemoryStore;
import org.junit.runner.notification.RunListener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class InstrumentationLeakDetector {

    public InstrumentationLeakResults detectLeaks() {
        long leakDetectionTime = SystemClock.uptimeMillis();
        long watchDurationMillis = LeakSentry.config.watchDurationMillis;
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        android.content.Context context = instrumentation.getTargetContext();
        RefWatcher refWatcher = LeakSentry.refWatcher;

        if (!refWatcher.hasWatchedReferences) {
            return InstrumentationLeakResults.NONE;
        }

        instrumentation.waitForIdleSync();
        if (!refWatcher.hasWatchedReferences) {
            return InstrumentationLeakResults.NONE;
        }

        GcTrigger.DEFAULT.runGc();
        if (!refWatcher.hasWatchedReferences) {
            return InstrumentationLeakResults.NONE;
        }

        SystemClock.sleep(2000);

        if (!refWatcher.hasWatchedReferences) {
            return InstrumentationLeakResults.NONE;
        }

        SystemClock.sleep(2000);

        long endOfWatchDelay = watchDurationMillis - (SystemClock.uptimeMillis() - leakDetectionTime);
        if (endOfWatchDelay > 0) {
            SystemClock.sleep(endOfWatchDelay);
        }

        GcTrigger.DEFAULT.runGc();

        if (!refWatcher.hasRetainedReferences) {
            return InstrumentationLeakResults.NONE;
        }

        File heapDumpFile = new File(context.getFilesDir(), "instrumentation_tests_heapdump.hprof");

        List<String> retainedKeys = refWatcher.retainedKeys;
        HeapDumpMemoryStore.setRetainedKeysForHeapDump(retainedKeys);
        HeapDumpMemoryStore.heapDumpUptimeMillis = SystemClock.uptimeMillis();

        try {
            Debug.dumpHprofData(heapDumpFile.getAbsolutePath());
        } catch (Exception e) {
            CanaryLog.d(e, "Could not dump heap");
            return InstrumentationLeakResults.NONE;
        }

        refWatcher.removeRetainedKeys(retainedKeys);

        LeakCanaryConfig config = LeakCanary.config;

        HeapAnalyzer heapAnalyzer = new HeapAnalyzer(
                config.excludedRefs, AnalyzerProgressListener.NONE,
                config.reachabilityInspectorClasses
        );

        List<AnalysisResult> results = heapAnalyzer.checkForLeaks(heapDumpFile, false);

        List<Result> detectedLeaks = new ArrayList<>();
        List<Result> excludedLeaks = new ArrayList<>();
        List<Result> failures = new ArrayList<>();

        for (AnalysisResult analysisResult : results) {
            HeapDump heapDump = new HeapDump.Builder()
                    .heapDumpFile(heapDumpFile)
                    .excludedRefs(config.excludedRefs)
                    .reachabilityInspectorClasses(config.reachabilityInspectorClasses)
                    .build();
            Result leakResult = new Result(heapDump, analysisResult);

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
        LeakCanary.config = LeakCanary.config.copy(false);
    }
}