

package com.squareup.leakcanary;

import java.util.EnumSet;
import java.util.Objects;

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

    AnalyzerProgressListener NONE = new AnalyzerProgressListener() {
        @Override
        public void onProgressUpdate(Step step) {
            // Intentionally left blank
        }
    };
}