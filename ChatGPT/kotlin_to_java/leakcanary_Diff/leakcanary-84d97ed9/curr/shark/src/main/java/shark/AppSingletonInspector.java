
package shark;

import shark.HeapObject.HeapInstance;

public class AppSingletonInspector implements ObjectInspector {

    private final String[] singletonClasses;

    public AppSingletonInspector(String... singletonClasses) {
        this.singletonClasses = singletonClasses;
    }

    @Override
    public void inspect(ObjectReporter reporter) {
        if (reporter.heapObject instanceof HeapInstance) {
            ((HeapInstance) reporter.heapObject).instanceClass
                    .classHierarchy
                    .forEach(heapClass -> {
                        if (heapClass.name in singletonClasses) {
                            reporter.reportNotLeaking(heapClass.name + " is an app singleton");
                        }
                    });
        }
    }
}