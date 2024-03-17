

package com.squareup.leakcanary;

import com.squareup.leakcanary.Reachability.Inspector;
import java.io.Serializable;

public class Reachability implements Serializable {

    public enum Status {
        REACHABLE,
        UNREACHABLE,
        UNKNOWN
    }

    public interface Inspector {
        Reachability expectedReachability(LeakTraceElement element);
    }

    public static final Reachability UNKNOWN_REACHABILITY = new Reachability(Status.UNKNOWN, "");

    public final Status status;
    public final String reason;

    private Reachability(Status status, String reason) {
        this.status = status;
        this.reason = reason;
    }

    public static Reachability reachable(String reason) {
        return new Reachability(Status.REACHABLE, reason);
    }

    public static Reachability unreachable(String reason) {
        return new Reachability(Status.UNREACHABLE, reason);
    }

    public static Reachability unknown() {
        return UNKNOWN_REACHABILITY;
    }
}