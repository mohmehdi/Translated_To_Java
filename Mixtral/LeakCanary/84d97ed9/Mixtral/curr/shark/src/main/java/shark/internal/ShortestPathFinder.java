

package shark.internal;

import shark.GcRoot;
import shark.GcRoot.JavaFrame;
import shark.GcRoot.ThreadObject;
import shark.HeapGraph;
import shark.HeapObject;
import shark.HeapObject.HeapClass;
import shark.HeapObject.HeapInstance;
import shark.HeapObject.HeapObjectArray;
import shark.HeapObject.HeapPrimitiveArray;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ObjectArrayDumpRecord;
import shark.LeakReference;
import shark.LeakTraceElement.Type;
import shark.LeakTraceElement.Type.ARRAY_ENTRY;
import shark.LeakTraceElement.Type.INSTANCE_FIELD;
import shark.LeakTraceElement.Type.LOCAL;
import shark.LeakTraceElement.Type.STATIC_FIELD;
import shark.PrimitiveType;
import shark.ReferenceMatcher;
import shark.IgnoredReferenceMatcher;
import shark.LibraryLeakReferenceMatcher;
import shark.ReferencePattern;
import shark.ReferencePattern.InstanceFieldPattern;
import shark.ReferencePattern.StaticFieldPattern;
import shark.SharkLog;
import shark.ValueHolder;
import shark.internal.ReferencePathNode.ChildNode.LibraryLeakNode;
import shark.internal.ReferencePathNode.ChildNode.NormalNode;
import shark.internal.ReferencePathNode.RootNode;
import shark.internal.hppc.LongLongScatterMap;
import shark.internal.hppc.LongScatterSet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ShortestPathFinder {

    private final Deque<ReferencePathNode> toVisitQueue = new ArrayDeque<>();
    private final Deque<LibraryLeakNode> toVisitLastQueue = new ArrayDeque<>();
    private final Set<Long> toVisitSet = new HashSet<>();
    private final Set<Long> toVisitLastSet = new HashSet<>();
    private final LongScatterSet visitedSet = new LongScatterSet();
    private Set<Long> leakingInstanceObjectIds;
    private LongLongScatterMap dominatedInstances;
    private int sizeOfObjectInstances;


    public class Results {
        private List<ReferencePathNode> shortestPathsToLeakingInstances;
        private LongLongMap dominatedInstances; // You can use LongLongObjectMap if you need to support null values

        public Results(List<ReferencePathNode> shortestPathsToLeakingInstances, LongLongMap dominatedInstances) {
            this.shortestPathsToLeakingInstances = shortestPathsToLeakingInstances;
            this.dominatedInstances = dominatedInstances != null ? dominatedInstances : LongLongMap.empty();
        }
    }
    public Results findPaths(
            HeapGraph graph,
            List<ReferenceMatcher> referenceMatchers,
            Set<Long> leakingInstanceObjectIds,
            boolean computeDominators,
            shark.OnAnalysisProgressListener listener) {

        listener.onAnalysisProgress(FindingPathsToLeakingInstances.FINDING_PATHS_TO_LEAKING_INSTANCES);
        clearState();
        this.leakingInstanceObjectIds = leakingInstanceObjectIds;

        HeapClass objectClass = graph.findClassByName("java.lang.Object");
        int sizeOfObjectInstances = (objectClass != null) ? getSizeOfObjectInstances(graph, objectClass) : 0;

        Map<String, Map<String, ReferenceMatcher>> fieldNameByClassName = new HashMap<>();
        Map<String, Map<String, ReferenceMatcher>> staticFieldNameByClassName = new HashMap<>();
        Map<String, ReferenceMatcher> threadNames = new HashMap<>();

        for (ReferenceMatcher referenceMatcher : referenceMatchers) {
            ReferencePattern pattern = referenceMatcher.pattern;
            if (isIgnoredOrLibraryLeakReferenceMatcher(referenceMatcher)) {
                switch (pattern.getClass().getName()) {
                    case "shark.ReferencePattern$JavaLocalPattern":
                        threadNames.put(pattern.getThreadName(), referenceMatcher);
                        break;
                    case "shark.StaticFieldPattern":
                        putInStaticFieldNameByClassName(staticFieldNameByClassName, (StaticFieldPattern) pattern, referenceMatcher);
                        break;
                    case "shark.InstanceFieldPattern":
                        putInFieldNameByClassName(fieldNameByClassName, (InstanceFieldPattern) pattern, referenceMatcher);
                        break;
                }
            }
        }

        enqueueGcRoots(graph, threadNames, computeDominators);

        List<ReferencePathNode> shortestPathsToLeakingInstances = new ArrayList<>();
        visitingQueue:
        while (!toVisitQueue.isEmpty() || !toVisitLastQueue.isEmpty()) {
            ReferencePathNode node;
            if (!toVisitQueue.isEmpty()) {
                node = toVisitQueue.poll();
                toVisitSet.remove(node.getInstance());
            } else {
                node = toVisitLastQueue.poll();
                toVisitLastSet.remove(node.getInstance());
            }

            if (checkSeen(node)) {
                throw new IllegalStateException(
                        "Node " + node + " objectId=" + node.getInstance() + " should not be enqueued when already visited or enqueued");
            }

            if (leakingInstanceObjectIds.contains(node.getInstance())) {
                shortestPathsToLeakingInstances.add(node);

                if (shortestPathsToLeakingInstances.size() == leakingInstanceObjectIds.size()) {
                    if (computeDominators) {
                        listener.onAnalysisProgress(FindingPathsToLeakingInstances.FINDING_DOMINATORS);
                    } else {
                        break visitingQueue;
                    }
                }
            }

            Object graphRecord = graph.findObjectById(node.getInstance());
            if (graphRecord instanceof HeapClass) {
                visitClassRecord(graph, (HeapClass) graphRecord, node, staticFieldNameByClassName, computeDominators);
            } else if (graphRecord instanceof HeapInstance) {
                visitInstanceRecord(graph, (HeapInstance) graphRecord, node, fieldNameByClassName, computeDominators);
            } else if (graphRecord instanceof HeapObjectArray) {
                visitObjectArrayRecord(graph, ((HeapObjectArray) graphRecord).readRecord(), node, computeDominators);
            }
        }

        Set<Long> dominatedInstances = this.dominatedInstances;

        clearState();

        return new Results(shortestPathsToLeakingInstances, dominatedInstances);
    }

    private boolean checkSeen(ReferencePathNode node) {
    Boolean neverSeen = visitedSet.add(node.instance);
    return !neverSeen;
    }

    private void clearState() {
        toVisitQueue.clear();
        toVisitLastQueue.clear();
        toVisitSet.clear();
        toVisitLastSet.clear();
        visitedSet.release();
        dominatedInstances = new LongLongScatterMap();
        leakingInstanceObjectIds = Collections.emptySet();
        sizeOfObjectInstances = 0;
    }

    private void enqueueGcRoots(
            HeapGraph graph,
            Map<String, ReferenceMatcher> threadNameReferenceMatchers,
            boolean computeDominators
    ) {
        List<Pair<HeapObject, GcRoot>> gcRoots = sortedGcRoots(graph);

        Map<Integer, Pair<HeapInstance, ThreadObject>> threadsBySerialNumber = new HashMap<>();
        for (Pair<HeapObject, GcRoot> pair : gcRoots) {
            HeapObject objectRecord = pair.getFirst();
            GcRoot gcRoot = pair.getSecond();
            if (computeDominators) {
                undominateWithSkips(graph, gcRoot.id);
            }
            if (gcRoot instanceof ThreadObject) {
                ThreadObject threadObject = (ThreadObject) gcRoot;
                threadsBySerialNumber.put(threadObject.threadSerialNumber, new Pair<>(objectRecord.asInstance, threadObject));
                enqueue(graph, new RootNode(gcRoot, gcRoot.id));
            } else if (gcRoot instanceof JavaFrame) {
                Pair<HeapInstance, ThreadObject> threadPair = threadsBySerialNumber.get(gcRoot.threadSerialNumber);
                HeapInstance threadInstance = threadPair.getFirst();
                ThreadObject threadRoot = threadPair.getSecond();
                String threadName = (String) threadInstance.get(Thread.class, "name").getValue().readAsJavaString();
                ReferenceMatcher referenceMatcher = threadNameReferenceMatchers.get(threadName);

                if (!(referenceMatcher instanceof IgnoredReferenceMatcher)) {
                    RootNode rootNode = new RootNode(gcRoot, threadRoot.id);
                    LeakReference leakReference = new LeakReference(LOCAL, "");
                    Node childNode;
                    if (referenceMatcher instanceof LibraryLeakReferenceMatcher) {
                        childNode = new LibraryLeakNode(gcRoot.id, rootNode, leakReference, (LibraryLeakReferenceMatcher) referenceMatcher);
                    } else {
                        childNode = new NormalNode(gcRoot.id, rootNode, leakReference);
                    }
                    enqueue(graph, childNode);
                }
            } else {
                enqueue(graph, new RootNode(gcRoot, gcRoot.id));
            }
        }
    }

    private List<Pair<HeapObject, GcRoot>> sortedGcRoots(HeapGraph graph) {
        Function<HeapObject, String> rootClassName = graphObject -> {
            if (graphObject instanceof HeapClass) {
                return graphObject.name;
            } else if (graphObject instanceof HeapInstance) {
                return graphObject.instanceClassName;
            } else if (graphObject instanceof HeapObjectArray) {
                return graphObject.arrayClassName;
            } else if (graphObject instanceof HeapPrimitiveArray) {
                return graphObject.arrayClassName;
            }
            return "";
        };

        return graph.gcRoots
                .stream()
                .map(id -> new Pair<>(graph.findObjectById(id), graph.getGcRoot(id)))
                .sorted(Comparator.comparing((Pair<HeapObject, GcRoot> pair) -> pair.getSecond().getClass().getName())
                        .thenComparing(pair -> rootClassName.apply(pair.getFirst())))
                .collect(Collectors.toList());
    }
    private void visitClassRecord(HeapGraph graph, HeapClass heapClass, ReferencePathNode parent, Map<String, Map<String, ReferenceMatcher>> staticFieldNameByClassName, boolean computeRetainedHeapSize) {
    Map<String, ReferenceMatcher> ignoredStaticFields = staticFieldNameByClassName.getOrDefault(heapClass.name, Collections.emptyMap());

    for (StaticField staticField : heapClass.readStaticFields()) {
        if (!staticField.value.isNonNullReference) {
            continue;
        }

        String fieldName = staticField.name;
        if (fieldName.equals("\$staticOverhead")) {
            continue;
        }

        long objectId = staticField.value.asObjectId;

        if (computeRetainedHeapSize) {
            undominateWithSkips(graph, objectId);
        }

        ReferenceMatcher referenceMatcher = ignoredStaticFields.get(fieldName);
        if (referenceMatcher == null) {
            enqueue(graph, new NormalNode(objectId, parent, new LeakReference(STATIC_FIELD, fieldName)));
        } else if (referenceMatcher instanceof LibraryLeakReferenceMatcher) {
            enqueue(graph, new LibraryLeakNode(objectId, parent, new LeakReference(STATIC_FIELD, fieldName), (LibraryLeakReferenceMatcher) referenceMatcher));
        }
    }
}

private void visitInstanceRecord(HeapGraph graph, HeapInstance instance, ReferencePathNode parent, Map<String, Map<String, ReferenceMatcher>> fieldNameByClassName, boolean computeRetainedHeapSize) {
    Map<String, ReferenceMatcher> fieldReferenceMatchers = new LinkedHashMap<>();

    for (ClassHierarchy classHierarchyItem : instance.instanceClass.classHierarchy) {
        Map<String, ReferenceMatcher> referenceMatcherByField = fieldNameByClassName.get(classHierarchyItem.name);
        if (referenceMatcherByField != null) {
            for (Map.Entry<String, ReferenceMatcher> entry : referenceMatcherByField.entrySet()) {
                if (!fieldReferenceMatchers.containsKey(entry.getKey())) {
                    fieldReferenceMatchers.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    List<FieldNameAndValue> fieldNamesAndValues = new ArrayList<>(instance.readFields());
    fieldNamesAndValues.sort(Comparator.comparing(FieldNameAndValue::name));

    for (FieldNameAndValue field : fieldNamesAndValues) {
        if (!field.value.isNonNullReference) {
            continue;
        }

        long objectId = field.value.asObjectId;
        if (computeRetainedHeapSize) {
            updateDominatorWithSkips(graph, parent.instance, objectId);
        }

        ReferenceMatcher referenceMatcher = fieldReferenceMatchers.get(field.name);
        if (referenceMatcher == null) {
            enqueue(graph, new NormalNode(objectId, parent, new LeakReference(INSTANCE_FIELD, field.name)));
        } else if (referenceMatcher instanceof LibraryLeakReferenceMatcher) {
            enqueue(graph, new LibraryLeakNode(objectId, parent, new LeakReference(INSTANCE_FIELD, field.name), (LibraryLeakReferenceMatcher) referenceMatcher));
        }
    }
}

private void visitObjectArrayRecord(HeapGraph graph, ObjectArrayDumpRecord record, ReferencePathNode parentNode, boolean computeRetainedHeapSize) {
    List<Long> elementIds = record.elementIds.stream()
            .filter(objectId -> objectId != ValueHolder.NULL_REFERENCE && graph.objectExists(objectId))
            .collect(Collectors.toList());

    IntStream.range(0, elementIds.size())
            .forEach(index -> {
                long elementId = elementIds.get(index);
                if (computeRetainedHeapSize) {
                    updateDominatorWithSkips(graph, parentNode.instance, elementId);
                }
                String name = String.valueOf(index);
                enqueue(graph, new NormalNode(elementId, parentNode, new LeakReference(ARRAY_ENTRY, name)));
            });
}

private void enqueue(HeapGraph graph, ReferencePathNode node) {
    if (node.instance == ValueHolder.NULL_REFERENCE) {
        return;
    }
    if (visitedSet.contains(node.instance)) {
        return;
    }

    if (toVisitSet.contains(node.instance)) {
        return;
    }

    if (toVisitLastSet.contains(node.instance)) {
        if (node instanceof LibraryLeakNode) {
            return;
        } else {
            toVisitQueue.add(node);
            toVisitSet.add(node.instance);
            ReferencePathNode nodeToRemove = null;
            for (ReferencePathNode n : toVisitLastQueue) {
                if (n.instance == node.instance) {
                    nodeToRemove = n;
                    break;
                }
            }
            toVisitLastQueue.remove(nodeToRemove);
            toVisitLastSet.remove(node.instance);
            return;
        }
    }

    boolean isLeakingInstance = leakingInstanceObjectIds.contains(node.instance);

    if (!isLeakingInstance) {
        boolean skip = false;
        if (graph.findObjectById(node.instance) instanceof HeapClass) {
            skip = false;
        } else if (graph.findObjectById(node.instance) instanceof HeapInstance) {
            HeapInstance graphObject = (HeapInstance) graph.findObjectById(node.instance);
            if (graphObject.isPrimitiveWrapper) {
                skip = true;
            } else if (graphObject.instanceClassName.equals("java.lang.String")) {
                skip = true;
            } else if (graphObject.instanceClass.instanceByteSize <= sizeOfObjectInstances) {
                skip = true;
            } else {
                skip = false;
            }
        } else if (graph.findObjectById(node.instance) instanceof HeapObjectArray) {
            HeapObjectArray graphObject = (HeapObjectArray) graph.findObjectById(node.instance);
            if (graphObject.isPrimitiveWrapperArray) {
                skip = true;
            } else {
                skip = false;
            }
        } else if (graph.findObjectById(node.instance) instanceof HeapPrimitiveArray) {
            skip = true;
        }
        if (skip) {
            return;
        }
    }
    if (node instanceof LibraryLeakNode) {
        toVisitLastQueue.add(node);
        toVisitLastSet.add(node.instance);
    } else {
        toVisitQueue.add(node);
        toVisitSet.add(node.instance);
    }
}

private void updateDominatorWithSkips(HeapGraph graph, long parentObjectId, long objectId) {
    Object graphObject = graph.findObjectById(objectId);

    if (graphObject instanceof HeapClass) {
        undominate(objectId, false);
    } else if (graphObject instanceof HeapInstance) {
        HeapInstance heapInstance = (HeapInstance) graphObject;

        if (heapInstance.instanceClassName.equals("java.lang.String")) {
            updateDominator(parentObjectId, objectId, true);
            Long valueId = getValueId(heapInstance, "java.lang.String", "value");
            if (valueId != null) {
                updateDominator(parentObjectId, valueId, true);
            }
        } else {
            updateDominator(parentObjectId, objectId, false);
        }
    } else if (graphObject instanceof HeapObjectArray) {
        HeapObjectArray heapObjectArray = (HeapObjectArray) graphObject;

        if (heapObjectArray.isPrimitiveWrapperArray) {
            updateDominator(parentObjectId, objectId, true);
            for (long wrapperId : heapObjectArray.readRecord().elementIds) {
                updateDominator(parentObjectId, wrapperId, true);
            }
        } else {
            updateDominator(parentObjectId, objectId, false);
        }
    } else {
        updateDominator(parentObjectId, objectId, false);
    }
}

private void updateDominator(long parent, long instance, boolean neverEnqueued) {
    Long currentDominator = dominatedInstances.get(instance);
    if (currentDominator == null && (dominatedSet.contains(instance) || toVisitSet.contains(instance) || toVisitLastSet.contains(instance))) {
        return;
    }
    Long parentDominator = dominatedInstances.get(parent);

    Long nextDominator = parentDominator;
    if (parentDominator != null && leakingInstanceObjectIds.contains(parent)) {
        nextDominator = parent;
    }

    if (nextDominator == null) {
        if (neverEnqueued) {
            visitedSet.add(instance);
        }

        if (currentDominator != null) {
            dominatedInstances.remove(instance);
        }
        return;
    }
    if (currentDominator == null) {
        dominatedInstances.put(instance, nextDominator);
    } else {
        List<Long> parentDominators = new ArrayList<>();
        List<Long> currentDominators = new ArrayList<>();
        Long dominator = nextDominator;
        while (dominator != null) {
            parentDominators.add(dominator);
            dominator = dominatedInstances.get(dominator);
        }
        dominator = currentDominator;
        while (dominator != null) {
            currentDominators.add(dominator);
            dominator = dominatedInstances.get(dominator);
        }

        Long sharedDominator = null;
        for (Long parentD : parentDominators) {
            for (Long currentD : currentDominators) {
                if (currentD.equals(parentD)) {
                    sharedDominator = currentD;
                    break;
                }
            }
            if (sharedDominator != null) {
                break;
            }
        }
        if (sharedDominator == null) {
            dominatedInstances.remove(instance);
            if (neverEnqueued) {
                visitedSet.add(instance);
            }
        } else {
            dominatedInstances.put(instance, sharedDominator);
        }
    }
}

private void undominateWithSkips(HeapGraph graph, long objectId) {
    Object graphObject = graph.findObjectById(objectId);

    if (graphObject instanceof HeapClass) {
        undominate(objectId, false);
    } else if (graphObject instanceof HeapInstance) {
        HeapInstance heapInstance = (HeapInstance) graphObject;

        if (heapInstance.instanceClassName.equals("java.lang.String")) {
            undominate(objectId, true);
            Long valueId = getValueId(heapInstance, "java.lang.String", "value");
            if (valueId != null) {
                undominate(valueId, true);
            }
        } else {
            undominate(objectId, false);
        }
    } else if (graphObject instanceof HeapObjectArray) {
        HeapObjectArray heapObjectArray = (HeapObjectArray) graphObject;

        if (heapObjectArray.isPrimitiveWrapperArray) {
            undominate(objectId, true);
            for (long wrapperId : heapObjectArray.readRecord().elementIds) {
                undominate(wrapperId, true);
            }
        } else {
            undominate(objectId, false);
        }
    } else {
        undominate(objectId, false);
    }
}

private void undominate(long instance, boolean neverEnqueued) {
    dominatedInstances.remove(instance);
    if (neverEnqueued) {
        visitedSet.add(instance);
    }
}
}