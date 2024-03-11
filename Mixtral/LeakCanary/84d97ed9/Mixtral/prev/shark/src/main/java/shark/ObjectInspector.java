

package shark;

import shark.HeapGraph;
import shark.ObjectReporter;

@FunctionalInterface
interface ObjectInspector {

    void inspect(HeapGraph graph, ObjectReporter reporter);

    static ObjectInspector invoke(BiConsumer<HeapGraph, ObjectReporter> block) {
        return new ObjectInspector() {
            @Override
            public void inspect(HeapGraph graph, ObjectReporter reporter) {
                block.accept(graph, reporter);
            }
        };
    }
}