

package shark;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import shark.HeapObject.HeapClass;
import shark.HeapObject.HeapInstance;
import shark.LeakNodeStatus.LEAKING;
import shark.LeakNodeStatus.NOT_LEAKING;
import shark.LeakNodeStatus.UNKNOWN;
import shark.LeakStatusReporter.ObjectInspector;
import shark.LeakStatusReporter.ObjectReporter;
import shark.LeakStatusReporter.HeapAnalysisSuccess;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LeakStatusTest {

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();
  private File hprofFile;

  @Before
  public void setUp() throws IOException {
    hprofFile = testFolder.newFile("temp.hprof");
  }

  @Test
  public void gcRootClassNotLeaking() {
    writeSinglePathToInstance(hprofFile);

    HeapAnalysisSuccess analysis = checkForLeaks(
        hprofFile,
        Arrays.asList(new ObjectInspector[] { ObjectInspectors.CLASS })
    );

    LeakTrace.LeakTraceElement leak = analysis.applicationLeaks.get(0).leakTrace.elements.get(0);

    Assertions.assertThat(leak.leakStatusAndReason.status).isEqualTo(NOT_LEAKING);
  }

  @Test
  public void leakingInstanceLeaking() {
    writeSinglePathToInstance(hprofFile);

    HeapAnalysisSuccess analysis = checkForLeaks(hprofFile);

    LeakTrace.LeakTraceElement leak = analysis.applicationLeaks.get(0).leakTrace.elements.get(0);

    Assertions.assertThat(leak.leakStatusAndReason.status).isEqualTo(LEAKING);
  }

  @Test
  public void defaultsToUnknown() {
    dumpHprofFile(hprofFile, "GcRoot", "clazz", "staticField", "staticField1", "Class1", "instance", "field", "field1", "Leaking", "watchedInstance");

    HeapAnalysisSuccess analysis = checkForLeaks(hprofFile);

    LeakTrace.LeakTraceElement leak = analysis.applicationLeaks.get(0).leakTrace.elements.get(1);

    Assertions.assertThat(leak.leakStatusAndReason.status).isEqualTo(UNKNOWN);
  }

  @Test
  public void inspectorNotLeaking() {
    dumpHprofFile(hprofFile, "GcRoot", "clazz", "staticField", "staticField1", "Class1", "instance", "field", "field1", "Leaking", "watchedInstance");

    HeapAnalysisSuccess analysis = checkForLeaks(
        hprofFile,
        Arrays.asList(notLeakingInstance("Class1"))
    );

    LeakTrace.LeakTraceElement leak = analysis.applicationLeaks.get(0).leakTrace.elements.get(1);

    Assertions.assertThat(leak.leakStatusAndReason.status).isEqualTo(NOT_LEAKING);
  }

  @Test
  public void inspectorLeaking() {
    dumpHprofFile(hprofFile, "GcRoot", "clazz", "staticField", "staticField1", "Class1", "instance", "field", "field1", "Leaking", "watchedInstance");

    HeapAnalysisSuccess analysis = checkForLeaks(
        hprofFile,
        Arrays.asList(leakingInstance("Class1"))
    );

    LeakTrace.LeakTraceElement leak = analysis.applicationLeaks.get(0).leakTrace.elements.get(1);

    Assertions.assertThat(leak.leakStatusAndReason.status).isEqualTo(LEAKING);
  }

  @Test
  public void leakingWinsUnknown() {
    dumpHprofFile(hprofFile, "GcRoot", "clazz", "staticField", "staticField1", "Class1", "instance", "field", "field1", "Leaking", "watchedInstance");

    HeapAnalysisSuccess analysis = checkForLeaks(
        hprofFile,
        Arrays.asList(leakingInstance("Class1"))
    );

    LeakTrace.LeakTraceElement leak = analysis.applicationLeaks.get(0).leakTrace.elements.get(1);

    Assertions.assertThat(leak.leakStatusAndReason.status).isEqualTo(LEAKING);
  }

  @Test
  public void notLeakingWhenNextIsNotLeaking() {
    dumpHprofFile(hprofFile,
        "GcRoot", "clazz",
        "staticField", "staticField1", "Class1",
        "instance", "field", "field1", "Class2",
        "instance", "field", "field2", "Class3",
        "instance", "field", "field3", "Leaking",
        "watchedInstance"
    );

    HeapAnalysisSuccess analysis = checkForLeaks(
        hprofFile,
        Arrays.asList(notLeakingInstance("Class3"))
    );

    LeakTrace.LeakTraceElement leak = analysis.applicationLeaks.get(0).leakTrace.elements.get(1);

    Assertions.assertThat(leak.leakStatusAndReason.status).isEqualTo(NOT_LEAKING);
  }

  @Test
  public void leakingWhenPreviousIsLeaking() {
    dumpHprofFile(hprofFile,
        "GcRoot", "clazz",
        "staticField", "staticField1", "Class1",
        "instance", "field", "field1", "Class2",
        "instance", "field", "field2", "Class3",
        "instance", "field", "field3", "Leaking",
        "watchedInstance"
    );

    HeapAnalysisSuccess analysis = checkForLeaks(
        hprofFile,
        Arrays.asList(leakingInstance("Class1"))
    );

    LeakTrace leak = analysis.applicationLeaks.get(0).leakTrace;

    Assertions.assertThat(leak.elements).hasSize(2);
    Assertions.assertThat(leak.elements.get(1).leakStatusAndReason.status).isEqualTo(LEAKING);
  }

  @Test
  public void middleUnknown() {
    dumpHprofFile(hprofFile,
        "GcRoot", "clazz",
        "staticField", "staticField1", "Class1",
        "instance", "field", "field1", "Class2",
        "instance", "field", "field2", "Class3",
        "instance", "field", "field3", "Leaking",
        "watchedInstance"
    );

    HeapAnalysisSuccess analysis = checkForLeaks(
        hprofFile,
        Arrays.asList(
            notLeakingInstance("Class1"), leakingInstance("Class3")
        )
    );

    LeakTrace.LeakTraceElement leak = analysis.applicationLeaks.get(0).leakTrace.elements.get(2);

    Assertions.assertThat(leak.leakStatusAndReason.status).isEqualTo(UNKNOWN);
  }

  @Test
  public void gcRootClassNotLeakingConflictingWithInspector() {
    writeSinglePathToInstance(hprofFile);

    HeapAnalysisSuccess analysis = checkForLeaks(
        hprofFile,
        Arrays.asList(leakingClass("GcRoot"), ObjectInspectors.CLASS)
    );

    LeakTrace.LeakTraceElement leak = analysis.applicationLeaks.get(0).leakTrace.elements.get(0);

    Assertions.assertThat(leak.leakStatusAndReason.status).isEqualTo(NOT_LEAKING);
    Assertions.assertThat(leak.leakStatusAndReason.reason).isEqualTo(
        "a class is never leaking. Conflicts with GcRoot is leaking"
    );
  }

  @Test
  public void gcRootClassNotLeakingAgreesWithInspector() {
    writeSinglePathToInstance(hprofFile);

    HeapAnalysisSuccess analysis = checkForLeaks(
        hprofFile,
        Arrays.asList(notLeakingClass("GcRoot"), ObjectInspectors.CLASS)
    );

    Assertions.assertThat(analysis).isNotNull();

    LeakTrace.LeakTraceElement leak = analysis.applicationLeaks.get(0).leakTrace.elements.get(0);

    Assertions.assertThat(leak.leakStatusAndReason.status).isEqualTo(NOT_LEAKING);
    Assertions.assertThat(leak.leakStatusAndReason.reason).isEqualTo(
        "GcRoot is not leaking and a class is never leaking"
    );
  }

  @Test
  public void leakingInstanceLeakingConflictingWithInspector() {
    writeSinglePathToInstance(hprofFile);
    HeapAnalysisSuccess analysis = checkForLeaks(
        hprofFile,
        Arrays.asList(notLeakingInstance("Leaking"))
    );

    LeakTrace.LeakTraceElement leak = analysis.applicationLeaks.get(0).leakTrace.elements.get(0);

    Assertions.assertThat(leak.leakStatusAndReason.status).isEqualTo(NOT_LEAKING);
    Assertions.assertThat(leak.leakStatusAndReason.reason).isEqualTo(
        "Leaking is not leaking. Conflicts with ObjectWatcher was watching this"
    );
  }

  @Test
  public void leakingInstanceLeakingAgreesWithInspector() {
    writeSinglePathToInstance(hprofFile);
    HeapAnalysisSuccess analysis = checkForLeaks(
        hprofFile,
        Arrays.asList(leakingInstance("Leaking"))
    );

    LeakTrace.LeakTraceElement leak = analysis.applicationLeaks.get(0).leakTrace.elements.get(0);

    Assertions.assertThat(leak.leakStatusAndReason.status).isEqualTo(LEAKING);
    Assertions.assertThat(leak.leakStatusAndReason.reason).isEqualTo(
        "Leaking is leaking and ObjectWatcher was watching this"
    );
  }

  @Test
  public void conflictNotLeakingWins() {
    dumpHprofFile(hprofFile,
        "GcRoot", "clazz",
        "staticField", "staticField1", "Class1",
        "instance", "field", "field1", "Leaking",
        "watchedInstance"
    );

    HeapAnalysisSuccess analysis = checkForLeaks(
        hprofFile,
        Arrays.asList(
            notLeakingInstance("Class1"), leakingInstance("Class1")
        )
    );

    LeakTrace.LeakTraceElement leak = analysis.applicationLeaks.get(0).leakTrace.elements.get(1);

    Assertions.assertThat(leak.leakStatusAndReason.status).isEqualTo(NOT_LEAKING);
    Assertions.assertThat(leak.leakStatusAndReason.reason).isEqualTo(
        "Class1 is not leaking. Conflicts with Class1 is leaking"
    );
  }

  @Test
  public void twoInspectorsAgreeNotLeaking() {
    dumpHprofFile(hprofFile,
        "GcRoot", "clazz",
        "staticField", "staticField1", "Class1",
        "instance", "field", "field1", "Leaking",
        "watchedInstance"
    );

    HeapAnalysisSuccess analysis = checkForLeaks(
        hprofFile,
        Arrays.asList(
            notLeakingInstance("Class1"), notLeakingInstance("Class1")
        )
    );

    LeakTrace.LeakTraceElement leak = analysis.applicationLeaks.get(0).leakTrace.elements.get(1);

    Assertions.assertThat(leak.leakStatusAndReason.status).isEqualTo(NOT_LEAKING);
    Assertions.assertThat(leak.leakStatusAndReason.reason).isEqualTo(
        "Class1 is not leaking and Class1 is not leaking"
    );
  }

  @Test
  public void twoInspectorsAgreeLeaking() {
    dumpHprofFile(hprofFile,
        "GcRoot", "clazz",
        "staticField", "staticField1", "Class1",
        "instance", "field", "field1", "Leaking",
        "watchedInstance"
    );

    HeapAnalysisSuccess analysis = checkForLeaks(
        hprofFile,
        Arrays.asList(leakingInstance("Class1"), leakingInstance("Class1"))
    );

    LeakTrace.LeakTraceElement leak = analysis.applicationLeaks.get(0).leakTrace.elements.get(1);

    Assertions.assertThat(leak.leakStatusAndReason.status).isEqualTo(LEAKING);
    Assertions.assertThat(leak.leakStatusAndReason.reason).isEqualTo(
        "Class1 is leaking and Class1 is leaking"
    );
  }

  @Test
  public void leakCausesAreLastNotLeakingAndUnknown() {
    dumpHprofFile(hprofFile,
        "GcRoot", "clazz",
        "staticField", "staticField1", "Class1",
        "instance", "field", "field1", "Class2",
        "instance", "field", "field2", "Class3",
        "instance", "field", "field3", "Leaking",
        "watchedInstance"
    );

    HeapAnalysisSuccess analysis = checkForLeaks(
        hprofFile,
        Arrays.asList(
            notLeakingInstance("Class1"), leakingInstance("Class3")
        )
    );

    LeakTrace leak = analysis.applicationLeaks.get(0).leakTrace;

    Assertions.assertThat(leak.elementMayBeLeakCause(0)).isFalse();
    Assertions.assertThat(leak.elementMayBeLeakCause(1)).isTrue();
    Assertions.assertThat(leak.elementMayBeLeakCause(2)).isTrue();
    Assertions.assertThat(leak.elementMayBeLeakCause(3)).isFalse();
  }

  @Test
  public void sameLeakTraceSameGroup() {
    dumpHprofFile(hprofFile,
        "GcRoot", "clazz",
        "staticField", "staticField1", "Class1",
        "instance", "field", "field1", "Class2",
        "instance", "field", "field2", "Class3",
        "instance", "field", "field3", "Leaking",
        "watchedInstance"
    );
    String hash1 = computeGroupHash(notLeaking("Class1"), leaking("Class3"));
    dumpHprofFile(hprofFile,
        "GcRoot", "clazz",
        "staticField", "staticField1", "Class1",
        "instance", "field", "field1", "Class2",
        "instance", "field", "field2", "Class3",
        "instance", "field", "field3", "Leaking",
        "watchedInstance"
    );
    String hash2 = computeGroupHash(notLeaking("Class1"), leaking("Class3"));
    Assertions.assertThat(hash1).isEqualTo(hash2);
  }

  @Test
  public void differentLeakTraceDifferentGroup() {
    dumpHprofFile(hprofFile,
        "GcRoot", "clazz",
        "staticField", "staticField1", "Class1",
        "instance", "field", "field1a", "Class2",
        "instance", "field", "field2", "Class3",
        "instance", "field", "field3", "Leaking",
        "watchedInstance"
    );
    String hash1 = computeGroupHash(notLeaking("Class1"), leaking("Class3"));
    dumpHprofFile(hprofFile,
        "GcRoot", "clazz",
        "staticField", "staticField1", "Class1",
        "instance", "field", "field1b", "Class2",
        "instance", "field", "field2", "Class3",
        "instance", "field", "field3", "Leaking",
        "watchedInstance"
    );
    String hash2 = computeGroupHash(notLeaking("Class1"), leaking("Class3"));
    Assertions.assertThat(hash1).isNotEqualTo(hash2);
  }

  @Test
  public void sameCausesSameGroup() {
    dumpHprofFile(hprofFile,
        "GcRoot", "clazz",
        "staticField", "staticField1", "Class1",
        "instance", "field", "field1", "Class2",
        "instance", "field", "field2a", "Class3",
        "instance", "field", "field3", "Leaking",
        "watchedInstance"
    );
    String hash1 = computeGroupHash(notLeaking("Class1"), leaking("Class3"));

    dumpHprofFile(hprofFile,
        "GcRoot", "clazz",
        "staticField", "staticField1", "Class1",
        "instance", "field", "field1", "Class2",
        "instance", "field", "field2b", "Class3",
        "instance", "field", "field3", "Leaking",
        "watchedInstance"
    );
    String hash2 = computeGroupHash(notLeaking("Class1"), leaking("Class3"));
    Assertions.assertThat(hash1).isEqualTo(hash2);
  }

private ObjectInspector notLeakingInstance(String className) {
    return new ObjectInspector() {
      @Override
      public void inspect(ObjectReporter reporter) {
        Object record = reporter.getHeapObject();
        if (record instanceof HeapInstance && ((HeapInstance) record).getInstanceClassName().equals(className)) {
          reporter.reportNotLeaking(className + " is not leaking");
        }
      }
    };
  }

  private ObjectInspector leakingInstance(String className) {
    return new ObjectInspector() {
      @Override
      public void inspect(ObjectReporter reporter) {
        Object record = reporter.getHeapObject();
        if (record instanceof HeapInstance && ((HeapInstance) record).getInstanceClassName().equals(className)) {
          reporter.reportLeaking(className + " is leaking");
        }
      }
    };
  }

  private ObjectInspector notLeakingClass(String className) {
    return new ObjectInspector() {
      @Override
      public void inspect(ObjectReporter reporter) {
        Object record = reporter.getHeapObject();
        if (record instanceof HeapClass && ((HeapClass) record).getName().equals(className)) {
          reporter.reportNotLeaking(className + " is not leaking");
        }
      }
    };
  }

  private ObjectInspector leakingClass(String className) {
    return new ObjectInspector() {
      @Override
      public void inspect(ObjectReporter reporter) {
        Object record = reporter.getHeapObject();
        if (record instanceof HeapClass && ((HeapClass) record).getName().equals(className)) {
          reporter.reportLeaking(className + " is leaking");
        }
      }
    };
  }

  private static String computeGroupHash(String notLeaking, String leaking) {
    HeapAnalysisSuccess analysis = checkForLeaks(
        hprofFile,
        Arrays.asList(notLeakingInstance(notLeaking), leakingInstance(leaking))
    );
    require(analysis.getApplicationLeaks().size() == 1, "Expecting 1 retained instance in " + analysis.getApplicationLeaks());
    LeakTrace leak = analysis.getApplicationLeaks().get(0).getLeakTrace();
    return leak.getGroupHash();
  }

}