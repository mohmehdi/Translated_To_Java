
package leakcanary.internal;

public class HeapDumpMemoryStore {

  private static volatile String[] retainedKeysForHeapDump = null;

  private static volatile long heapDumpUptimeMillis = 0;

  public static void setRetainedKeysForHeapDump(Set<String> retainedKeys) {
    retainedKeysForHeapDump = retainedKeys.toArray(new String[0]);
  }

}