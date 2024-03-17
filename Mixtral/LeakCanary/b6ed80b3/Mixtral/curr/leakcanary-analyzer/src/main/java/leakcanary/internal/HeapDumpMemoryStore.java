package leakcanary.internal;

import java.util.Arrays;
import java.util.Set;

public class HeapDumpMemoryStore {

  private static volatile Array retainedKeysForHeapDump;
  private static volatile long heapDumpUptimeMillis;

  public static void setRetainedKeysForHeapDump(Set retainedKeys) {
    retainedKeysForHeapDump = retainedKeys.toArray(new String[0]);
  }

}