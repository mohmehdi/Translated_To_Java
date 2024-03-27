

package com.squareup.leakcanary;

public interface AnalyzerProgressListener {

    enum Step {
        READING_HEAP_DUMP_FILE,
        PARSING_HEAP_DUMP,
        DEDUPLICATING_GC_ROOTS,
        FINDING_LEAKING_REF,
        FINDING_LEAKING_REFS,
        FINDING_SHORTEST_PATH,
        BUILDING_LEAK_TRACE,
        COMPUTING_DOMINATORS
    }

    void onProgressUpdate(Step step);

    static final AnalyzerProgressListener NONE = new AnalyzerProgressListener() {
        @Override
        public void onProgressUpdate(Step step) {
        }
    };
}