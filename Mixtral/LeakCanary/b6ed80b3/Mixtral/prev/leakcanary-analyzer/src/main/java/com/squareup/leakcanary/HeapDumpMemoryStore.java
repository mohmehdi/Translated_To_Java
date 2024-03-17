

package com.squareup.leakcanary;

import java.util.Arrays;

public class HeapDumpMemoryStore {

    @Volatile
    private static String[] retainedKeysForHeapDump;
    @Volatile
    private static long heapDumpUptimeMillis;

    public static void setRetainedKeysForHeapDump(Set<String> retainedKeys) {
        retainedKeysForHeapDump = retainedKeys.toArray(new String[0]);
    }

}