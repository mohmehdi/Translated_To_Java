
package shark;

import static shark.ObjectInspector.invoke;

public interface ObjectInspector {

    void inspect(HeapGraph graph, ObjectReporter reporter);

    static ObjectInspector invoke(ObjectInspector block) {
        return new ObjectInspector() {
            @Override
            public void inspect(HeapGraph graph, ObjectReporter reporter) {
                block.inspect(graph, reporter);
            }
        };
    }
}