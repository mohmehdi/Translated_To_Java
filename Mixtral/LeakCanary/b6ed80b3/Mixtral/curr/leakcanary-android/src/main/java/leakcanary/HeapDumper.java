

package leakcanary;

import java.io.File;

public interface HeapDumper {

    File dumpHeap();

    HeapDumper NONE = new HeapDumper() {
        @Override
        public File dumpHeap() {
            return RETRY_LATER;
        }
    };

    File RETRY_LATER = null;
}