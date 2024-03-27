
package com.squareup.leakcanary.internal;

import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;
import com.squareup.leakcanary.AbstractAnalysisResultService;
import com.squareup.leakcanary.AnalyzerProgressListener;
import com.squareup.leakcanary.CanaryLog;
import com.squareup.leakcanary.HeapAnalyzer;
import com.squareup.leakcanary.HeapDump;
import com.squareup.leakcanary.R;
import com.squareup.leakcanary.internal.LeakCanaryInternals;

import java.io.File;
import java.io.IOException;

public class HeapAnalyzerService extends ForegroundService implements AnalyzerProgressListener {

    private static final String LISTENER_CLASS_EXTRA = "listener_class_extra";
    private static final String HEAPDUMP_EXTRA = "heapdump_extra";

    public HeapAnalyzerService() {
        super(HeapAnalyzerService.class.getSimpleName(), R.string.leak_canary_notification_analysing);
    }

    @Override
    protected void onHandleIntentInForeground(Intent intent) {
        if (intent == null) {
            CanaryLog.d("HeapAnalyzerService received a null intent, ignoring.");
            return;
        }
        String listenerClassName = intent.getStringExtra(LISTENER_CLASS_EXTRA);
        HeapDump heapDump = (HeapDump) intent.getSerializableExtra(HEAPDUMP_EXTRA);

        HeapAnalyzer heapAnalyzer = new HeapAnalyzer(
                heapDump.excludedRefs, this,
                heapDump.reachabilityInspectorClasses
        );

        AnalysisResult result;
        int i = 0;
        for (AnalysisResult result : heapAnalyzer.checkForLeaks(
                heapDump.heapDumpFile,
                heapDump.computeRetainedHeapSize
        )) {
            HeapDump fakeFileHeapDump;
            if (i > 0) {
                File newFile = new File(
                        heapDump.heapDumpFile.getParentFile(),
                        i + heapDump.heapDumpFile.getName()
                );
                try {
                    boolean created = newFile.createNewFile();
                    if (!created) {
                        continue;
                    }
                } catch (IOException e) {
                    continue;
                }

                fakeFileHeapDump = heapDump.buildUpon()
                        .heapDumpFile(newFile)
                        .build();
            } else {
                fakeFileHeapDump = heapDump;
            }
            AbstractAnalysisResultService.sendResultToListener(
                    this, listenerClassName, fakeFileHeapDump,
                    result
            );
            i++;
        }
    }

    @Override
    public void onProgressUpdate(AnalyzerProgressListener.Step step) {
        int percent = (int) (100f * step.ordinal() / AnalyzerProgressListener.Step.values().length);
        CanaryLog.d("Analysis in progress, working on: %s", step.name());
        String lowercase = step.name().replace("_", " ")
                .toLowerCase();
        String message = lowercase.substring(0, 1).toUpperCase() + lowercase.substring(1);
        showForegroundNotification(100, percent, false, message);
    }

    public static void runAnalysis(
            Context context,
            HeapDump heapDump,
            Class<? extends AbstractAnalysisResultService> listenerServiceClass
    ) {
        LeakCanaryInternals.setEnabledBlocking(context, HeapAnalyzerService.class, true);
        LeakCanaryInternals.setEnabledBlocking(context, listenerServiceClass, true);
        Intent intent = new Intent(context, HeapAnalyzerService.class);
        intent.putExtra(LISTENER_CLASS_EXTRA, listenerServiceClass.getName());
        intent.putExtra(HEAPDUMP_EXTRA, heapDump);
        ContextCompat.startForegroundService(context, intent);
    }
}