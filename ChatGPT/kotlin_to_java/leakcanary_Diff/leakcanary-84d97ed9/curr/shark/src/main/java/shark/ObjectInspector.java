
package shark;

public interface ObjectInspector {

    void inspect(ObjectReporter reporter);

    static ObjectInspector invoke(ObjectReporter block) {
        return new ObjectInspector() {
            @Override
            public void inspect(ObjectReporter reporter) {
                block.invoke(reporter);
            }
        };
    }
}