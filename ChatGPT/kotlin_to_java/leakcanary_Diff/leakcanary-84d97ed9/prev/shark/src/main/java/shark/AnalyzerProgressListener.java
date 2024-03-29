
package shark;

public interface AnalyzerProgressListener {

    enum Step {
        PARSING_HEAP_DUMP,
        FINDING_LEAKING_INSTANCES,
        FINDING_PATHS_TO_LEAKING_INSTANCES,
        FINDING_DOMINATORS,
        COMPUTING_NATIVE_RETAINED_SIZE,
        COMPUTING_RETAINED_SIZE,
        BUILDING_LEAK_TRACES
    }

    void onProgressUpdate(Step step);

    static final AnalyzerProgressListener NONE = new AnalyzerProgressListener() {
        @Override
        public void onProgressUpdate(Step step) {
        }
    };
}