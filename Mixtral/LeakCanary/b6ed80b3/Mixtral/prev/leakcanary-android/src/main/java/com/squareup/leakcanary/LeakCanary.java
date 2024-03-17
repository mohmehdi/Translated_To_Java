

package com.squareup.leakcanary;

import android.content.Context;

import com.squareup.leakcanary.internal.HeapAnalyzerService;
import com.squareup.leakcanary.internal.InternalLeakCanary;
import com.squareup.leakcanary.excludedrefs.ExcludedRefs;
import com.squareup.leakcanary.excludedrefs.AndroidExcludedRefs;
import com.squareup.leakcanary.reachability.Reachability;
import com.squareup.leakcanary.reachability.AndroidReachabilityInspectors;
import com.squareup.leakcanary.analysis.HeapDump;
import com.squareup.leakcanary.analysis.AnalysisResult;
import com.squareup.leakcanary.analysis.AnalysisContext;

public class LeakCanary {

    public static class Config {
        public boolean dumpHeap = true;
        public ExcludedRefs excludedRefs = AndroidExcludedRefs.createAppDefaults().build();
        public Class[] reachabilityInspectorClasses = AndroidReachabilityInspectors.defaultAndroidInspectors();
        public boolean computeRetainedHeapSize = false;
    }

    public static volatile Config config = new Config();

    public static String leakInfo(Context context, HeapDump heapDump, AnalysisResult result, boolean detailed) {
        return InternalLeakCanary.leakInfo(context, heapDump, result, detailed);
    }

    public static boolean isInAnalyzerProcess(Context context) {
        return InternalLeakCanary.isInAnalyzerProcess(context);
    }
}