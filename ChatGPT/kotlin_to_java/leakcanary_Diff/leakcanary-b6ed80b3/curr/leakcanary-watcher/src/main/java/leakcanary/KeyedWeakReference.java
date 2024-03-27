
package leakcanary;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

@SuppressWarnings("unused")
public class KeyedWeakReference extends WeakReference<Object> {

    public final String key;
    public final String name;
    public final long watchUptimeMillis;

    public KeyedWeakReference(
            Object referent,
            String key,
            String name,
            long watchUptimeMillis,
            ReferenceQueue<Object> referenceQueue
    ) {
        super(referent, referenceQueue);
        this.key = key;
        this.name = name;
        this.watchUptimeMillis = watchUptimeMillis;
    }
}