package leakcanary;

import android.content.Context;
import leakcanary.internal.HeapAnalyzerService;
import leakcanary.internal.InternalLeakCanary;

public class LeakCanary {

  public static class Config {
    public boolean dumpHeap = true;
    public ExcludedRefs excludedRefs = AndroidExcludedRefs.createAppDefaults().build();
    public List<Class<? extends Reachability.Inspector>> reachabilityInspectorClasses = AndroidReachabilityInspectors.defaultAndroidInspectors();
    public boolean computeRetainedHeapSize = false;
  }

  public static volatile Config config = new Config();

  public static String leakInfo(
    Context context,
    HeapDump heapDump,
    AnalysisResult result,
    boolean detailed
  ) {
    return InternalLeakCanary.leakInfo(context, heapDump, result, detailed);
  }

  public static boolean isInAnalyzerProcess(Context context) {
    return InternalLeakCanary.isInAnalyzerProcess(context);
  }

}