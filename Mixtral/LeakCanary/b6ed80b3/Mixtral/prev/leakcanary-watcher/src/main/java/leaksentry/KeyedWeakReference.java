

package leaksentry;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

@SuppressWarnings("unused")
class KeyedWeakReference extends WeakReference<Object> {
    final String key;
    final String name;
    final long watchUptimeMillis;

    public KeyedWeakReference(Object referent, String key, String name, long watchUptimeMillis, ReferenceQueue<Object> referenceQueue) {
        super(referent, referenceQueue);
        this.key = key;
        this.name = name;
        this.watchUptimeMillis = watchUptimeMillis;
    }
}