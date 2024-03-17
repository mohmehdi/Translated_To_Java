

package leakcanary;

import android.content.Context;
import leakcanary.internal.HeapAnalyzerService;
import leakcanary.internal.InternalLeakCanary;
import java.util.List;

public class LeakCanary {

    public static class Config {
        public boolean dumpHeap = true;
        public ExcludedRefs excludedRefs = AndroidExcludedRefs.createAppDefaults().build();
        public List<Class<out Reachability.Inspector>> reachabilityInspectorClasses = AndroidReachabilityInspectors.defaultAndroidInspectors();
        public boolean computeRetainedHeapSize = false;
    }

    @javax.annotation.concurrent.GuardedBy("this")
    public static Config config = new Config();

    public static String leakInfo(Context context, HeapDump heapDump, AnalysisResult result, boolean detailed) {
        return InternalLeakCanary.leakInfo(context, heapDump, result, detailed);
    }

    public static boolean isInAnalyzerProcess(Context context) {
        return InternalLeakCanary.isInAnalyzerProcess(context);
    }
}