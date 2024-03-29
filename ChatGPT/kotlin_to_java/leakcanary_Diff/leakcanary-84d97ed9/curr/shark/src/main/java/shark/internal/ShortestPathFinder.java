
package shark.internal;

import shark.OnAnalysisProgressListener.Step;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

class ShortestPathFinder {

    private final Deque<ReferencePathNode> toVisitQueue = new ArrayDeque<>();
    private final Deque<LibraryLeakNode> toVisitLastQueue = new ArrayDeque<>();

    private final HashSet<Long> toVisitSet = new HashSet<>();
    private final HashSet<Long> toVisitLastSet = new HashSet<>();

    private final LongScatterSet visitedSet = new LongScatterSet();
    private Set<Long> leakingInstanceObjectIds;

    private LongLongScatterMap dominatedInstances = new LongLongScatterMap();
    private int sizeOfObjectInstances;

    static class Results {
        final List<ReferencePathNode> shortestPathsToLeakingInstances;
        final LongLongScatterMap dominatedInstances;

        Results(List<ReferencePathNode> shortestPathsToLeakingInstances, LongLongScatterMap dominatedInstances) {
            this.shortestPathsToLeakingInstances = shortestPathsToLeakingInstances;
            this.dominatedInstances = dominatedInstances;
        }
    }

    Results findPaths(
            HeapGraph graph,
            List<ReferenceMatcher> referenceMatchers,
            Set<Long> leakingInstanceObjectIds,
            boolean computeDominators,
            shark.OnAnalysisProgressListener listener
    ) {
        listener.onAnalysisProgress(Step.FINDING_PATHS_TO_LEAKING_INSTANCES);
        clearState();
        this.leakingInstanceObjectIds = leakingInstanceObjectIds;

        HeapClass objectClass = graph.findClassByName("java.lang.Object");
        sizeOfObjectInstances = (objectClass != null) ? objectClass.readFieldsByteSize() == (graph.identifierByteSize + PrimitiveType.INT.byteSize) ? graph.identifierByteSize + PrimitiveType.INT.byteSize : 0 : 0;

        Map<String, Map<String, ReferenceMatcher>> fieldNameByClassName = new LinkedHashMap<>();
        Map<String, Map<String, ReferenceMatcher>> staticFieldNameByClassName = new LinkedHashMap<>();
        Map<String, ReferenceMatcher> threadNames = new LinkedHashMap<>();

        referenceMatchers.stream()
                .filter(it -> (it instanceof IgnoredReferenceMatcher || (it instanceof LibraryLeakReferenceMatcher && ((LibraryLeakReferenceMatcher) it).patternApplies(graph))))
                .forEach(referenceMatcher -> {
                    ReferencePattern pattern = referenceMatcher.pattern;
                    if (pattern instanceof ReferencePattern.JavaLocalPattern) {
                        threadNames.put(((ReferencePattern.JavaLocalPattern) pattern).threadName, referenceMatcher);
                    } else if (pattern instanceof StaticFieldPattern) {
                        String className = ((StaticFieldPattern) pattern).className;
                        Map<String, ReferenceMatcher> map = staticFieldNameByClassName.computeIfAbsent(className, k -> new LinkedHashMap<>());
                        map.put(((StaticFieldPattern) pattern).fieldName, referenceMatcher);
                    } else if (pattern instanceof InstanceFieldPattern) {
                        String className = ((InstanceFieldPattern) pattern).className;
                        Map<String, ReferenceMatcher> map = fieldNameByClassName.computeIfAbsent(className, k -> new LinkedHashMap<>());
                        map.put(((InstanceFieldPattern) pattern).fieldName, referenceMatcher);
                    }
                });

        enqueueGcRoots(graph, threadNames, computeDominators);

        List<ReferencePathNode> shortestPathsToLeakingInstances = new ArrayList<>();
        visitingQueue:
        while (!toVisitQueue.isEmpty() || !toVisitLastQueue.isEmpty()) {
            ReferencePathNode node = (!toVisitQueue.isEmpty()) ? toVisitQueue.poll() : toVisitLastQueue.poll();
            if (checkSeen(node)) {
                throw new IllegalStateException("Node " + node + " objectId=" + node.instance + " should not be enqueued when already visited or enqueued");
            }
            if (leakingInstanceObjectIds.contains(node.instance)) {
                shortestPathsToLeakingInstances.add(node);
                if (shortestPathsToLeakingInstances.size() == leakingInstanceObjectIds.size()) {
                    if (computeDominators) {
                        listener.onAnalysisProgress(Step.FINDING_DOMINATORS);
                    } else {
                        break visitingQueue;
                    }
                }
            }
            HeapObject graphRecord = graph.findObjectById(node.instance);
            if (graphRecord instanceof HeapClass) {
                visitClassRecord(graph, (HeapClass) graphRecord, node, staticFieldNameByClassName, computeDominators);
            } else if (graphRecord instanceof HeapInstance) {
                visitInstanceRecord(graph, (HeapInstance) graphRecord, node, fieldNameByClassName, computeDominators);
            } else if (graphRecord instanceof HeapObjectArray) {
                visitObjectArrayRecord(graph, ((HeapObjectArray) graphRecord).readRecord(), node, computeDominators);
            }
        }

        LongLongScatterMap dominatedInstances = this.dominatedInstances;

        clearState();

        return new Results(shortestPathsToLeakingInstances, dominatedInstances);
    }

    private boolean checkSeen(ReferencePathNode node) {
        boolean neverSeen = visitedSet.add(node.instance);
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

        Map<Integer, Pair<HeapInstance, ThreadObject>> threadsBySerialNumber = new LinkedHashMap<>();
        gcRoots.forEach(pair -> {
            ObjectRecord objectRecord = pair.getFirst();
            GcRoot gcRoot = pair.getSecond();
            if (computeDominators) {
                undominateWithSkips(graph, gcRoot.id);
            }
            if (gcRoot instanceof ThreadObject) {
                ThreadObject threadObject = (ThreadObject) gcRoot;
                threadsBySerialNumber.put(threadObject.threadSerialNumber, new Pair<>(objectRecord.asInstance, threadObject));
                enqueue(graph, new RootNode(gcRoot, gcRoot.id));
            } else if (gcRoot instanceof JavaFrame) {
                JavaFrame javaFrame = (JavaFrame) gcRoot;
                Pair<HeapInstance, ThreadObject> pair = threadsBySerialNumber.get(javaFrame.threadSerialNumber);
                HeapInstance threadInstance = pair.getFirst();
                ThreadObject threadRoot = pair.getSecond();
                String threadName = threadInstance.get(Thread.class, "name").value.readAsJavaString();
                ReferenceMatcher referenceMatcher = threadNameReferenceMatchers.get(threadName);
                if (!(referenceMatcher instanceof IgnoredReferenceMatcher)) {
                    RootNode rootNode = new RootNode(gcRoot, threadRoot.id);
                    LeakReference leakReference = new LeakReference(Type.LOCAL, "");
                    ChildNode childNode = (referenceMatcher instanceof LibraryLeakReferenceMatcher) ?
                            new LibraryLeakNode(gcRoot.id, rootNode, leakReference, (LibraryLeakReferenceMatcher) referenceMatcher) :
                            new NormalNode(gcRoot.id, rootNode, leakReference);
                    enqueue(graph, childNode);
                }
            } else {
                enqueue(graph, new RootNode(gcRoot, gcRoot.id));
            }
        });
    }

    private List<Pair<HeapObject, GcRoot>> sortedGcRoots(HeapGraph graph) {
        Function<HeapObject, String> rootClassName = graphObject -> {
            if (graphObject instanceof HeapClass) {
                return ((HeapClass) graphObject).name;
            } else if (graphObject instanceof HeapInstance) {
                return ((HeapInstance) graphObject).instanceClassName;
            } else if (graphObject instanceof HeapObjectArray) {
                return ((HeapObjectArray) graphObject).arrayClassName;
            } else if (graphObject instanceof HeapPrimitiveArray) {
                return ((HeapPrimitiveArray) graphObject).arrayClassName;
            }
            return "";
        };

        return graph.gcRoots.stream()
                .map(id -> new Pair<>(graph.findObjectById(id.id), id))
                .sorted(Comparator.comparing((Pair<HeapObject, GcRoot> pair) -> pair.getSecond().getClass().getName())
                        .thenComparing(pair -> rootClassName.apply(pair.getFirst())))
                .collect(Collectors.toList());
    }

    private void visitClassRecord(
            HeapGraph graph,
            HeapClass heapClass,
            ReferencePathNode parent,
            Map<String, Map<String, ReferenceMatcher>> staticFieldNameByClassName,
            boolean computeRetainedHeapSize
    ) {
        Map<String, ReferenceMatcher> ignoredStaticFields = staticFieldNameByClassName.getOrDefault(heapClass.name, Collections.emptyMap());

        for (Field staticField : heapClass.readStaticFields()) {
            if (!staticField.value.isNonNullReference) {
                continue;
            }
            String fieldName = staticField.name;
            if (fieldName.equals("$staticOverhead")) {
                continue;
            }
            long objectId = staticField.value.asObjectId;
            if (computeRetainedHeapSize) {
                undominateWithSkips(graph, objectId);
            }
            ReferenceMatcher referenceMatcher = ignoredStaticFields.get(fieldName);
            if (referenceMatcher == null) {
                enqueue(graph, new NormalNode(objectId, parent, new LeakReference(Type.STATIC_FIELD, fieldName)));
            } else if (referenceMatcher instanceof LibraryLeakReferenceMatcher) {
                enqueue(graph, new LibraryLeakNode(objectId, parent, new LeakReference(Type.STATIC_FIELD, fieldName), (LibraryLeakReferenceMatcher) referenceMatcher));
            }
        }
    }

    private void visitInstanceRecord(
            HeapGraph graph,
            HeapInstance instance,
            ReferencePathNode parent,
            Map<String, Map<String, ReferenceMatcher>> fieldNameByClassName,
            boolean computeRetainedHeapSize
    ) {
        Map<String, ReferenceMatcher> fieldReferenceMatchers = new LinkedHashMap<>();

        instance.instanceClass.classHierarchy.forEach(clazz -> {
            Map<String, ReferenceMatcher> referenceMatcherByField = fieldNameByClassName.get(clazz.name);
            if (referenceMatcherByField != null) {
                referenceMatcherByField.forEach((fieldName, referenceMatcher) -> fieldReferenceMatchers.putIfAbsent(fieldName, referenceMatcher));
            }
        });

        List<FieldHolder> fieldNamesAndValues = new ArrayList<>(instance.readFields());
        fieldNamesAndValues.sort(Comparator.comparing(FieldHolder::getName));

        fieldNamesAndValues.stream()
                .filter(field -> field.value.isNonNullReference)
                .forEach(field -> {
                    long objectId = field.value.asObjectId;
                    if (computeRetainedHeapSize) {
                        updateDominatorWithSkips(graph, parent.instance, objectId);
                    }
                    ReferenceMatcher referenceMatcher = fieldReferenceMatchers.get(field.name);
                    if (referenceMatcher == null) {
                        enqueue(graph, new NormalNode(objectId, parent, new LeakReference(Type.INSTANCE_FIELD, field.name)));
                    } else if (referenceMatcher instanceof LibraryLeakReferenceMatcher) {
                        enqueue(graph, new LibraryLeakNode(objectId, parent, new LeakReference(Type.INSTANCE_FIELD, field.name), (LibraryLeakReferenceMatcher) referenceMatcher));
                    }
                });
    }

    private void visitObjectArrayRecord(
            HeapGraph graph,
            ObjectArrayDumpRecord record,
            ReferencePathNode parentNode,
            boolean computeRetainedHeapSize
    ) {
        record.elementIds.stream()
                .filter(elementId -> elementId != ValueHolder.NULL_REFERENCE && graph.objectExists(elementId))
                .forEachOrdered((elementId, index) -> {
                    if (computeRetainedHeapSize) {
                        updateDominatorWithSkips(graph, parentNode.instance, elementId);
                    }
                    String name = Integer.toString(index);
                    enqueue(graph, new NormalNode(elementId, parentNode, new LeakReference(Type.ARRAY_ENTRY, name)));
                });
    }

   private void enqueue(HeapGraph graph, ReferencePathNode node) {
    if (node.instance == ValueHolder.NULL_REFERENCE || visitedSet.contains(node.instance) || toVisitSet.contains(node.instance) || toVisitLastSet.contains(node.instance)) {
        return;
    }
    boolean isLeakingInstance = leakingInstanceObjectIds.contains(node.instance);
    if (!isLeakingInstance) {
        HeapObject heapObject = graph.findObjectById(node.instance);
        boolean skip = false;
        if (heapObject instanceof HeapInstance) {
            HeapInstance graphObject = (HeapInstance) heapObject;
            if (graphObject.isPrimitiveWrapper || "java.lang.String".equals(graphObject.instanceClassName) || graphObject.instanceClass.instanceByteSize <= sizeOfObjectInstances) {
                skip = true;
            }
        } else if (heapObject instanceof HeapObjectArray) {
            HeapObjectArray graphObject = (HeapObjectArray) heapObject;
            if (graphObject.isPrimitiveWrapperArray) {
                skip = true;
            }
        } else if (heapObject instanceof HeapPrimitiveArray) {
            skip = true;
        }
        if (skip) {
            return;
        }
    }
    if (node instanceof LibraryLeakNode) {
        toVisitLastQueue.add((LibraryLeakNode) node);
        toVisitLastSet.add(node.instance);
    } else {
        toVisitQueue.add(node);
        toVisitSet.add(node.instance);
    }
}

    private void updateDominatorWithSkips(HeapGraph graph, long parentObjectId, long objectId) {
    HeapObject heapObject = graph.findObjectById(objectId);
    if (heapObject instanceof HeapClass) {
        undominate(objectId, false);
    } else if (heapObject instanceof HeapInstance) {
        HeapInstance graphObject = (HeapInstance) heapObject;
        if ("java.lang.String".equals(graphObject.instanceClassName)) {
            updateDominator(parentObjectId, objectId, true);
            long valueId = graphObject.get("java.lang.String", "value").value.asObjectId;
            if (valueId != ValueHolder.NULL_REFERENCE) {
                updateDominator(parentObjectId, valueId, true);
            }
        } else {
            updateDominator(parentObjectId, objectId, false);
        }
    } else if (heapObject instanceof HeapObjectArray) {
        HeapObjectArray graphObject = (HeapObjectArray) heapObject;
        if (graphObject.isPrimitiveWrapperArray) {
            updateDominator(parentObjectId, objectId, true);
            graphObject.readRecord().elementIds.forEach(wrapperId -> updateDominator(parentObjectId, wrapperId, true));
        } else {
            updateDominator(parentObjectId, objectId, false);
        }
    } else {
        updateDominator(parentObjectId, objectId, false);
    }
}

    private void updateDominator(long parent, long instance, boolean neverEnqueued) {
        Long currentDominator = dominatedInstances.get(instance);
        if (currentDominator == null && (visitedSet.contains(instance) || toVisitSet.contains(instance) || toVisitLastSet.contains(instance))) {
            return;
        }
        Long parentDominator = dominatedInstances.get(parent);

        boolean parentIsRetainedInstance = leakingInstanceObjectIds.contains(parent);

        long nextDominator = parentIsRetainedInstance ? parent : parentDominator;

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
            exit:
            for (Long parentD : parentDominators) {
                for (Long currentD : currentDominators) {
                    if (currentD.equals(parentD)) {
                        sharedDominator = currentD;
                        break exit;
                    }
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
    HeapObject heapObject = graph.findObjectById(objectId);
    if (heapObject instanceof HeapClass) {
        undominate(objectId, false);
    } else if (heapObject instanceof HeapInstance) {
        HeapInstance graphObject = (HeapInstance) heapObject;
        if ("java.lang.String".equals(graphObject.instanceClassName)) {
            undominate(objectId, true);
            long valueId = graphObject.get("java.lang.String", "value").value.asObjectId;
            if (valueId != ValueHolder.NULL_REFERENCE) {
                undominate(valueId, true);
            }
        } else {
            undominate(objectId, false);
        }
    } else if (heapObject instanceof HeapObjectArray) {
        HeapObjectArray graphObject = (HeapObjectArray) heapObject;
        if (graphObject.isPrimitiveWrapperArray) {
            undominate(objectId, true);
            graphObject.readRecord().elementIds.forEach(wrapperId -> undominate(wrapperId, true));
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