
package leakcanary.internal;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import com.squareup.leakcanary.core.R;
import leakcanary.LeakCanary;
import shark.OnAnalysisProgressListener;
import shark.HeapAnalyzer;
import shark.ObjectInspectors;
import shark.SharkLog;
import java.io.File;

public class HeapAnalyzerService extends ForegroundService implements OnAnalysisProgressListener {

    private static final String HEAPDUMP_FILE_EXTRA = "HEAPDUMP_FILE_EXTRA";

    public HeapAnalyzerService() {
        super(HeapAnalyzerService.class.getSimpleName(), R.string.leak_canary_notification_analysing, R.id.leak_canary_notification_analyzing_heap);
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
                    "Hprof file missing due to: [" + LeakDirectoryProvider.hprofDeleteReason(heapDumpFile) + "] " + heapDumpFile
            );
        }

        HeapAnalyzer heapAnalyzer = new HeapAnalyzer(this);
        LeakCanary config = LeakCanary.config;

        HeapAnalyzer.HeapAnalysis heapAnalysis = heapAnalyzer.checkForLeaks(
                heapDumpFile, config.referenceMatchers, config.computeRetainedHeapSize, config.objectInspectors,
                config.useExperimentalLeakFinders ? config.objectInspectors : listOf(ObjectInspectors.KEYED_WEAK_REFERENCE)
        );

        config.onHeapAnalyzedListener.onHeapAnalyzed(heapAnalysis);
    }

    @Override
    public void onAnalysisProgress(OnAnalysisProgressListener.Step step) {
        int percent = (int) (100f * step.ordinal() / shark.OnAnalysisProgressListener.Step.values().length);
        SharkLog.d("Analysis in progress, working on: %s", step.name());
        String lowercase = step.name().replace("_", " ").toLowerCase();
        String message = lowercase.substring(0, 1).toUpperCase() + lowercase.substring(1);
        showForegroundNotification(100, percent, false, message);
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