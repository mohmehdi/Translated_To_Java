

package com.squareup.leakcanary;

import android.app.Application;
import android.os.Handler;
import android.os.SystemClock;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.internal.Registration;
import leaksentry.RefWatcher;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

class HeapDumpTrigger {
    private final Application application;
    private final Handler backgroundHandler;
    private final DebuggerControl debuggerControl;
    private final RefWatcher refWatcher;
    private final LeakDirectoryProvider leakDirectoryProvider;
    private final GcTrigger gcTrigger;
    private final HeapDumper heapDumper;
    private final HeapDump.Listener heapdumpListener;
    private final Supplier<LeakCanary.Config> configProvider;

    @Volatile
    private boolean applicationVisible;

    public HeapDumpTrigger(Application application, Handler backgroundHandler, DebuggerControl debuggerControl, RefWatcher refWatcher, LeakDirectoryProvider leakDirectoryProvider, GcTrigger gcTrigger, HeapDumper heapDumper, HeapDump.Listener heapdumpListener, Supplier<LeakCanary.Config> configProvider) {
        this.application = application;
        this.backgroundHandler = backgroundHandler;
        this.debuggerControl = debuggerControl;
        this.refWatcher = refWatcher;
        this.leakDirectoryProvider = leakDirectoryProvider;
        this.gcTrigger = gcTrigger;
        this.heapDumper = heapDumper;
        this.heapdumpListener = heapdumpListener;
        this.configProvider = configProvider;
    }

    public void registerToVisibilityChanges() {
        Registration registration = application.registerVisibilityListener(new Application.VisibilityListener() {
    }

    public void onReferenceRetained() {
        scheduleTick("found new reference retained");
    }

    private void tick(String reason) {
        CanaryLog.d("Checking retained references because %s", reason);
        LeakCanary.Config config = configProvider.get();

        if (!config.dumpHeap) {
            return;
        }

        int minLeaks = applicationVisible ? MIN_LEAKS_WHEN_VISIBLE : MIN_LEAKS_WHEN_NOT_VISIBLE;
        Set<Key> retainedKeys = refWatcher.getRetainedKeys();
        if (retainedKeys.size() < minLeaks) {
            CanaryLog.d(
                    "Found %d retained references, which is less than the min of %d", retainedKeys.size(), minLeaks
            );
            return;
        }

        if (debuggerControl.isDebuggerAttached()) {
            scheduleTick("debugger was attached", WAIT_FOR_DEBUG_MILLIS);
            CanaryLog.d(
                    "Not checking for leaks while the debugger is attached, will retry in %d ms",
                    WAIT_FOR_DEBUG_MILLIS
            );
            return;
        }

        if (leakDirectoryProvider.hasPendingHeapDump()) {
            CanaryLog.d(
                    "Leak Analysis in progress, will retry in %d ms", WAIT_FOR_PENDING_ANALYSIS_MILLIS
            );
            scheduleTick("had pending heap dump", WAIT_FOR_PENDING_ANALYSIS_MILLIS);
            return;
        }
        long gcStartUptimeMillis = SystemClock.uptimeMillis();
        gcTrigger.runGc();
        long gcDurationMillis = SystemClock.uptimeMillis() - gcStartUptimeMillis;

        retainedKeys = refWatcher.getRetainedKeys();
        if (retainedKeys.size() < minLeaks) {
            CanaryLog.d(
                    "Found %d retained references after GC, which is less than the min of %d", retainedKeys.size(),
                    minLeaks
            );
            return;
        }

        HeapDumpMemoryStore.setRetainedKeysForHeapDump(retainedKeys);

        CanaryLog.d("Found %d retained references, dumping the heap", retainedKeys.size());
        long heapDumpUptimeMillis = SystemClock.uptimeMillis();
        File heapDumpFile = heapDumper.dumpHeap();
        long heapDumpDurationMillis =
                SystemClock.uptimeMillis() - heapDumpUptimeMillis;

        if (heapDumpFile == HeapDumper.RETRY_LATER) {
            CanaryLog.d(
                    "Failed to dump heap, will retry in %d ms", WAIT_FOR_HEAP_DUMPER_MILLIS
            );
            scheduleTick("failed to dump heap", WAIT_FOR_HEAP_DUMPER_MILLIS);
            return;
        }
        refWatcher.removeRetainedKeys(retainedKeys);

        HeapDump heapDump = new HeapDump.Builder()
                .heapDumpFile(heapDumpFile)
                .excludedRefs(config.getExcludedRefs())
                .gcDurationMs(gcDurationMillis)
                .heapDumpDurationMs(heapDumpDurationMillis)
                .computeRetainedHeapSize(config.getComputeRetainedHeapSize())
                .reachabilityInspectorClasses(config.getReachabilityInspectorClasses())
                .build();
        heapdumpListener.analyze(heapDump);
    }

    private void scheduleTick(String reason) {
        backgroundHandler.post(() -> tick(reason));
    }

    private void scheduleTick(String reason, long delayMillis) {
        backgroundHandler.postDelayed(() -> tick(reason), delayMillis);
    }

    companion object {
        final String LEAK_CANARY_THREAD_NAME = "LeakCanary-Heap-Dump";
        final long WAIT_FOR_PENDING_ANALYSIS_MILLIS = 20_000L;
        final long WAIT_FOR_DEBUG_MILLIS = 20_000L;
        final long WAIT_FOR_HEAP_DUMPER_MILLIS = 5_000L;
        final int MIN_LEAKS_WHEN_VISIBLE = 5;
        final int MIN_LEAKS_WHEN_NOT_VISIBLE = 1;
    }
}