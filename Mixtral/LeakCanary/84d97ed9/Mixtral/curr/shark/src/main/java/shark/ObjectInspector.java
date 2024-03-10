

package shark;

import shark.ObjectInspector;
import shark.ObjectReporter;

@FunctionalInterface
public interface ObjectInspector {

  void inspect(ObjectReporter reporter);

  public static ObjectInspector invoke(ObjectInspector.ObjectReporterBlock block) {
    return new ObjectInspector() {
      @Override
      public void inspect(ObjectReporter reporter) {
        block.invoke(reporter);
      }
    };
  }


}