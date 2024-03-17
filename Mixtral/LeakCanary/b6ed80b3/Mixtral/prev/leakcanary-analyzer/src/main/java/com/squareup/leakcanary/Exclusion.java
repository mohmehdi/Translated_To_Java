

package com.squareup.leakcanary;

import java.io.Serializable;

public class Exclusion implements Serializable {
    private final String name;
    private final String reason;
    private final boolean alwaysExclude;
    private final String matching;

    public Exclusion(ExcludedRefs.ParamsBuilder builder) {
        this.name = builder.name;
        this.reason = builder.reason;
        this.alwaysExclude = builder.alwaysExclude;
        this.matching = builder.matching;
    }

}