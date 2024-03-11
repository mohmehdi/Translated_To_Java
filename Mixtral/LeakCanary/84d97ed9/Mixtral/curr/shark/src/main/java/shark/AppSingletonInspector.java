

package shark;

import shark.HeapObject.HeapInstance;

public class AppSingletonInspector implements ObjectInspector {
    private final String[] singletonClasses;

    public AppSingletonInspector(String... singletonClasses) {
        this.singletonClasses = singletonClasses;
    }

    @Override
    public void inspect(ObjectReporter reporter) {
        if (reporter.getHeapObject() instanceof HeapInstance) {
            HeapInstance heapInstance = (HeapInstance) reporter.getHeapObject();
            heapInstance.getInstanceClass()
                    .getClassHierarchy()
                    .forEach(heapClass -> {
                        if (Arrays.asList(singletonClasses).contains(heapClass.name)) {
                            reporter.reportNotLeaking(heapClass.name + " is an app singleton");
                        }
                    });
        }
    }
}