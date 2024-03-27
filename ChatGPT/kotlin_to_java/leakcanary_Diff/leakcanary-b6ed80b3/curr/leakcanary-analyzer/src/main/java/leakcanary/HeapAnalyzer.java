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
import leakcanary.internal.HeapDumpMemoryStore;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HeapAnalyzer {

    private final ExcludedRefs excludedRefs;
    private final AnalyzerProgressListener listener;
    private final List<Inspector> reachabilityInspectors;
    private final String keyedWeakReferenceClassName;
    private final String heapDumpMemoryStoreClassName;
    private final List<Inspector> reachabilityInspectors = new ArrayList<>();

    public HeapAnalyzer(ExcludedRefs excludedRefs, AnalyzerProgressListener listener,
                        List<Class<? extends Inspector>> reachabilityInspectorClasses,
                        String keyedWeakReferenceClassName, String heapDumpMemoryStoreClassName) {
        this.excludedRefs = excludedRefs;
        this.listener = listener;
        this.keyedWeakReferenceClassName = keyedWeakReferenceClassName;
        this.heapDumpMemoryStoreClassName = heapDumpMemoryStoreClassName;

        for (Class<? extends Inspector> reachabilityInspectorClass : reachabilityInspectorClasses) {
            try {
                Inspector inspector = reachabilityInspectorClass.getDeclaredConstructor().newInstance();
                reachabilityInspectors.add(inspector);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public HeapAnalyzer(ExcludedRefs excludedRefs, AnalyzerProgressListener listener,
                        List<Class<? extends Inspector>> reachabilityInspectorClasses) {
        this(excludedRefs, listener, reachabilityInspectorClasses, KeyedWeakReference.class.getName(),
                HeapDumpMemoryStore.class.getName());
    }

    @TestOnly
    @Deprecated("Use {@link #checkForLeaks(File, boolean)} instead. We're keeping this only because\n" +
            "    our tests currently run with older heapdumps.")
    public AnalysisResult checkForLeak(File heapDumpFile, String referenceKey, boolean computeRetainedSize) {
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

            return findLeakTrace(referenceKey, "NAME_NOT_SUPPORTED", analysisStartNanoTime, snapshot,
                    leakingRef, computeRetainedSize, 0);
        } catch (Throwable e) {
            return AnalysisResult.failure(e, since(analysisStartNanoTime));
        }
    }

    public List<AnalysisResult> checkForLeaks(File heapDumpFile, boolean computeRetainedSize) {
        long analysisStartNanoTime = System.nanoTime();

        if (!heapDumpFile.exists()) {
            IllegalArgumentException exception = new IllegalArgumentException("File does not exist: " + heapDumpFile);
            return List.of(AnalysisResult.failure(exception, since(analysisStartNanoTime)));
        }

        try {
            listener.onProgressUpdate(Step.READING_HEAP_DUMP_FILE);
            MemoryMappedFileBuffer buffer = new MemoryMappedFileBuffer(heapDumpFile);
            listener.onProgressUpdate(Step.PARSING_HEAP_DUMP);
            Snapshot snapshot = Snapshot.createSnapshot(buffer);
            listener.onProgressUpdate(Step.DEDUPLICATING_GC_ROOTS);
            deduplicateGcRoots(snapshot);
            listener.onProgressUpdate(Step.FINDING_LEAKING_REFS);

            ClassObj heapDumpMemoryStoreClass = snapshot.findClass(heapDumpMemoryStoreClassName);
            ArrayInstance retainedKeysArray = HahaHelper.staticFieldValue(heapDumpMemoryStoreClass, "retainedKeysForHeapDump");
            String[] retainedKeys = HahaHelper.asStringArray(retainedKeysArray);
            long heapDumpUptimeMillis = HahaHelper.staticFieldValue(heapDumpMemoryStoreClass, "heapDumpUptimeMillis");

            if (retainedKeys.length == 0) {
                IllegalStateException exception = new IllegalStateException("No retained keys found in heap dump");
                return List.of(AnalysisResult.failure(exception, since(analysisStartNanoTime)));
            }

            ClassObj refClass = snapshot.findClass(keyedWeakReferenceClassName);
            if (refClass == null) {
                throw new IllegalStateException("Could not find the " + keyedWeakReferenceClassName + " class in the heap dump.");
            }

            List<Instance> leakingWeakRefs = new ArrayList<>();
            List<String> keysFound = new ArrayList<>();
            for (Instance instance : refClass.getInstancesList()) {
                List<Object> values = HahaHelper.classInstanceValues(instance);
                Object keyFieldValue = HahaHelper.fieldValue(values, "key");
                if (keyFieldValue == null) {
                    keysFound.add(null);
                    continue;
                }
                String keyCandidate = HahaHelper.asString(keyFieldValue);
                boolean wasRetained = retainedKeys.remove(keyCandidate);
                if (wasRetained) {
                    leakingWeakRefs.add(instance);
                }
                keysFound.add(keyCandidate);
            }
            if (retainedKeys.length > 0) {
                throw new IllegalStateException("Could not find weak references with keys " + retainedKeys + " in " + keysFound);
            }

            List<AnalysisResult> analysisResults = new ArrayList<>();
            for (Instance leakingWeakRef : leakingWeakRefs) {
                List<Object> values = HahaHelper.classInstanceValues(leakingWeakRef);
                Instance referent = HahaHelper.fieldValue(values, "referent");
                String name = HahaHelper.asString(HahaHelper.fieldValue(values, "name"));
                if (referent != null) {
                    String key = HahaHelper.asString(HahaHelper.fieldValue(values, "key"));
                    long watchUptimeMillis = HahaHelper.fieldValue(values, "watchUptimeMillis");
                    long watchDurationMillis = heapDumpUptimeMillis - watchUptimeMillis;
                    analysisResults.add(findLeakTrace(key, name, analysisStartNanoTime, snapshot, referent,
                            computeRetainedSize, watchDurationMillis));
                } else {
                    analysisResults.add(AnalysisResult.noLeak(name, since(analysisStartNanoTime)));
                }
            }

            return analysisResults;
        } catch (Throwable e) {
            return List.of(AnalysisResult.failure(e, since(analysisStartNanoTime)));
        }
    }

    void deduplicateGcRoots(Snapshot snapshot) {
        THashMap<String, RootObj> uniqueRootMap = new THashMap<>();

        List<RootObj> gcRoots = snapshot.getGcRoots();
        for (RootObj root : gcRoots) {
            String key = generateRootKey(root);
            if (!uniqueRootMap.containsKey(key)) {
                uniqueRootMap.put(key, root);
            }
        }

        gcRoots.clear();
        uniqueRootMap.forEach((TObjectProcedure<String>) key -> gcRoots.add(uniqueRootMap.get(key)));
    }

    private String generateRootKey(RootObj root) {
        return String.format("%s@0x%08x", root.getRootType().getName(), root.getId());
    }

    private Instance findLeakingReference(String key, Snapshot snapshot) {
        ClassObj refClass = snapshot.findClass(keyedWeakReferenceClassName);
        if (refClass == null) {
            throw new IllegalStateException("Could not find the " + keyedWeakReferenceClassName + " class in the heap dump.");
        }
        List<String> keysFound = new ArrayList<>();
        for (Instance instance : refClass.getInstancesList()) {
            List<Object> values = HahaHelper.classInstanceValues(instance);
            Object keyFieldValue = HahaHelper.fieldValue(values, "key");
            if (keyFieldValue == null) {
                keysFound.add(null);
                continue;
            }
            String keyCandidate = HahaHelper.asString(keyFieldValue);
            if (keyCandidate.equals(key)) {
                return HahaHelper.fieldValue(values, "referent");
            }
            keysFound.add(keyCandidate);
        }
        throw new IllegalStateException("Could not find weak reference with key " + key + " in " + keysFound);
    }

    private AnalysisResult findLeakTrace(String referenceKey, String referenceName, long analysisStartNanoTime,
                                         Snapshot snapshot, Instance leakingRef, boolean computeRetainedSize, long watchDurationMs) {
        listener.onProgressUpdate(Step.FINDING_SHORTEST_PATH);
        ShortestPathFinder pathFinder = new ShortestPathFinder(excludedRefs);
        ShortestPathFinder.Result result = pathFinder.findPath(snapshot, leakingRef);

        String className = leakingRef.getClassObj().getClassName();

        if (result.getLeakingNode() == null) {
            return AnalysisResult.noLeak(className, since(analysisStartNanoTime));
        }

        listener.onProgressUpdate(Step.BUILDING_LEAK_TRACE);
        LeakTrace leakTrace = buildLeakTrace(result.getLeakingNode());

        long retainedSize;
        if (computeRetainedSize) {
            listener.onProgressUpdate(Step.COMPUTING_DOMINATORS);
            snapshot.computeDominators();
            Instance leakingInstance = result.getLeakingNode().getInstance();
            retainedSize = leakingInstance.getTotalRetainedSize();
        } else {
            retainedSize = AnalysisResult.RETAINED_HEAP_SKIPPED;
        }

        return AnalysisResult.leakDetected(referenceKey, referenceName, result.getExcludingKnownLeaks(),
                className, leakTrace, retainedSize, since(analysisStartNanoTime), watchDurationMs);
    }

    private LeakTrace buildLeakTrace(LeakNode leakingNode) {
        List<LeakTraceElement> elements = new ArrayList<>();

        LeakNode node = new LeakNode(null, null, leakingNode, null);
        while (node != null) {
            LeakTraceElement element = buildLeakElement(node);
            if (element != null) {
                elements.add(0, element);
            }
            node = node.getParent();
        }

        List<Reachability> expectedReachability = computeExpectedReachability(elements);

        return new LeakTrace(elements, expectedReachability);
    }

    private List<Reachability> computeExpectedReachability(List<LeakTraceElement> elements) {
        int lastReachableElementIndex = 0;
        int lastElementIndex = elements.size() - 1;
        int firstUnreachableElementIndex = lastElementIndex;

        List<Reachability> expectedReachability = new ArrayList<>();

        for (int i = 0; i < elements.size(); i++) {
            LeakTraceElement element = elements.get(i);
            Reachability reachability = inspectElementReachability(element);
            expectedReachability.add(reachability);
            if (reachability.getStatus() == Reachability.Status.REACHABLE) {
                lastReachableElementIndex = i;
            } else if (firstUnreachableElementIndex == lastElementIndex && reachability.getStatus() == Reachability.Status.UNREACHABLE) {
                firstUnreachableElementIndex = i;
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
                    expectedReachability.set(i, Reachability.reachable(lastReachableName + " is not leaking"));
                } else if (i >= firstUnreachableElementIndex) {
                    String firstUnreachableName = elements.get(firstUnreachableElementIndex).getSimpleClassName();
                    expectedReachability.set(i, Reachability.unreachable(firstUnreachableName + " is leaking"));
                }
            }
        }

        return expectedReachability;
    }

    private Reachability inspectElementReachability(LeakTraceElement element) {
        for (Inspector reachabilityInspector : reachabilityInspectors) {
            Reachability reachability = reachabilityInspector.expectedReachability(element);
            if (reachability.getStatus() != Reachability.Status.UNKNOWN) {
                return reachability;
            }
        }
        return Reachability.unknown();
    }

    private LeakTraceElement buildLeakElement(LeakNode node) {
        if (node.getParent() == null) {
            return null;
        }

        Instance holder = node.getParent().getInstance();

        if (holder instanceof RootObj) {
            return null;
        }

        LeakTraceElement.Holder holderType;
        String className;
        String extra = null;
        List<LeakReference> leakReferences = describeFields(holder);

        className = getClassName(holder);

        List<String> classHierarchy = new ArrayList<>();
        classHierarchy.add(className);
        String rootClassName = Object.class.getName();
        if (holder instanceof ClassInstance) {
            ClassObj classObj = ((ClassInstance) holder).getClassObj();
            do {
                classObj = classObj.getSuperClassObj();
                if (!classObj.getClassName().equals(rootClassName)) {
                    classHierarchy.add(classObj.getClassName());
                }
            } while (!classObj.getClassName().equals(rootClassName));
        }

        if (holder instanceof ClassObj) {
            holderType = LeakTraceElement.Holder.CLASS;
        } else if (holder instanceof ArrayInstance) {
            holderType = LeakTraceElement.Holder.ARRAY;
        } else {
            ClassObj classObj = holder.getClassObj();
            if (HahaHelper.extendsThread(classObj)) {
                holderType = LeakTraceElement.Holder.THREAD;
                String threadName = HahaHelper.threadName(holder);
                extra = "(named '" + threadName + "')";
            } else if (className.matches(HeapAnalyzer.ANONYMOUS_CLASS_NAME_PATTERN)) {
                String parentClassName = classObj.getSuperClassObj().getClassName();
                if (rootClassName.equals(parentClassName)) {
                    holderType = LeakTraceElement.Holder.OBJECT;
                    try {
                        Class<?> actualClass = Class.forName(classObj.getClassName());
                        Class<?>[] interfaces = actualClass.getInterfaces();
                        if (interfaces.length > 0) {
                            String implementedInterface = interfaces[0].getName();
                            extra = "(anonymous implementation of " + implementedInterface + ")";
                        } else {
                            extra = "(anonymous subclass of java.lang.Object)";
                        }
                    } catch (ClassNotFoundException ignored) {
                    }
                } else {
                    holderType = LeakTraceElement.Holder.OBJECT;
                    extra = "(anonymous subclass of " + parentClassName + ")";
                }
            } else {
                holderType = LeakTraceElement.Holder.OBJECT;
            }
        }
        return new LeakTraceElement(node.getLeakReference(), holderType, classHierarchy, extra,
                node.getExclusion(), leakReferences);
    }

    private List<LeakReference> describeFields(Instance instance) {
        List<LeakReference> leakReferences = new ArrayList<>();
        if (instance instanceof ClassObj) {
            ClassObj classObj = (ClassObj) instance;
            for (Object key : classObj.getStaticFieldValues().keySet()) {
                String name = key.toString();
                String stringValue = HahaHelper.valueAsString(classObj.getStaticFieldValues().get(key));
                leakReferences.add(new LeakReference(LeakTraceElement.Type.STATIC_FIELD, name, stringValue));
            }
        } else if (instance instanceof ArrayInstance) {
            ArrayInstance arrayInstance = (ArrayInstance) instance;
            if (arrayInstance.getArrayType() == Type.OBJECT) {
                Object[] values = arrayInstance.getValues();
                for (int i = 0; i < values.length; i++) {
                    String name = Integer.toString(i);
                    String stringValue = HahaHelper.valueAsString(values[i]);
                    leakReferences.add(new LeakReference(LeakTraceElement.Type.ARRAY_ENTRY, name, stringValue));
                }
            }
        } else {
            ClassObj classObj = instance.getClassObj();
            for (Object key : classObj.getStaticFieldValues().keySet()) {
                String name = key.toString();
                String stringValue = HahaHelper.valueAsString(classObj.getStaticFieldValues().get(key));
                leakReferences.add(new LeakReference(LeakTraceElement.Type.STATIC_FIELD, name, stringValue));
            }
            ClassInstance classInstance = (ClassInstance) instance;
            for (Instance.Field field : classInstance.getValues()) {
                String name = field.getField().getName();
                String stringValue = HahaHelper.valueAsString(field.getValue());
                leakReferences.add(new LeakReference(LeakTraceElement.Type.INSTANCE_FIELD, name, stringValue));
            }
        }
        return leakReferences;
    }

    private String getClassName(Instance instance) {
        if (instance instanceof ClassObj) {
            return ((ClassObj) instance).getClassName();
        } else if (instance instanceof ArrayInstance) {
            return ((ArrayInstance) instance).getClassObj().getClassName();
        } else {
            return instance.getClassObj().getClassName();
        }
    }

    private long since(long analysisStartNanoTime) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime);
    }

    private static final String ANONYMOUS_CLASS_NAME_PATTERN = "^.+\\$\\d+$";
}