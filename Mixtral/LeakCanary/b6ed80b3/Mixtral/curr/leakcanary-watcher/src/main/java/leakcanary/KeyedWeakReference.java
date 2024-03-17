

package leakcanary;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

public class KeyedWeakReference extends WeakReference<Object> {
    private final String key;
    private final String name;
    private final long watchUptimeMillis;

    public KeyedWeakReference(Object referent, String key, String name, long watchUptimeMillis, ReferenceQueue<Object> referenceQueue) {
        super(referent, referenceQueue);
        this.key = key;
        this.name = name;
        this.watchUptimeMillis = watchUptimeMillis;
    }

}