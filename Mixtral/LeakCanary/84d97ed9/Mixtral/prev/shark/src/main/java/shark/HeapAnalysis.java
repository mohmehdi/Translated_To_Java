

package shark;

import shark.Leak;
import shark.LeakTrace;
import shark.internal.CreateSHA1Hash;
import shark.internal.CreateSHA1HashImpl;
import shark.Leak.ApplicationLeak;
import shark.Leak.LibraryLeak;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract sealed class HeapAnalysis implements Serializable {
  public abstract File heapDumpFile;
  public abstract long createdAtTimeMillis;
  
  public abstract long analysisDurationMillis;

  public static class HeapAnalysisFailure extends HeapAnalysis {
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

  public static class HeapAnalysisSuccess extends HeapAnalysis {
    public final File heapDumpFile;
    public final long createdAtTimeMillis;
    public final long analysisDurationMillis;
    public final List<ApplicationLeak> applicationLeaks;
    public final List<LibraryLeak> libraryLeaks;

    public HeapAnalysisSuccess(File heapDumpFile, long createdAtTimeMillis, long analysisDurationMillis, List<ApplicationLeak> applicationLeaks, List<LibraryLeak> libraryLeaks) {
      this.heapDumpFile = heapDumpFile;
      this.createdAtTimeMillis = createdAtTimeMillis;
      this.analysisDurationMillis = analysisDurationMillis;
      this.applicationLeaks = applicationLeaks;
      this.libraryLeaks = libraryLeaks;
    }

  }

  public abstract static class Leak implements Serializable {
    public abstract String className;

    public abstract LeakTrace leakTrace;

    public abstract Integer retainedHeapSize;

    public String groupHash;

    public String classSimpleName;

    public Leak() {
      int separator = className.lastIndexOf('.');
      if (separator == -1) {
        classSimpleName = className;
      } else {
        classSimpleName = className.substring(separator + 1);
      }
      groupHash = createGroupHash();
    }

    public abstract String createGroupHash();

    public static class LibraryLeak extends Leak {
      public final String pattern;
      public final String description;

      public LibraryLeak(String className, LeakTrace leakTrace, Integer retainedHeapSize, String pattern, String description) {
        super();
        this.className = className;
        this.leakTrace = leakTrace;
        this.retainedHeapSize = retainedHeapSize;
        this.pattern = pattern;
        this.description = description;
      }

      @Override
      public String createGroupHash() {
        return CreateSHA1HashImpl.createSHA1Hash(pattern);
      }
    }

    public static class ApplicationLeak extends Leak {
      public final List<LeakCause> leakCauses;

      public ApplicationLeak(String className, LeakTrace leakTrace, Integer retainedHeapSize, List<LeakCause> leakCauses) {
        super();
        this.className = className;
        this.leakTrace = leakTrace;
        this.retainedHeapSize = retainedHeapSize;
        this.leakCauses = leakCauses;
      }

      @Override
      public String createGroupHash() {
        StringBuilder stringBuilder = new StringBuilder();
        for (LeakCause leakCause : leakCauses) {
          String referenceName = leakCause.reference.groupingName;
          stringBuilder.append(leakCause.className).append(referenceName);
        }
        return CreateSHA1HashImpl.createSHA1Hash(stringBuilder.toString());
      }
    }
  }

}


