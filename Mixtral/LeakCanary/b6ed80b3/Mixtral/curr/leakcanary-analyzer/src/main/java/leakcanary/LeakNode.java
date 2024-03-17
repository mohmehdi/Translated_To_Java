

package leakcanary;

import com.squareup.haha.perflib.Instance;

public class LeakNode {
    private Exclusion exclusion;
    private Instance instance;
    private LeakNode parent;
    private LeakReference leakReference;

    public LeakNode(Exclusion exclusion, Instance instance, LeakNode parent, LeakReference leakReference) {
        this.exclusion = exclusion;
        this.instance = instance;
        this.parent = parent;
        this.leakReference = leakReference;
    }

}