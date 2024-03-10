

package shark;

import java.util.function.Consumer;

public interface OnAnalysisProgressListener {

    enum Step {
        PARSING_HEAP_DUMP,
        FINDING_LEAKING_INSTANCES,
        FINDING_PATHS_TO_LEAKING_INSTANCES,
        FINDING_DOMINATORS,
        COMPUTING_NATIVE_RETAINED_SIZE,
        COMPUTING_RETAINED_SIZE,
        BUILDING_LEAK_TRACES
    }

    void onAnalysisProgress(Step step);

    OnAnalysisProgressListener NO_OP = new OnAnalysisProgressListener() {
        @Override
        public void onAnalysisProgress(Step step) {
            // No operation
        }
    };

    default OnAnalysisProgressListener invoke(Consumer<Step> block) {
        return new OnAnalysisProgressListener() {
            @Override
            public void onAnalysisProgress(Step step) {
                block.accept(step);
            }
        };
    }
}