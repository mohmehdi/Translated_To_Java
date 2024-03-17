

package com.squareup.leakcanary;

import com.android.tools.perflib.captures.MemoryMappedFileBuffer;
import com.squareup.haha.perflib.ArrayInstance;
import com.squareup.haha.perflib.ClassInstance;
import com.squareup.haha.perflib.ClassObj;
import com.squareup.haha.perflib.Instance;
import com.squareup.haha.perflib.RootObj;
import com.squareup.haha.perflib.Snapshot;
import com.squareup.haha.perflib.Type;
import com.squareup.leakcanary.AnalyzerProgressListener;
import com.squareup.leakcanary.AnalyzerProgressListener.Step;
import com.squareup.leakcanary.HahaHelper;
import com.squareup.leakcanary.HahaHelper.AsStringFunction;
import com.squareup.leakcanary.HahaHelper.AsStringArrayFunction;
import com.squareup.leakcanary.HahaHelper.ClassInstanceValuesFunction;
import com.squareup.leakcanary.HahaHelper.ExtendsThreadFunction;
import com.squareup.leakcanary.HahaHelper.FieldValueFunction;
import com.squareup.leakcanary.HahaHelper.StaticFieldValueFunction;
import com.squareup.leakcanary.HahaHelper.ThreadNameFunction;
import com.squareup.leakcanary.HahaHelper.ValueAsStringFunction;
import com.squareup.leakcanary.LeakTraceElement.Holder;
import com.squareup.leakcanary.LeakTraceElement.Type;
import com.squareup.leakcanary.Reachability.Status;
import com.squareup.leakcanary.Reachability.Status.REACHABLE;
import com.squareup.leakcanary.Reachability.Status.UNREACHABLE;
import gnu.trove.THashMap;
import gnu.trove.TObjectProcedure;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.TestOnly;

public class HeapAnalyzer {

  private final ExcludedRefs excludedRefs;
  private final AnalyzerProgressListener listener;
  private final List<Class<? extends Reachability.Inspector>> reachabilityInspectorClasses;
  private final String keyedWeakReferenceClassName;
  private final List<Reachability.Inspector> reachabilityInspectors;

  public HeapAnalyzer(
      ExcludedRefs excludedRefs,
      AnalyzerProgressListener listener,
      List<Class<? extends Reachability.Inspector>> reachabilityInspectorClasses,
      String keyedWeakReferenceClassName) {
    this.excludedRefs = excludedRefs;
    this.listener = listener;
    this.reachabilityInspectorClasses = reachabilityInspectorClasses;
    this.keyedWeakReferenceClassName = keyedWeakReferenceClassName;
    this.reachabilityInspectors = new ArrayList<>();
  }

  public HeapAnalyzer(
      ExcludedRefs excludedRefs,
      AnalyzerProgressListener listener,
      List<Class<? extends Reachability.Inspector>> reachabilityInspectorClasses) {
    this(excludedRefs, listener, reachabilityInspectorClasses, KeyedWeakReference.class.getName());
  }

  public HeapAnalyzer(ExcludedRefs excludedRefs, AnalyzerProgressListener listener) {
    this(excludedRefs, listener, new ArrayList<>(), KeyedWeakReference.class.getName());
  }

  public void init() {
    for (Class<? extends Reachability.Inspector> reachabilityInspectorClass : reachabilityInspectorClasses) {
      try {
        Constructor<?> defaultConstructor = reachabilityInspectorClass.getDeclaredConstructor();
        defaultConstructor.setAccessible(true);
        reachabilityInspectors.add((Reachability.Inspector) defaultConstructor.newInstance());
      } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }
  }

      public AnalysisResult findLeakTrace(
            String referenceKey,
            String referenceName,
            long analysisStartNanoTime,
            Snapshot snapshot,
            Instance leakingRef,
            boolean computeRetainedSize,
            long watchDurationMs
    ) {
        listener.accept(FINDING_SHORTEST_PATH);
        ShortestPathFinder pathFinder = new ShortestPathFinder(excludedRefs);
        PathFinderResult result = pathFinder.findPath(snapshot, leakingRef);

        String className = leakingRef.getClassObj().getClassName();

        if (result.getLeakingNode() == null) {
            return AnalysisResult.noLeak(className, Duration.ofNanos(System.nanoTime() - analysisStartNanoTime));
        }

        listener.accept(BUILDING_LEAK_TRACE);
        List<PathNode> leakTrace = buildLeakTrace(result.getLeakingNode());

        long retainedSize = computeRetainedSize
                ? (listener.andThen(n -> System.out.println(n))).accept(COMPUTING_DOMINATORS)
                && snapshot.computeDominators()
                && leakingRef.getClassObj().getInstance().getTotalRetainedSize()
                : AnalysisResult.RETAINED_HEAP_SKIPPED;

        return AnalysisResult.leakDetected(
                referenceKey, referenceName,
                result.isExcludingKnownLeaks(), className, leakTrace,
                retainedSize,
                Duration.ofNanos(System.nanoTime() - analysisStartNanoTime), watchDurationMs
        );
    }

  @TestOnly
  @Deprecated(
      "Use {@link #checkForLeaks(File, boolean)} instead. We're keeping this only because\n"
          + "    our tests currently run with older heapdumps."
  )
  public AnalysisResult checkForLeak(
      File heapDumpFile,
      String referenceKey,
      boolean computeRetainedSize) {
    long analysisStartNanoTime = System.nanoTime();

    if (!heapDumpFile.exists()) {
      IllegalArgumentException exception = new IllegalArgumentException("File does not exist: " + heapDumpFile);
      return AnalysisResult.failure(exception, since(analysisStartNanoTime));
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
        return AnalysisResult.noLeak("UnknownNoKeyedWeakReference", since(analysisStartNanoTime));
      }

      return findLeakTrace(
          referenceKey, "NAME_NOT_SUPPORTED", analysisStartNanoTime, snapshot,
          leakingRef, computeRetainedSize, 0
      );
    } catch (Throwable e) {
      return AnalysisResult.failure(e, since(analysisStartNanoTime));
    }
  }

  public static List<AnalysisResult> checkForLeaks(File heapDumpFile, boolean computeRetainedSize) {
        long analysisStartNanoTime = System.nanoTime();

        if (!heapDumpFile.exists()) {
            IllegalArgumentException exception = new IllegalArgumentException("File does not exist: " + heapDumpFile);
            return Arrays.asList(new AnalysisResult.Failure(exception, since(analysisStartNanoTime)));
        }

        try {
            onProgressUpdate(READING_HEAP_DUMP_FILE);
            MemoryMappedFileBuffer buffer = new MemoryMappedFileBuffer(heapDumpFile);
            onProgressUpdate(PARSING_HEAP_DUMP);
            Snapshot snapshot = Snapshot.createSnapshot(buffer);
            onProgressUpdate(DEDUPLICATING_GC_ROOTS);
            deduplicateGcRoots(snapshot);
            onProgressUpdate(FINDING_LEAKING_REFS);

            Class heapDumpMemoryStoreClass = snapshot.findClass(HeapDumpMemoryStore.class.getName());
            Object retainedKeysArray = staticFieldValue(heapDumpMemoryStoreClass, "retainedKeysForHeapDump");
            String[] retainedKeys = asStringArray(retainedKeysArray);
            long heapDumpUptimeMillis = staticFieldValue(heapDumpMemoryStoreClass, "heapDumpUptimeMillis");

            if (retainedKeys.length == 0) {
                IllegalStateException exception = new IllegalStateException("No retained keys found in heap dump");
                return Arrays.asList(new AnalysisResult.Failure(exception, since(analysisStartNanoTime)));
            }

            Class refClass = snapshot.findClass(keyedWeakReferenceClassName);
            if (refClass == null) {
                throw new IllegalStateException("Could not find the " + keyedWeakReferenceClassName + " class in the heap dump.");
            }
            List<Instance> leakingWeakRefs = new ArrayList<>();
            List<String> keysFound = new ArrayList<>();
            for (Instance instance : refClass.getInstancesList()) {
                Object values = classInstanceValues(instance);
                Object keyFieldValue = fieldValue(values, "key");
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
                Object values = classInstanceValues(leakingWeakRef);
                Instance referent = (Instance) fieldValue(values, "referent");
                String name = asString((Object) fieldValue(values, "name")).toString();
                if (referent != null) {
                    String key = asString((Object) fieldValue(values, "key")).toString();
                    long watchUptimeMillis = (long) fieldValue(values, "watchUptimeMillis");
                    long watchDurationMillis = heapDumpUptimeMillis - watchUptimeMillis;
                    analysisResults.add(findLeakTrace(
                            key, name, analysisStartNanoTime, snapshot, referent,
                            computeRetainedSize, watchDurationMillis
                    ));
                } else {
                    analysisResults.add(new AnalysisResult.NoLeak(name, since(analysisStartNanoTime)));
                }
            }

            return analysisResults;
        } catch (Throwable e) {
            return Arrays.asList(new AnalysisResult.Failure(e, since(analysisStartNanoTime)));
        }
    }
    public void deduplicateGcRoots(Snapshot snapshot) {
        Map<String, RootObj> uniqueRootMap = new THashMap<>();

        List<RootObj> gcRoots = snapshot.gcRoots;
        for (RootObj root : gcRoots) {
            String key = generateRootKey(root);
            if (!uniqueRootMap.containsKey(key)) {
                uniqueRootMap.put(key, root);
            }
        }

        gcRoots.clear();
        for (Map.Entry<String, RootObj> entry : uniqueRootMap.entrySet()) {
            gcRoots.add(entry.getValue());
        }
    }

    private String generateRootKey(RootObj root) {
        return String.format("%s@0x%08x", root.getRootType().getName(), root.getId());
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
        for (Instance instance : refClass.getInstancesList()) {
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

      private List<LeakTraceElement> buildLeakTrace(LeakNode leakingNode) {
        List<LeakTraceElement> elements = new ArrayList<>();

        LeakNode node = new LeakNode(null, null, leakingNode, null);
        while (node != null) {
            LeakTraceElement element = buildLeakElement(node);
            if (element != null) {
                elements.add(0, element);
            }
            node = node.parent;
        }

        List<Reachability> expectedReachability = computeExpectedReachability(elements);

        return new LeakTrace(elements, expectedReachability);
    }

    private List<Reachability> computeExpectedReachability(List<LeakTraceElement> elements) {
        int lastReachableElementIndex = 0;
        int lastElementIndex = elements.size() - 1;
        int firstUnreachableElementIndex = lastElementIndex;

        List<Reachability> expectedReachability = new ArrayList<>();

        for (int index = 0; index < elements.size(); index++) {
            LeakTraceElement element = elements.get(index);
            Reachability reachability = inspectElementReachability(element);
            expectedReachability.add(reachability);
            if (reachability.getStatus() == Reachability.Status.REACHABLE) {
                lastReachableElementIndex = index;
            } else if (firstUnreachableElementIndex == lastElementIndex && reachability.getStatus() == Reachability.Status.UNREACHABLE) {
                firstUnreachableElementIndex = index;
            }
        }

        if (expectedReachability.get(0).getStatus() == Reachability.Status.UNKNOWN) {
            expectedReachability.set(0, Reachability.reachable("it's a GC root"));
        }

        if (expectedReachability.get(lastElementIndex).getStatus() == Reachability.Status.UNKNOWN) {
            expectedReachability.set(lastElementIndex, Reachability.unreachable("it's the leaking instance"));
        }

        for (int i = 1; i < lastElementIndex; i++) {
            Reachability reachability = expectedReachability.get(i);
            if (reachability.getStatus() == Reachability.Status.UNKNOWN) {
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

    private Reachability inspectElementReachability(LeakTraceElement element) {
        for (ReachabilityInspector reachabilityInspector : reachabilityInspectors) {
            Reachability reachability = reachabilityInspector.expectedReachability(element);
            if (reachability.getStatus() != Reachability.Status.UNKNOWN) {
                return reachability;
            }
        }
        return Reachability.unknown();
    }

  private LeakTraceElement buildLeakElement(LeakNode node) {
  ArrayList classHierarchy = new ArrayList < > ();
  if (node.parent == null) {
    return null;
  }
  Object holder = node.parent.instance;
  LeakTraceElement.Holder holderType;
  String className;
  String extra = null;
  List leakReferences = describeFields(holder);
  className = getClassName(holder);
  classHierarchy.add(className);
  String rootClassName = Object.class.getName();
  if (holder instanceof ClassInstance) {
    ClassObj classObj = ((ClassInstance) holder).classObj;
    do {
      classObj = classObj.superClassObj;
      if (!rootClassName.equals(classObj.className)) {
        classHierarchy.add(classObj.className);
      }
    } while (!rootClassName.equals(classObj.className));
  }
  if (holder instanceof ClassObj) {
    holderType = LeakTraceElement.Holder.CLASS;
  } else if (holder instanceof ArrayInstance) {
    holderType = LeakTraceElement.Holder.ARRAY;
  } else {
    ClassObj classObj = ((Instance) holder).classObj;
    if (extendsThread(classObj)) {
      holderType = LeakTraceElement.Holder.THREAD;
      String threadName = threadName(holder);
      extra = "(named '" + threadName + "')";
    } else if (className.matches(ANONYMOUS_CLASS_NAME_PATTERN.toString())) {
      String parentClassName = classObj.superClassObj.className;
      if (rootClassName.equals(parentClassName)) {
        holderType = LeakTraceElement.Holder.OBJECT;
        try {
          Class < ? > actualClass = Class.forName(classObj.className);
          Class[] interfaces = actualClass.getInterfaces();
          extra = interfaces.length > 0 ? "(anonymous implementation of " + interfaces[0].getName() + ")" : "(anonymous subclass of java.lang.Object)";
        } catch (ClassNotFoundException ignored) {}
      } else {
        holderType = LeakTraceElement.Holder.OBJECT;
        extra = "(anonymous subclass of " + parentClassName + ")";
      }
    } else {
      holderType = LeakTraceElement.Holder.OBJECT;
    }
  }
  return new LeakTraceElement(node.leakReference, holderType, classHierarchy, extra, node.exclusion, leakReferences);
}

    private List<LeakReference> describeFields(Instance instance) {
        List<LeakReference> leakReferences = new ArrayList<>();
        if (instance instanceof ClassObj) {
            ClassObj classObj = (ClassObj) instance;
            for (Map.Entry<Field, Object> entry : classObj.staticFieldValues.entrySet()) {
                String name = entry.getKey().getName();
                String stringValue = valueAsString(entry.getValue());
                leakReferences.add(new LeakReference(STATIC_FIELD, name, stringValue));
            }
        } else if (instance instanceof ArrayInstance) {
            ArrayInstance arrayInstance = (ArrayInstance) instance;
            if (arrayInstance.arrayType == Type.OBJECT) {
                Object[] values = arrayInstance.values;
                for (int i = 0; i < values.length; i++) {
                    String name = Integer.toString(i);
                    String stringValue = valueAsString(values[i]);
                    leakReferences.add(new LeakReference(ARRAY_ENTRY, name, stringValue));
                }
            }
        } else {
            ClassObj classObj = instance.getClassObj();
            for (Map.Entry<Field, Object> entry : classObj.staticFieldValues.entrySet()) {
                String name = entry.getKey().getName();
                String stringValue = valueAsString(entry.getValue());
                leakReferences.add(new LeakReference(STATIC_FIELD, name, stringValue));
            }
            ClassInstance classInstance = (ClassInstance) instance;
            for (FieldValue field : classInstance.values) {
                String name = field.field.getName();
                String stringValue = valueAsString(field.value);
                leakReferences.add(new LeakReference(INSTANCE_FIELD, name, stringValue));
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
            return instance.getClassObj().className;
        }
    }

  private long since(long analysisStartNanoTime) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime);
  }
  
  private static final String ANONYMOUS_CLASS_NAME_PATTERN = "^.+\\$\\d+$";

}