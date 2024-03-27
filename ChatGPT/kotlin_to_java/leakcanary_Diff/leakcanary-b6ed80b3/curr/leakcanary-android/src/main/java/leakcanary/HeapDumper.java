
package leakcanary;

import java.io.File;

public interface HeapDumper {

    File dumpHeap();

        public static final HeapDumper NONE = new HeapDumper() {
            @Override
            public File dumpHeap() {
                return RETRY_LATER;
            }
        };

        public static final File RETRY_LATER = null;
}