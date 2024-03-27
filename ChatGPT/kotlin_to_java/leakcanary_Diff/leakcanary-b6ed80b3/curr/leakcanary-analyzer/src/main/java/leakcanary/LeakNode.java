
package leakcanary;

import com.squareup.haha.perflib.Instance;

public class LeakNode {
    public final Exclusion exclusion;
    public final Instance instance;
    public final LeakNode parent;
    public final LeakReference leakReference;

    public LeakNode(Exclusion exclusion, Instance instance, LeakNode parent, LeakReference leakReference) {
        this.exclusion = exclusion;
        this.instance = instance;
        this.parent = parent;
        this.leakReference = leakReference;
    }
}