

package com.squareup.leakcanary;

import java.util.List;

public class InstrumentationLeakResults {

    public final List<Result> detectedLeaks;
    public final List<Result> excludedLeaks;
    public final List<Result> failures;

    public static final InstrumentationLeakResults NONE =
            new InstrumentationLeakResults(java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList());

    public InstrumentationLeakResults(List<Result> detectedLeaks, List<Result> excludedLeaks,
            List<Result> failures) {
        this.detectedLeaks = detectedLeaks;
        this.excludedLeaks = excludedLeaks;
        this.failures = failures;
    }

    public static class Result {
        public final HeapDump heapDump;
        public final AnalysisResult analysisResult;

        public Result(HeapDump heapDump, AnalysisResult analysisResult) {
            this.heapDump = heapDump;
            this.analysisResult = analysisResult;
        }
    }
}