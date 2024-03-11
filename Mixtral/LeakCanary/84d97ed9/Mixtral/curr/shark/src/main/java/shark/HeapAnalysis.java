

package shark;

import shark.internal.CreateSHA1Hash;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract sealed class HeapAnalysis implements Serializable {
    protected final File heapDumpFile;
    protected final long createdAtTimeMillis;
    protected final long analysisDurationMillis;

    public static class HeapAnalysisFailure extends HeapAnalysis {
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

    public static class HeapAnalysisSuccess extends HeapAnalysis {
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


        public List<Leak> getAllLeaks() {
            List<Leak> allLeaks = new ArrayList<>();
            allLeaks.addAll(applicationLeaks);
            allLeaks.addAll(libraryLeaks);
            return allLeaks;
        }
    }

    public abstract static class Leak implements Serializable {
        private final String className;
        private final LeakTrace leakTrace;
        private final Integer retainedHeapByteSize;

        public Leak(String className, LeakTrace leakTrace, Integer retainedHeapByteSize) {
            this.className = className;
            this.leakTrace = leakTrace;
            this.retainedHeapByteSize = retainedHeapByteSize;
        }



        public String getClassSimpleName() {
            int separator = className.lastIndexOf('.');
            return (separator == -1) ? className : className.substring(separator + 1);
        }



        protected abstract String createGroupHash();
    }

    public static class LibraryLeak extends Leak {
        private final ReferencePattern pattern;
        private final String description;

        public LibraryLeak(String className, LeakTrace leakTrace, Integer retainedHeapByteSize, ReferencePattern pattern, String description) {
            super(className, leakTrace, retainedHeapByteSize);
            this.pattern = pattern;
            this.description = description;
        }

    }

    public static class ApplicationLeak extends Leak {
        public ApplicationLeak(String className, LeakTrace leakTrace, Integer retainedHeapByteSize) {
            super(className, leakTrace, retainedHeapByteSize);
        }

        @Override
        protected String createGroupHash() {
            return leakTrace.getLeakCauses()
                    .stream()
                    .map(element -> element.getReference().getGroupingName() + element.getClassName())
                    .reduce("", String::concat)
                    .createSHA1Hash();
        }
    }
}