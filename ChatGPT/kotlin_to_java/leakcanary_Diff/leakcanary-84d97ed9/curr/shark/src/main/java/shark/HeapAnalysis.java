package shark;

import shark.internal.createSHA1Hash;
import java.io.File;
import java.io.Serializable;
import java.util.List;

public abstract class HeapAnalysis implements Serializable {

    public File heapDumpFile;

    public long createdAtTimeMillis;

    public long analysisDurationMillis;
}

public class HeapAnalysisFailure extends HeapAnalysis {

    private final File heapDumpFile;
    private final long createdAtTimeMillis;
    private final long analysisDurationMillis;
    private final HeapAnalysisException exception;

    public HeapAnalysisFailure(File heapDumpFile, long createdAtTimeMillis, long analysisDurationMillis, HeapAnalysisException exception) {
        this.heapDumpFile = heapDumpFile;
        this.createdAtTimeMillis = createdAtTimeMillis;
        this.analysisDurationMillis = analysisDurationMillis;
        this.exception = exception;
    }


}

public class HeapAnalysisSuccess extends HeapAnalysis {

    private final File heapDumpFile;
    private final long createdAtTimeMillis;
    private final long analysisDurationMillis;
    private final List<ApplicationLeak> applicationLeaks;
    private final List<LibraryLeak> libraryLeaks;

    public HeapAnalysisSuccess(File heapDumpFile, long createdAtTimeMillis, long analysisDurationMillis, List<ApplicationLeak> applicationLeaks, List<LibraryLeak> libraryLeaks) {
        this.heapDumpFile = heapDumpFile;
        this.createdAtTimeMillis = createdAtTimeMillis;
        this.analysisDurationMillis = analysisDurationMillis;
        this.applicationLeaks = applicationLeaks;
        this.libraryLeaks = libraryLeaks;
    }

}

public abstract class Leak implements Serializable {

    public final String className;
    public final LeakTrace leakTrace;
    public final Integer retainedHeapByteSize;
    public final String groupHash = createGroupHash();
    protected abstract String createGroupHash();
}

public class LibraryLeak extends Leak {

    private final String className;
    private final LeakTrace leakTrace;
    private final Integer retainedHeapByteSize;
    private final ReferencePattern pattern;
    private final String description;

    public LibraryLeak(String className, LeakTrace leakTrace, Integer retainedHeapByteSize, ReferencePattern pattern, String description) {
        this.className = className;
        this.leakTrace = leakTrace;
        this.retainedHeapByteSize = retainedHeapByteSize;
        this.pattern = pattern;
        this.description = description;
    }


}

public class ApplicationLeak extends Leak {

    private final String className;
    private final LeakTrace leakTrace;
    private final Integer retainedHeapByteSize;

    public ApplicationLeak(String className, LeakTrace leakTrace, Integer retainedHeapByteSize) {
        this.className = className;
        this.leakTrace = leakTrace;
        this.retainedHeapByteSize = retainedHeapByteSize;
    }



    @Override
    protected String createGroupHash() {
        return leakTrace.getLeakCauses().stream()
                .map(element -> {
                    String referenceName = element.getReference().getGroupingName();
                    return element.getClassName() + referenceName;
                })
                .reduce("", String::concat)
                .createSHA1Hash();
    }
}