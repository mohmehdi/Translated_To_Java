

package shark;

import shark.HeapObject.HeapInstance;

public class AppSingletonInspector implements ObjectInspector {
    private final String[] singletonClasses;

    public AppSingletonInspector(String... singletonClasses) {
        this.singletonClasses = singletonClasses;
    }

    @Override
    public void inspect(HeapGraph graph, ObjectReporter reporter) {
        if (reporter.getHeapObject() instanceof HeapInstance) {
            HeapInstance heapInstance = (HeapInstance) reporter.getHeapObject();
            heapInstance.getInstanceClass()
                    .getClassHierarchy()
                    .forEach(heapClass -> {
                        if (Arrays.asList(singletonClasses).contains(heapClass.getName())) {
                            reporter.reportNotLeaking(heapClass.getName() + " is an app singleton");
                        }
                    });
        }
    }
}