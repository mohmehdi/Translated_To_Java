

package leakcanary;

import android.app.Application;
import android.os.Handler;
import android.os.SystemClock;
import leakcanary.LeakCanary;
import leakcanary.internal.Debuggers;
import leakcanary.internal.HeapDumpMemoryStore;
import leakcanary.internal.RegisterVisibilityListener;
import leakcanary.HeapDump;
import leakcanary.HeapDump$Builder;
import leakcanary.RefWatcher;
import leakcanary.LeakDirectoryProvider;
import leakcanary.GcTrigger;
import leakcanary.HeapDumper;
import leakcanary.Listener;
import leakcanary.Config;
import java.io.File;

public class HeapDumpTrigger {
  private final Application application;
  private final Handler backgroundHandler;
  private final Debuggers.DebuggerControl debuggerControl;
  private final RefWatcher refWatcher;
  private final LeakDirectoryProvider leakDirectoryProvider;
  private final GcTrigger gcTrigger;
  private final HeapDumper heapDumper;
  private final Listener heapdumpListener;
  private final Config.Factory configProvider;

  @Volatile
  private boolean applicationVisible;

  public HeapDumpTrigger(
      Application application,
      Handler backgroundHandler,
      Debuggers.DebuggerControl debuggerControl,
      RefWatcher refWatcher,
      LeakDirectoryProvider leakDirectoryProvider,
      GcTrigger gcTrigger,
      HeapDumper heapDumper,
      Listener heapdumpListener,
      Config.Factory configProvider) {
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
    RegisterVisibilityListener.register(application, new VisibilityListener());
  }

  public void onReferenceRetained() {
    scheduleTick("found new reference retained");
  }

  private void tick(String reason) {
    CanaryLog.d("Checking retained references because %s", reason);
    Config config = configProvider.createConfig();

    if (!config.dumpHeap) {
      return;
    }

    int minLeaks = applicationVisible ? MIN_LEAKS_WHEN_VISIBLE : MIN_LEAKS_WHEN_NOT_VISIBLE;
    Set<Key> retainedKeys = refWatcher.getRetainedKeys();
    if (retainedKeys.size() < minLeaks) {
      CanaryLog.d(
          "Found %d retained references, which is less than the min of %d", retainedKeys.size(),
          minLeaks);
      return;
    }

    if (debuggerControl.isDebuggerAttached()) {
      scheduleTick("debugger was attached", WAIT_FOR_DEBUG_MILLIS);
      CanaryLog.d(
          "Not checking for leaks while the debugger is attached, will retry in %d ms",
          WAIT_FOR_DEBUG_MILLIS);
      return;
    }

    if (leakDirectoryProvider.hasPendingHeapDump()) {
      CanaryLog.d(
          "Leak Analysis in progress, will retry in %d ms", WAIT_FOR_PENDING_ANALYSIS_MILLIS);
      scheduleTick("had pending heap dump", WAIT_FOR_PENDING_ANALYSIS_MILLIS);
      return;
    }
    long gcStartUptimeMillis = SystemClock.uptimeMillis();
    gcTrigger.runGc();
    long gcDurationMillis = SystemClock.uptimeMillis() - gcStartUptimeMillis;

    retainedKeys = refWatcher.getRetainedKeys();
    if (retainedKeys.size() < minLeaks) {
      CanaryLog.d(
          "Found %d retained references after GC, which is less than the min of %d",
          retainedKeys.size(), minLeaks);
      return;
    }

    HeapDumpMemoryStore.setRetainedKeysForHeapDump(retainedKeys);

    CanaryLog.d("Found %d retained references, dumping the heap", retainedKeys.size());
    HeapDumpMemoryStore.heapDumpUptimeMillis = SystemClock.uptimeMillis();
    File heapDumpFile = heapDumper.dumpHeap();
    long heapDumpDurationMillis =
        SystemClock.uptimeMillis() - HeapDumpMemoryStore.heapDumpUptimeMillis;

    if (heapDumpFile == HeapDumper.RETRY_LATER) {
      CanaryLog.d(
          "Failed to dump heap, will retry in %d ms", WAIT_FOR_HEAP_DUMPER_MILLIS);
      scheduleTick("failed to dump heap", WAIT_FOR_HEAP_DUMPER_MILLIS);
      return;
    }
    refWatcher.removeRetainedKeys(retainedKeys);

    HeapDump heapDump =
        new HeapDump.Builder()
            .heapDumpFile(heapDumpFile)
            .excludedRefs(config.getExcludedRefs())
            .gcDurationMs(gcDurationMillis)
            .heapDumpDurationMs(heapDumpDurationMillis)
            .computeRetainedHeapSize(config.shouldComputeRetainedHeapSize())
            .reachabilityInspectorClasses(config.getReachabilityInspectorClasses())
            .build();
    heapdumpListener.analyze(heapDump);
  }

  private void scheduleTick(String reason) {
    backgroundHandler.post(
        new Runnable() {
          @Override
          public void run() {
            tick(reason);
          }
        });
  }

  private void scheduleTick(String reason, long delayMillis) {
    backgroundHandler.postDelayed(
        new Runnable() {
          @Override
          public void run() {
            tick(reason);
          }
        },
        delayMillis);
  }


  static {
    System.loadLibrary("leakcanary");
  }
}