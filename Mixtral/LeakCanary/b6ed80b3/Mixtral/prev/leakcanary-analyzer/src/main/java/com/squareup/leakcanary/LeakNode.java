

package com.squareup.leakcanary;

import com.squareup.haha.perflib.Instance;

public class LeakNode {
    private final Exclusion exclusion;
    private final Instance instance;
    private final LeakNode parent;
    private final LeakReference leakReference;

    public LeakNode(Exclusion exclusion, Instance instance, LeakNode parent, LeakReference leakReference) {
        this.exclusion = exclusion;
        this.instance = instance;
        this.parent = parent;
        this.leakReference = leakReference;
    }

}