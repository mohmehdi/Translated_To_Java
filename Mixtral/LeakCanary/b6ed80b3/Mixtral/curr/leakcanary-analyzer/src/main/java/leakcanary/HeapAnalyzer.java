

package leakcanary;

import com.android.tools.perflib.captures.MemoryMappedFileBuffer;
import com.squareup.haha.perflib.ArrayInstance;
import com.squareup.haha.perflib.ClassInstance;
import com.squareup.haha.perflib.ClassObj;
import com.squareup.haha.perflib.Instance;
import com.squareup.haha.perflib.RootObj;
import com.squareup.haha.perflib.Snapshot;
import com.squareup.haha.perflib.Type;
import gnu.trove.THashMap;
import gnu.trove.TObjectProcedure;
import leakcanary.AnalyzerProgressListener.Step;
import leakcanary.HahaHelper;
import leakcanary.HahaHelper.asString;
import leakcanary.HahaHelper.asStringArray;
import leakcanary.HahaHelper.classInstanceValues;
import leakcanary.HahaHelper.extendsThread;
import leakcanary.HahaHelper.fieldValue;
import leakcanary.HahaHelper.staticFieldValue;
import leakcanary.HahaHelper.threadName;
import leakcanary.HahaHelper.valueAsString;
import leakcanary.LeakTraceElement.Holder;
import leakcanary.LeakTraceElement.Type;
import leakcanary.Reachability.Inspector;
import leakcanary.Reachability.Status;
import leakcanary.internal.HeapDumpMemoryStore;
import org.jetbrains.annotations.TestOnly;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HeapAnalyzer {

    private final ExcludedRefs excludedRefs;
    private final AnalyzerProgressListener listener;
    private final List<Class<? extends Inspector>> reachabilityInspectorClasses;
    private final String keyedWeakReferenceClassName;
    private final String heapDumpMemoryStoreClassName;
    private final List<Inspector> reachabilityInspectors;

    public HeapAnalyzer(ExcludedRefs excludedRefs, AnalyzerProgressListener listener, List<Class<? extends Inspector>> reachabilityInspectorClasses, String keyedWeakReferenceClassName, String heapDumpMemoryStoreClassName) {
        this.excludedRefs = excludedRefs;
        this.listener = listener;
        this.reachabilityInspectorClasses = reachabilityInspectorClasses;
        this.keyedWeakReferenceClassName = keyedWeakReferenceClassName;
        this.heapDumpMemoryStoreClassName = heapDumpMemoryStoreClassName;
        this.reachabilityInspectors = new ArrayList<>();

        for (Class<? extends Inspector> reachabilityInspectorClass : reachabilityInspectorClasses) {
            try {
                Constructor<? extends Inspector> defaultConstructor = reachabilityInspectorClass.getDeclaredConstructor();
                reachabilityInspectors.add(defaultConstructor.newInstance());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @TestOnly
    @Deprecated(
            "Use {@link #checkForLeaks(File, boolean)} instead. We're keeping this only because\n" +
                    "    our tests currently run with older heapdumps."
    )
    public AnalysisResult checkForLeak(
            File heapDumpFile, String referenceKey, boolean computeRetainedSize) {
        long analysisStartNanoTime = System.nanoTime();

        if (!heapDumpFile.exists()) {
            IllegalArgumentException exception = new IllegalArgumentException("File does not exist: " + heapDumpFile);
            return AnalysisResult.failure(exception, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime));
        }

        try {
            listener.onProgressUpdate(Step.READING_HEAP_DUMP_FILE);
            MemoryMappedFileBuffer buffer = new MemoryMappedFileBuffer(heapDumpFile);
            listener.onProgressUpdate(Step.PARSING_HEAP_DUMP);
            Snapshot snapshot = Snapshot.createSnapshot(buffer);
            listener.onProgressUpdate(Step.DEDUPLICATING_GC_ROOTS);
            deduplicateGcRoots(snapshot);
            listener.onProgressUpdate(Step.FINDING_LEAKING_REF);
            Instance leakingRef = findLeakingReference(referenceKey, snapshot);
            if (leakingRef == null) {
                return AnalysisResult.noLeak("UnknownNoKeyedWeakReference", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime));
            }

            return findLeakTrace(
                    referenceKey, "NAME_NOT_SUPPORTED", analysisStartNanoTime, snapshot,
                    leakingRef, computeRetainedSize, 0
            );
        } catch (Throwable e) {
            return AnalysisResult.failure(e, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime));
        }
    }

    public static List<AnalysisResult> checkForLeaks(
            File heapDumpFile,
            boolean computeRetainedSize) throws Exception {

        long analysisStartNanoTime = System.nanoTime();

        if (!heapDumpFile.exists()) {
            IllegalArgumentException exception = new IllegalArgumentException("File does not exist: " + heapDumpFile);
            return Arrays.asList(
                    new AnalysisResult.failure(exception, nanoTimeToMillis(analysisStartNanoTime))
            );
        }

        try {
            onProgressUpdate("READING_HEAP_DUMP_FILE");
            MemoryMappedFileBuffer buffer = new MemoryMappedFileBuffer(heapDumpFile);
            onProgressUpdate("PARSING_HEAP_DUMP");
            Snapshot snapshot = Snapshot.createSnapshot(buffer);
            onProgressUpdate("DEDUPLICATING_GC_ROOTS");
            deduplicateGcRoots(snapshot);
            onProgressUpdate("FINDING_LEAKING_REFS");

            String heapDumpMemoryStoreClassName = "HeapDumpMemoryStore";
            Class<?> heapDumpMemoryStoreClass = snapshot.findClass(heapDumpMemoryStoreClassName);
            Object retainedKeysArray = staticFieldValue(heapDumpMemoryStoreClass, "retainedKeysForHeapDump");
            String[] retainedKeys = asStringArray(retainedKeysArray);
            long heapDumpUptimeMillis = staticFieldValue(heapDumpMemoryStoreClass, "heapDumpUptimeMillis", long.class);

            if (retainedKeys.length == 0) {
                IllegalStateException exception = new IllegalStateException("No retained keys found in heap dump");
                return Arrays.asList(
                        new AnalysisResult.failure(exception, nanoTimeToMillis(analysisStartNanoTime))
                );
            }

            Class<?> refClass = snapshot.findClass(keyedWeakReferenceClassName);
            if (refClass == null) {
                throw new IllegalStateException("Could not find the " + keyedWeakReferenceClassName + " class in the heap dump.");
            }
            List<Instance> leakingWeakRefs = new ArrayList<>();
            List<String> keysFound = new ArrayList<>();
            for (Instance instance : refClass.instancesList) {
                Map<String, Object> values = classInstanceValues(instance);
                Object keyFieldValue = values.get("key");
                if (keyFieldValue == null) {
                    keysFound.add(null);
                    continue;
                }
                String keyCandidate = asString(keyFieldValue);
                boolean wasRetained = contains(retainedKeys, keyCandidate);
                if (wasRetained) {
                    leakingWeakRefs.add(instance);
                }
                keysFound.add(keyCandidate);
            }
            if (retainedKeys.length > 0) {
                throw new IllegalStateException("Could not find weak references with keys " + Arrays.toString(retainedKeys) + " in " + keysFound);
            }

            List<AnalysisResult> analysisResults = new ArrayList<>();
            for (Instance leakingWeakRef : leakingWeakRefs) {
                Map<String, Object> values = classInstanceValues(leakingWeakRef);
                Instance referent = (Instance) values.get("referent");
                String name = asString(values.get("name"));
                if (referent != null) {
                    String key = asString(values.get("key"));
                    long watchUptimeMillis = ((Number) values.get("watchUptimeMillis")).longValue();
                    long watchDurationMillis = heapDumpUptimeMillis - watchUptimeMillis;
                    analysisResults.add(
                            findLeakTrace(
                                    key, name, analysisStartNanoTime, snapshot, referent,
                                    computeRetainedSize, watchDurationMillis
                            )
                    );
                } else {
                    analysisResults.add(
                            new AnalysisResult.noLeak(name, nanoTimeToMillis(analysisStartNanoTime))
                    );
                }
            }

            return analysisResults;
        } catch (Throwable e) {
            return Arrays.asList(new AnalysisResult.failure(e, nanoTimeToMillis(analysisStartNanoTime)));
        }
    }
    internal void deduplicateGcRoots(Snapshot snapshot) {
    Map<String, RootObj> uniqueRootMap = new THashMap<>();

    List<RootObj> gcRoots = snapshot.gcRoots;
    for (RootObj root : gcRoots) {
      String key = generateRootKey(root);
      if (!uniqueRootMap.containsKey(key)) {
        uniqueRootMap.put(key, root);
      }
    }

    gcRoots.clear();
    uniqueRootMap.forEach((key, value) -> gcRoots.add(uniqueRootMap.get(key)));
  }

  private String generateRootKey(RootObj root) {
    return String.format("%s@0x%08x", root.rootType.getName(), root.id);
  }

  private Instance findLeakingReference(
      String key,
      Snapshot snapshot) {
    Class refClass = snapshot.findClass(keyedWeakReferenceClassName);
    if (refClass == null) {
      throw new IllegalStateException(
          "Could not find the " + keyedWeakReferenceClassName + " class in the heap dump.");
    }
    List<String> keysFound = new ArrayList<>();
    for (Instance instance : refClass.instancesList) {
      Map<String, Object> values = classInstanceValues(instance);
      Object keyFieldValue = values.get("key");
      if (keyFieldValue == null) {
        keysFound.add(null);
        continue;
      }
      String keyCandidate = asString(keyFieldValue);
      if (keyCandidate.equals(key)) {
        return fieldValue(values, "referent");
      }
      keysFound.add(keyCandidate);
    }
    throw new IllegalStateException(
        "Could not find weak reference with key " + key + " in " + keysFound);
  }

  private AnalysisResult findLeakTrace(
      String referenceKey,
      String referenceName,
      long analysisStartNanoTime,
      Snapshot snapshot,
      Instance leakingRef,
      boolean computeRetainedSize,
      long watchDurationMs) {

    listener.onProgressUpdate(FINDING_SHORTEST_PATH);
    ShortestPathFinder pathFinder = new ShortestPathFinder(excludedRefs);
    PathResult result = pathFinder.findPath(snapshot, leakingRef);

    String className = leakingRef.classObj.className;

    if (result.leakingNode == null) {
      return AnalysisResult.noLeak(className, since(analysisStartNanoTime));
    }

    listener.onProgressUpdate(BUILDING_LEAK_TRACE);
    LeakTrace leakTrace = buildLeakTrace(result.leakingNode);

    long retainedSize = 0;
    if (computeRetainedSize) {
      listener.onProgressUpdate(COMPUTING_DOMINATORS);

      snapshot.computeDominators();

      Instance leakingInstance = result.leakingNode.instance;

      retainedSize = leakingInstance.totalRetainedSize;
    } else {
      retainedSize = AnalysisResult.RETAINED_HEAP_SKIPPED;
    }

    return AnalysisResult.leakDetected(
        referenceKey, referenceName,
        result.excludingKnownLeaks, className, leakTrace,
        retainedSize,
        since(analysisStartNanoTime), watchDurationMs);
  }

  private LeakTrace buildLeakTrace(LeakNode leakingNode) {
    List<LeakTraceElement> elements = new ArrayList<>();

    LeakNode node = new LeakNode(null, null, leakingNode, null);
    while (node != null) {
      LeakTraceElement element = buildLeakElement(node);
      if (element != null) {
        elements.add(0, element);
      }
      node = node.parent;
    }

    int expectedReachability = computeExpectedReachability(elements);

    return new LeakTrace(elements, expectedReachability);
  }

     private static List<Reachability> computeExpectedReachability(
            List<LeakTraceElement> elements) {
        int lastReachableElementIndex = 0;
        int lastElementIndex = elements.size() - 1;
        int firstUnreachableElementIndex = lastElementIndex;

        List<Reachability> expectedReachability = new ArrayList<>();

        for (int i = 0; i < elements.size(); i++) {
            LeakTraceElement element = elements.get(i);
            Reachability reachability = inspectElementReachability(element);
            expectedReachability.add(reachability);
            if (reachability.status == Reachability.REACHABILITY_STATUS.REACHABLE) {
                lastReachableElementIndex = i;
            } else if (firstUnreachableElementIndex == lastElementIndex && reachability.status == Reachability.REACHABILITY_STATUS.UNREACHABLE) {
                firstUnreachableElementIndex = i;
            }
        }

        if (expectedReachability.get(0).status == Reachability.REACHABILITY_STATUS.UNKNOWN) {
            expectedReachability.set(0, Reachability.reachable("it's a GC root"));
        }

        if (expectedReachability.get(lastElementIndex).status == Reachability.REACHABILITY_STATUS.UNKNOWN) {
            expectedReachability.set(lastElementIndex, Reachability.unreachable("it's the leaking instance"));
        }

        for (int i = 1; i < lastElementIndex; i++) {
            Reachability reachability = expectedReachability.get(i);
            if (reachability.status == Reachability.REACHABILITY_STATUS.UNKNOWN) {
                if (i <= lastReachableElementIndex) {
                    String lastReachableName = elements.get(lastReachableElementIndex).getSimpleClassName();
                    expectedReachability.set(i, Reachability.reachable("" + lastReachableName + " is not leaking"));
                } else if (i >= firstUnreachableElementIndex) {
                    String firstUnreachableName = elements.get(firstUnreachableElementIndex).getSimpleClassName();
                    expectedReachability.set(i, Reachability.unreachable("" + firstUnreachableName + " is leaking"));
                }
            }
        }
        return expectedReachability;
    }

    private static Reachability inspectElementReachability(LeakTraceElement element) {
        for (ReachabilityInspector reachabilityInspector : reachabilityInspectors) {
            Reachability reachability = reachabilityInspector.expectedReachability(element);
            if (reachability.status != Reachability.REACHABILITY_STATUS.UNKNOWN) {
                return reachability;
            }
        }
        return Reachability.unknown();
    }
    
    private LeakTraceElement buildLeakElement(LeakNode node) {
        if (node.parent == null) {
            return null;
        }
        Instance holder = node.parent.instance;

        if (holder instanceof RootObj) {
            return null;
        }
        Holder holderType;
        String className;
        String extra = null;
        List<LeakReference> leakReferences = describeFields(holder);

        className = getClassName(holder);

        List<String> classHierarchy = new ArrayList<>();
        classHierarchy.add(className);

        if (holder instanceof ClassInstance) {
            ClassObj classObj = ((ClassInstance) holder).classObj;

            do {
                classObj = classObj.superClassObj;
                if (!classObj.className.equals(ROOT_CLASS_NAME)) {
                    classHierarchy.add(classObj.className);
                }
            } while (!classObj.className.equals(ROOT_CLASS_NAME));
        }

        if (holder instanceof ClassObj) {
            holderType = Holder.CLASS;
        } else if (holder instanceof ArrayInstance) {
            holderType = Holder.ARRAY;
        } else {
            ClassObj classObj;
            if (holder instanceof ClassInstance) {
                classObj = ((ClassInstance) holder).classObj;
            } else {
                classObj = ((Instance) holder).classObj;
            }

            if (extendsThread(classObj)) {
                holderType = Holder.THREAD;
                String threadName = threadName(holder);
                extra = "(named '" + threadName + "')";
            } else if (className.matches(ANONYMOUS_CLASS_NAME_PATTERN.pattern())) {
                Class<?> actualClass;
                try {
                    actualClass = Class.forName(classObj.className);
                } catch (ClassNotFoundException e) {
                    actualClass = null;
                }

                if (actualClass != null) {
                    Class<?>[] interfaces = actualClass.getInterfaces();
                    if (interfaces.length > 0) {
                        extra = "(anonymous implementation of " + interfaces[0].getName() + ")";
                    } else {
                        extra = "(anonymous subclass of java.lang.Object)";
                    }
                }

                holderType = Holder.OBJECT;
            } else {
                holderType = Holder.OBJECT;
            }
        }
        return new LeakTraceElement(
                node.leakReference, holderType, classHierarchy, extra,
                node.exclusion, leakReferences
        );
    }

    private List<LeakReference> describeFields(Instance instance) {
        List<LeakReference> leakReferences = new ArrayList<>();
        if (instance instanceof ClassObj) {
            ClassObj classObj = (ClassObj) instance;
            for (Map.Entry<Field, Object> entry : classObj.staticFieldValues.entrySet()) {
                Field key = entry.getKey();
                Object value = entry.getValue();
                String name = key.getName();
                String stringValue = valueAsString(value);
                leakReferences.add(new LeakReference(LeakReference.Type.STATIC_FIELD, name, stringValue));
            }
        } else if (instance instanceof ArrayInstance) {
            ArrayInstance arrayInstance = (ArrayInstance) instance;
            if (arrayInstance.arrayType == Type.OBJECT) {
                Object[] values = arrayInstance.values;
                for (int i = 0; i < values.length; i++) {
                    String name = Integer.toString(i);
                    Object value = values[i];
                    String stringValue = valueAsString(value);
                    leakReferences.add(new LeakReference(LeakReference.Type.ARRAY_ENTRY, name, stringValue));
                }
            }
        } else {
            ClassObj classObj = instance.classObj;
            for (Map.Entry<Field, Object> entry : classObj.staticFieldValues.entrySet()) {
                Field key = entry.getKey();
                Object value = entry.getValue();
                String name = key.getName();
                String stringValue = valueAsString(value);
                leakReferences.add(new LeakReference(LeakReference.Type.STATIC_FIELD, name, stringValue));
            }
            ClassInstance classInstance = (ClassInstance) instance;
            for (FieldValue field : classInstance.values) {
                String name = field.field.getName();
                Object value = field.value;
                String stringValue = valueAsString(value);
                leakReferences.add(new LeakReference(LeakReference.Type.INSTANCE_FIELD, name, stringValue));
            }
        }
        return leakReferences;
    }

    private String getClassName(Instance instance) {
        if (instance instanceof ClassObj) {
            return ((ClassObj) instance).className;
        } else if (instance instanceof ArrayInstance) {
            return ((ArrayInstance) instance).classObj.className;
        } else {
            return ((Instance) instance).classObj.className;
        }
    }
    private static long since(long analysisStartNanoTime) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime);
    }
    private static final String ANONYMOUS_CLASS_NAME_PATTERN = "^.+\\$\\d+$";
}