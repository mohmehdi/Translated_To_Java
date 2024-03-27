
package com.squareup.leakcanary;

import android.app.Application;
import android.os.Handler;
import android.os.SystemClock;
import com.squareup.leakcanary.LeakCanary.Config;
import com.squareup.leakcanary.internal.registerVisibilityListener;
import leaksentry.RefWatcher;

public class HeapDumpTrigger {

  private final Application application;
  private final Handler backgroundHandler;
  private final DebuggerControl debuggerControl;
  private final RefWatcher refWatcher;
  private final LeakDirectoryProvider leakDirectoryProvider;
  private final GcTrigger gcTrigger;
  private final HeapDumper heapDumper;
  private final HeapDump.Listener heapdumpListener;
  private final ConfigProvider configProvider;

  private volatile boolean applicationVisible = false;

  public HeapDumpTrigger(Application application, Handler backgroundHandler,
      DebuggerControl debuggerControl, RefWatcher refWatcher,
      LeakDirectoryProvider leakDirectoryProvider, GcTrigger gcTrigger, HeapDumper heapDumper,
      HeapDump.Listener heapdumpListener, ConfigProvider configProvider) {
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
    application.registerVisibilityListener(applicationVisible -> {
      this.applicationVisible = applicationVisible;
      if (!applicationVisible) {
        scheduleTick("app became invisible");
      }
    });
  }

  public void onReferenceRetained() {
    scheduleTick("found new reference retained");
  }

  private void tick(String reason) {
    CanaryLog.d("Checking retained references because %s", reason);
    Config config = configProvider.get();

    if (!config.dumpHeap) {
      return;
    }

    int minLeaks = applicationVisible ? MIN_LEAKS_WHEN_VISIBLE : MIN_LEAKS_WHEN_NOT_VISIBLE;
    int retainedKeys = refWatcher.retainedKeys.size();
    if (retainedKeys < minLeaks) {
      CanaryLog.d(
          "Found %d retained references, which is less than the min of %d", retainedKeys, minLeaks
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

    retainedKeys = refWatcher.retainedKeys.size();
    if (retainedKeys < minLeaks) {
      CanaryLog.d(
          "Found %d retained references after GC, which is less than the min of %d", retainedKeys,
          minLeaks
      );
      return;
    }

    HeapDumpMemoryStore.setRetainedKeysForHeapDump(retainedKeys);

    CanaryLog.d("Found %d retained references, dumping the heap", retainedKeys);
    HeapDumpMemoryStore.heapDumpUptimeMillis = SystemClock.uptimeMillis();
    String heapDumpFile = heapDumper.dumpHeap();
    long heapDumpDurationMillis =
        SystemClock.uptimeMillis() - HeapDumpMemoryStore.heapDumpUptimeMillis;

    if (heapDumpFile.equals(HeapDumper.RETRY_LATER)) {
      CanaryLog.d(
          "Failed to dump heap, will retry in %d ms", WAIT_FOR_HEAP_DUMPER_MILLIS
      );
      scheduleTick("failed to dump heap", WAIT_FOR_HEAP_DUMPER_MILLIS);
      return;
    }
    refWatcher.removeRetainedKeys(retainedKeys);

    HeapDump heapDump = new HeapDump.Builder()
        .heapDumpFile(heapDumpFile)
        .excludedRefs(config.excludedRefs)
        .gcDurationMs(gcDurationMillis)
        .heapDumpDurationMs(heapDumpDurationMillis)
        .computeRetainedHeapSize(config.computeRetainedHeapSize)
        .reachabilityInspectorClasses(config.reachabilityInspectorClasses)
        .build();
    heapdumpListener.analyze(heapDump);
  }

  private void scheduleTick(String reason) {
    backgroundHandler.post(() -> tick(reason));
  }

  private void scheduleTick(String reason, long delayMillis) {
    backgroundHandler.postDelayed(() -> tick(reason), delayMillis);
  }

  public static final String LEAK_CANARY_THREAD_NAME = "LeakCanary-Heap-Dump";
  public static final long WAIT_FOR_PENDING_ANALYSIS_MILLIS = 20_000L;
  public static final long WAIT_FOR_DEBUG_MILLIS = 20_000L;
  public static final long WAIT_FOR_HEAP_DUMPER_MILLIS = 5_000L;
  public static final int MIN_LEAKS_WHEN_VISIBLE = 5;
  public static final int MIN_LEAKS_WHEN_NOT_VISIBLE = 1;
}