
package leakcanary;

import java.io.Serializable;

public class Reachability implements Serializable {

    private final Status status;
    private final String reason;

    public enum Status {
        REACHABLE,
        UNREACHABLE,
        UNKNOWN
    }

    public interface Inspector {
        Reachability expectedReachability(LeakTraceElement element);
    }

    private static final Reachability UNKNOWN_REACHABILITY = new Reachability(Status.UNKNOWN, "");

    public Reachability(Status status, String reason) {
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