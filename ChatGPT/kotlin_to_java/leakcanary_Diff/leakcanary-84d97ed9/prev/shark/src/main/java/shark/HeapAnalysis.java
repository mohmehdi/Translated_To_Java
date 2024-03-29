package shark;

import shark.Leak.ApplicationLeak;
import shark.Leak.LibraryLeak;
import shark.internal.createSHA1Hash;
import java.io.File;
import java.io.Serializable;
import java.util.List;

public sealed class HeapAnalysis implements Serializable {
    public abstract File heapDumpFile;
    public abstract long createdAtTimeMillis;
    public abstract long analysisDurationMillis;
}

public class HeapAnalysisFailure extends HeapAnalysis {
    public final File heapDumpFile;
    public final long createdAtTimeMillis;
    public final long analysisDurationMillis;
    public final HeapAnalysisException exception;

    public HeapAnalysisFailure(File heapDumpFile, long createdAtTimeMillis, long analysisDurationMillis, HeapAnalysisException exception) {
        this.heapDumpFile = heapDumpFile;
        this.createdAtTimeMillis = createdAtTimeMillis;
        this.analysisDurationMillis = analysisDurationMillis;
        this.exception = exception;
    }
}

public class HeapAnalysisSuccess extends HeapAnalysis {
    public final File heapDumpFile;
    public final long createdAtTimeMillis;
    public final long analysisDurationMillis;
    public final List<ApplicationLeak> applicationLeaks;
    public final List<LibraryLeak> libraryLeaks;

    public HeapAnalysisSuccess(File heapDumpFile, long createdAtTimeMillis, long analysisDurationMillis,
                                List<ApplicationLeak> applicationLeaks, List<LibraryLeak> libraryLeaks) {
        this.heapDumpFile = heapDumpFile;
        this.createdAtTimeMillis = createdAtTimeMillis;
        this.analysisDurationMillis = analysisDurationMillis;
        this.applicationLeaks = applicationLeaks;
        this.libraryLeaks = libraryLeaks;
    }

 
}

public sealed class Leak implements Serializable {
    public abstract String className;
    public abstract LeakTrace leakTrace;
    public abstract Integer retainedHeapSize;



    public String classSimpleName() {
        int separator = className.lastIndexOf('.');
        return (separator == -1) ? className : className.substring(separator + 1);
    }

    public abstract String createGroupHash();

    public static class LibraryLeak extends Leak {
        public final String className;
        public final LeakTrace leakTrace;
        public final Integer retainedHeapSize;
        public final ReferencePattern pattern;
        public final String description;

        public LibraryLeak(String className, LeakTrace leakTrace, Integer retainedHeapSize,
                           ReferencePattern pattern, String description) {
            this.className = className;
            this.leakTrace = leakTrace;
            this.retainedHeapSize = retainedHeapSize;
            this.pattern = pattern;
            this.description = description;
        }

        @Override
        public String createGroupHash() {
            return pattern.toString().createSHA1Hash();
        }
    }

    public static class ApplicationLeak extends Leak {
        public final String className;
        public final LeakTrace leakTrace;
        public final Integer retainedHeapSize;

        public ApplicationLeak(String className, LeakTrace leakTrace, Integer retainedHeapSize) {
            this.className = className;
            this.leakTrace = leakTrace;
            this.retainedHeapSize = retainedHeapSize;
        }

        @Override
        public String createGroupHash() {
            return leakTrace.leakCauses.stream()
                    .map(element -> element.className + element.reference.groupingName)
                    .collect(Collectors.joining())
                    .createSHA1Hash();
        }
    }
}