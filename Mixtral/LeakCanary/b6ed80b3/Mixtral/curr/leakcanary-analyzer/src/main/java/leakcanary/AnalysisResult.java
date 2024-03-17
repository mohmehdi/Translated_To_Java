

package leakcanary;

import java.io.Serializable;
import java.util.UUID;
import java.util.Arrays;

public class AnalysisResult implements Serializable {

    private final String referenceKey;
    private final String referenceName;
    private final boolean leakFound;
    private final boolean excludedLeak;
    private final String className;
    private final LeakTrace leakTrace;
    private final Throwable failure;
    private final long retainedHeapSize;
    private final long analysisDurationMs;
    private final long watchDurationMs;

    public AnalysisResult(
            String referenceKey,
            String referenceName,
            boolean leakFound,
            boolean excludedLeak,
            String className,
            LeakTrace leakTrace,
            Throwable failure,
            long retainedHeapSize,
            long analysisDurationMs,
            long watchDurationMs) {
        this.referenceKey = referenceKey;
        this.referenceName = referenceName;
        this.leakFound = leakFound;
        this.excludedLeak = excludedLeak;
        this.className = className;
        this.leakTrace = leakTrace;
        this.failure = failure;
        this.retainedHeapSize = retainedHeapSize;
        this.analysisDurationMs = analysisDurationMs;
        this.watchDurationMs = watchDurationMs;
    }

    public RuntimeException leakTraceAsFakeException() {
        if (!leakFound) {
            throw new UnsupportedOperationException(
                    "leakTraceAsFakeException() can only be called when leakFound is true"
            );
        }
        String rootSimpleName = classSimpleName(leakTrace.elements.get(0).className);
        String leakSimpleName = classSimpleName(className);

        RuntimeException runtimeException = new RuntimeException(
                leakSimpleName + " leak from " + rootSimpleName +
                        " (holder=" + leakTrace.elements.get(0).holder +
                        ", type= " + leakTrace.elements.get(0).reference.type + ")"
        );
        StackTraceElement[] stackTrace = new StackTraceElement[leakTrace.elements.size()];
        for (int i = 0; i < leakTrace.elements.size(); i++) {
            String methodName = leakTrace.elements.get(i).reference.name != null ?
                    leakTrace.elements.get(i).reference.name : "leaking";
            String file = classSimpleName(leakTrace.elements.get(i).className) + ".java";
            stackTrace[i] = new StackTraceElement(leakTrace.elements.get(i).className, methodName, file, 42);
        }
        runtimeException.setStackTrace(stackTrace);
        return runtimeException;
    }

    private String classSimpleName(String className) {
        int separator = className.lastIndexOf('.');
        return separator == -1 ? className : className.substring(separator + 1);
    }

    public static AnalysisResult noLeak(
            String className,
            long analysisDurationMs) {
        return new AnalysisResult(
                "Fake-" + UUID.randomUUID(),
                "",
                false,
                false,
                className,
                null,
                null,
                0,
                analysisDurationMs,
                0
        );
    }

    public static AnalysisResult leakDetected(
            String referenceKey,
            String referenceName,
            boolean excludedLeak,
            String className,
            LeakTrace leakTrace,
            long retainedHeapSize,
            long analysisDurationMs,
            long watchDurationMs) {
        return new AnalysisResult(
                referenceKey,
                referenceName,
                true,
                excludedLeak,
                className,
                leakTrace,
                null,
                retainedHeapSize,
                analysisDurationMs,
                watchDurationMs
        );
    }

    public static AnalysisResult failure(
            Throwable failure,
            long analysisDurationMs) {
        return new AnalysisResult(
                "Fake-" + UUID.randomUUID(),
                "",
                false,
                false,
                null,
                null,
                failure,
                0,
                analysisDurationMs,
                0
        );
    }
}
