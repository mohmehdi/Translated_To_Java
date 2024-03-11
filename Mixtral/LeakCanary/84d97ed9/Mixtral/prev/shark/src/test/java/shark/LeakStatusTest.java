
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
import shark.LeakStatus;
import shark.LeakStatus.LeakTraceElement.LeakStatusAndReason;
import shark.ObjectInspector;
import shark.ObjectReporter;
import shark.RetainedHeap;
import shark.heapgraph.HeapGraph;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
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

        RetainedHeap analysis = checkForLeaks(hprofFile, heap -> listOf(ObjectInspectors.CLASS), heapGraph -> {});

        List<LeakStatus.Leak> applicationLeaks = analysis.getApplicationLeaks();
        LeakStatus.Leak leak = applicationLeaks.get(0);

        LeakStatusAndReason leakStatusAndReason = leak.getLeakTrace().getElements().get(0).getLeakStatusAndReason();
        Assertions.assertThat(leakStatusAndReason.getStatus()).isEqualTo(NOT_LEAKING);
    }

    @Test
    public void leakingInstanceLeaking() {
        writeSinglePathToInstance(hprofFile);

        RetainedHeap analysis = checkForLeaks(hprofFile, heap -> {}, heapGraph -> {});

        List<LeakStatus.Leak> applicationLeaks = analysis.getApplicationLeaks();
        LeakStatus.Leak leak = applicationLeaks.get(0);

        LeakStatusAndReason leakStatusAndReason = leak.getLeakTrace().getElements().get(leak.getLeakTrace().getElements().size() - 1).getLeakStatusAndReason();
        Assertions.assertThat(leakStatusAndReason.getStatus()).isEqualTo(LEAKING);
    }

    @Test
    public void defaultsToUnknown() {
        dumpHprof(hprofFile, heap -> {
            heap.clazz("GcRoot").staticField("staticField1", "Class1").instanceField("field1", "Leaking").watchedInstance();
        });

        RetainedHeap analysis = checkForLeaks(hprofFile, heap -> {}, heapGraph -> {});

        List<LeakStatus.Leak> applicationLeaks = analysis.getApplicationLeaks();
        LeakStatus.Leak leak = applicationLeaks.get(0);

        LeakStatusAndReason leakStatusAndReason = leak.getLeakTrace().getElements().get(1).getLeakStatusAndReason();
        Assertions.assertThat(leakStatusAndReason.getStatus()).isEqualTo(UNKNOWN);
    }

    @Test
    public void inspectorNotLeaking() {
        dumpHprof(hprofFile, heap -> {
            heap.clazz("GcRoot").staticField("staticField1", "Class1").instanceField("field1", "Leaking").watchedInstance();
        });

        RetainedHeap analysis = checkForLeaks(hprofFile, heap -> listOf(notLeakingInstance("Class1")), heapGraph -> {});

        List<LeakStatus.Leak> applicationLeaks = analysis.getApplicationLeaks();
        LeakStatus.Leak leak = applicationLeaks.get(0);

        LeakStatusAndReason leakStatusAndReason = leak.getLeakTrace().getElements().get(1).getLeakStatusAndReason();
        Assertions.assertThat(leakStatusAndReason.getStatus()).isEqualTo(NOT_LEAKING);
    }

    @Test
    public void inspectorLeaking() {
        dumpHprof(hprofFile, heap -> {
            heap.clazz("GcRoot").staticField("staticField1", "Class1").instanceField("field1", "Leaking").watchedInstance();
        });

        RetainedHeap analysis = checkForLeaks(hprofFile, heap -> listOf(leakingInstance("Class1")), heapGraph -> {});

        List<LeakStatus.Leak> applicationLeaks = analysis.getApplicationLeaks();
        LeakStatus.Leak leak = applicationLeaks.get(0);

        LeakStatusAndReason leakStatusAndReason = leak.getLeakTrace().getElements().get(1).getLeakStatusAndReason();
        Assertions.assertThat(leakStatusAndReason.getStatus()).isEqualTo(LEAKING);
    }

   @Test
void leakingWinsUnknown() {
  hprofFile.dump(() -> {
    "GcRoot".clazz(() -> {
      staticField.put("staticField1", "Class1").instance(() -> {
        field.put("field1", "Leaking").watchedInstance(() -> {});
      });
    });
  });

  HeapAnalysisSuccess analysis =
    hprofFile.checkForLeaks(
      Arrays.asList(leakingInstance("Class1"))
    );

  Leak leak = analysis.getApplicationLeaks().get(0);
  assertEquals(LEAKING, leak.getLeakTrace().getElements().get(1).getLeakStatusAndReason().getStatus());
}

@Test
void notLeakingWhenNextIsNotLeaking() {
  hprofFile.dump(() -> {
    "GcRoot".clazz(() -> {
      staticField.put("staticField1", "Class1").instance(() -> {
        field.put("field1", "Class2").instance(() -> {
          field.put("field2", "Class3").instance(() -> {
            field.put("field3", "Leaking").watchedInstance(() -> {});
          });
        });
      });
    });
  });

  HeapAnalysisSuccess analysis =
    hprofFile.checkForLeaks(
      Arrays.asList(notLeakingInstance("Class3"))
    );

  Leak leak = analysis.getApplicationLeaks().get(0);
  assertEquals(NOT_LEAKING, leak.getLeakTrace().getElements().get(1).getLeakStatusAndReason().getStatus());
}

@Test
void leakingWhenPreviousIsLeaking() {
  hprofFile.dump(() -> {
    "GcRoot".clazz(() -> {
      staticField.put("staticField1", "Class1").instance(() -> {
        field.put("field1", "Class2").instance(() -> {
          field.put("field2", "Class3").instance(() -> {
            field.put("field3", "Leaking").watchedInstance(() -> {});
          });
        });
      });
    });
  });

  HeapAnalysisSuccess analysis =
    hprofFile.checkForLeaks(
      Arrays.asList(leakingInstance("Class1"))
    );

  Leak leak = analysis.getApplicationLeaks().get(0);
  assertEquals(2, leak.getLeakTrace().getElements().size());
  assertEquals(LEAKING, leak.getLeakTrace().getElements().get(1).getLeakStatusAndReason().getStatus());
}

@Test
void middleUnknown() {
  hprofFile.dump(() -> {
    "GcRoot".clazz(() -> {
      staticField.put("staticField1", "Class1").instance(() -> {
        field.put("field1", "Class2").instance(() -> {
          field.put("field2", "Class3").instance(() -> {
            field.put("field3", "Leaking").watchedInstance(() -> {});
          });
        });
      });
    });
  });

  HeapAnalysisSuccess analysis =
    hprofFile.checkForLeaks(
      Arrays.asList(
        notLeakingInstance("Class1"), leakingInstance("Class3")
      )
    );

  Leak leak = analysis.getApplicationLeaks().get(0);
  assertEquals(UNKNOWN, leak.getLeakTrace().getElements().get(2).getLeakStatusAndReason().getStatus());
}

@Test
void gcRootClassNotLeakingConflictingWithInspector() {
  hprofFile.writeSinglePathToInstance();

  HeapAnalysisSuccess analysis =
    hprofFile.checkForLeaks(
      Arrays.asList(leakingClass("GcRoot"), ObjectInspectors.CLASS)
    );

  Leak leak = analysis.getApplicationLeaks().get(0);

  assertEquals(NOT_LEAKING, leak.getLeakTrace().getElements().get(0).getLeakStatusAndReason().getStatus());
  assertEquals(
    "a class is never leaking. Conflicts with GcRoot is leaking",
    leak.getLeakTrace().getElements().get(0).getLeakStatusAndReason().getReason()
  );
}

@Test
void gcRootClassNotLeakingAgreesWithInspector() {
  hprofFile.writeSinglePathToInstance();

  HeapAnalysisSuccess analysis =
    hprofFile.checkForLeaks(
      Arrays.asList(notLeakingClass("GcRoot"), ObjectInspectors.CLASS)
    );

  Leak leak = analysis.getApplicationLeaks().get(0);

  assertEquals(NOT_LEAKING, leak.getLeakTrace().getElements().get(0).getLeakStatusAndReason().getStatus());
  assertEquals(
    "GcRoot is not leaking and a class is never leaking",
    leak.getLeakTrace().getElements().get(0).getLeakStatusAndReason().getReason()
  );
}

@Test
void leakingInstanceLeakingConflictingWithInspector() {
  hprofFile.writeSinglePathToInstance();
  HeapAnalysisSuccess analysis =
    hprofFile.checkForLeaks(
      Arrays.asList(notLeakingInstance("Leaking"))
    );

  Leak leak = analysis.getApplicationLeaks().get(0);
  assertEquals(NOT_LEAKING, leak.getLeakTrace().getElements().get(leak.getLeakTrace().getElements().size() - 1).getLeakStatusAndReason().getStatus());
  assertEquals(
    "Leaking is not leaking. Conflicts with ObjectWatcher was watching this",
    leak.getLeakTrace().getElements().get(leak.getLeakTrace().getElements().size() - 1).getLeakStatusAndReason().getReason()
  );
}

@Test
void leakingInstanceLeakingAgreesWithInspector() {
  hprofFile.writeSinglePathToInstance();
  HeapAnalysisSuccess analysis =
    hprofFile.checkForLeaks(
      Arrays.asList(leakingInstance("Leaking"))
    );

  Leak leak = analysis.getApplicationLeaks().get(0);
  assertEquals(LEAKING, leak.getLeakTrace().getElements().get(leak.getLeakTrace().getElements().size() - 1).getLeakStatusAndReason().getStatus());
  assertEquals(
    "Leaking is leaking and ObjectWatcher was watching this",
    leak.getLeakTrace().getElements().get(leak.getLeakTrace().getElements().size() - 1).getLeakStatusAndReason().getReason()
  );
}

@Test
void conflictNotLeakingWins() {
  hprofFile.dump(() -> {
    "GcRoot".clazz(() -> {
      staticField.put("staticField1", "Class1").instance(() -> {
        field.put("field1", "Leaking").watchedInstance(() -> {});
      });
    });
  });

  HeapAnalysisSuccess analysis =
    hprofFile.checkForLeaks(
      Arrays.asList(
        notLeakingInstance("Class1"), leakingInstance("Class1")
      )
    );

  Leak leak = analysis.getApplicationLeaks().get(0);
  assertEquals(NOT_LEAKING, leak.getLeakTrace().getElements().get(1).getLeakStatusAndReason().getStatus());
  assertEquals(
    "Class1 is not leaking. Conflicts with Class1 is leaking",
    leak.getLeakTrace().getElements().get(1).getLeakStatusAndReason().getReason()
  );
}

@Test
void twoInspectorsAgreeNotLeaking() {
  hprofFile.dump(() -> {
    "GcRoot".clazz(() -> {
      staticField.put("staticField1", "Class1").instance(() -> {
        field.put("field1", "Leaking").watchedInstance(() -> {});
      });
    });
  });

  HeapAnalysisSuccess analysis =
    hprofFile.checkForLeaks(
      Arrays.asList(
        notLeakingInstance("Class1"), notLeakingInstance("Class1")
      )
    );

  Leak leak = analysis.getApplicationLeaks().get(0);
  assertEquals(NOT_LEAKING, leak.getLeakTrace().getElements().get(1).getLeakStatusAndReason().getStatus());
  assertEquals(
    "Class1 is not leaking and Class1 is not leaking",
    leak.getLeakTrace().getElements().get(1).getLeakStatusAndReason().getReason()
  );
}

@Test
void twoInspectorsAgreeLeaking() {
  hprofFile.dump(() -> {
    "GcRoot".clazz(() -> {
      staticField.put("staticField1", "Class1").instance(() -> {
        field.put("field1", "Leaking").watchedInstance(() -> {});
      });
    });
  });

  HeapAnalysisSuccess analysis =
    hprofFile.checkForLeaks(
      Arrays.asList(leakingInstance("Class1"), leakingInstance("Class1"))
    );

  Leak leak = analysis.getApplicationLeaks().get(0);
  assertEquals(LEAKING, leak.getLeakTrace().getElements().get(1).getLeakStatusAndReason().getStatus());
  assertEquals(
    "Class1 is leaking and Class1 is leaking",
    leak.getLeakTrace().getElements().get(1).getLeakStatusAndReason().getReason()
  );
}

@Test
void leakCausesAreLastNotLeakingAndUnknown() {
  hprofFile.dump(() -> {
    "GcRoot".clazz(() -> {
      staticField.put("staticField1", "Class1").instance(() -> {
        field.put("field1", "Class2").instance(() -> {
          field.put("field2", "Class3").instance(() -> {
            field.put("field3", "Leaking").watchedInstance(() -> {});
          });
        });
      });
    });
  });

  HeapAnalysisSuccess analysis =
    hprofFile.checkForLeaks(
      Arrays.asList(
        notLeakingInstance("Class1"), leakingInstance("Class3")
      )
    );

  Leak leak = analysis.getApplicationLeaks().get(0);
  assertFalse(leak.getLeakTrace().elementMayBeLeakCause(0));
  assertTrue(leak.getLeakTrace().elementMayBeLeakCause(1));
  assertTrue(leak.getLeakTrace().elementMayBeLeakCause(2));
  assertFalse(leak.getLeakTrace().elementMayBeLeakCause(3));
}

@Test
void sameLeakTraceSameGroup() {
  hprofFile.dump(() -> {
    "GcRoot".clazz(() -> {
      staticField.put("staticField1", "Class1").instance(() -> {
        field.put("field1", "Class2").instance(() -> {
          field.put("field2", "Class3").instance(() -> {
            field.put("field3", "Leaking").watchedInstance(() -> {});
          });
        });
      });
    });
  });
  String hash1 = computeGroupHash("Class1", "Class3");
  hprofFile.dump(() -> {
    "GcRoot".clazz(() -> {
      staticField.put("staticField1", "Class1").instance(() -> {
        field.put("field1", "Class2").instance(() -> {
          field.put("field2", "Class3").instance(() -> {
            field.put("field3", "Leaking").watchedInstance(() -> {});
          });
        });
      });
    });
  });
  String hash2 = computeGroupHash("Class1", "Class3");
  assertEquals(hash1, hash2);
}

@Test
void differentLeakTraceDifferentGroup() {
  hprofFile.dump(() -> {
    "GcRoot".clazz(() -> {
      staticField.put("staticField1", "Class1").instance(() -> {
        field.put("field1a", "Class2").instance(() -> {
          field.put("field2", "Class3").instance(() -> {
            field.put("field3", "Leaking").watchedInstance(() -> {});
          });
        });
      });
    });
  });
  String hash1 = computeGroupHash("Class1", "Class3");
  hprofFile.dump(() -> {
    "GcRoot".clazz(() -> {
      staticField.put("staticField1", "Class1").instance(() -> {
        field.put("field1b", "Class2").instance(() -> {
          field.put("field2", "Class3").instance(() -> {
            field.put("field3", "Leaking").watchedInstance(() -> {});
          });
        });
      });
    });
  });
  String hash2 = computeGroupHash("Class1", "Class3");
  assertNotEquals(hash1, hash2);
}

@Test
void sameCausesSameGroup() {
  hprofFile.dump(() -> {
    "GcRoot".clazz(() -> {
      staticField.put("staticField1", "Class1").instance(() -> {
        field.put("field1", "Class2").instance(() -> {
          field.put("field2", "Class3").instance(() -> {
            field.put("field3a", "Leaking").watchedInstance(() -> {});
          });
        });
      });
    });
  });
  String hash1 = computeGroupHash("Class1", "Class3");

  hprofFile.dump(() -> {
    "GcRoot".clazz(() -> {
      staticField.put("staticField1", "Class1").instance(() -> {
        field.put("field1", "Class2").instance(() -> {
          field.put("field2", "Class3").instance(() -> {
            field.put("field3b", "Leaking").watchedInstance(() -> {});
          });
        });
      });
    });
  });
  String hash2 = computeGroupHash("Class1", "Class3");
  assertEquals(hash1, hash2);
}

    private static ObjectInspector notLeakingInstance(final String className) {
        return new ObjectInspector() {
            @Override
            public void inspect(HeapGraph graph, ObjectReporter reporter) {
                Object heapObject = reporter.getHeapObject();
                if (heapObject instanceof HeapInstance && ((HeapInstance) heapObject).getInstanceClassName().equals(className)) {
                    reporter.reportNotLeaking("" + className + " is not leaking");
                }
            }
        };
    }

    private static ObjectInspector leakingInstance(final String className) {
        return new ObjectInspector() {
            @Override
            public void inspect(HeapGraph graph, ObjectReporter reporter) {
                Object heapObject = reporter.getHeapObject();
                if (heapObject instanceof HeapInstance && ((HeapInstance) heapObject).getInstanceClassName().equals(className)) {
                    reporter.reportLeaking("" + className + " is leaking");
                }
            }
        };
    }
private ObjectInspector notLeakingClass(String className) {
    return new ObjectInspector() {
      @Override
      public void inspect(HeapGraph graph, ObjectReporter reporter) {
        ObjectRecord record = reporter.getHeapObject();
        if (record instanceof HeapClass && record.getName().equals(className)) {
          reporter.reportNotLeaking(className + " is not leaking");
        }
      }
    };
  }

  private ObjectInspector leakingClass(String className) {
    return new ObjectInspector() {
      @Override
      public void inspect(HeapGraph graph, ObjectReporter reporter) {
        ObjectRecord record = reporter.getHeapObject();
        if (record instanceof HeapClass && record.getName().equals(className)) {
          reporter.reportLeaking(className + " is leaking");
        }
      }
    };
  }

  private String computeGroupHash(String notLeaking, String leaking) {
    HeapAnalysisSuccess analysis = hprofFile.checkForLeaks(
        Arrays.asList(notLeakingInstance(notLeaking), leakingInstance(leaking))
    );
    if (analysis.getApplicationLeaks().size() != 1) {
      throw new RuntimeException("Expecting 1 retained instance in " + analysis.getApplicationLeaks());
    }
    Leak leak = analysis.getApplicationLeaks().get(0);
    return leak.getGroupHash();
  }
}