package shark;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import shark.LeakTrace.GcRootType;
import shark.LeakTraceObject.LeakingStatus;
import shark.LeakTraceObject.ObjectType;
import shark.internal.PathFinder;
import shark.internal.PathFinder.PathFindingResults;
import shark.internal.ReferencePathNode;
import shark.internal.ReferencePathNode.ChildNode;
import shark.internal.ReferencePathNode.LibraryLeakNode;
import shark.internal.ReferencePathNode.RootNode;
import shark.internal.createSHA1Hash;
import shark.internal.hppc.LongLongScatterMap;
import shark.internal.hppc.LongLongScatterMap.ForEachCallback;
import shark.internal.lastSegment;

public class HeapAnalyzer {
    private final OnAnalysisProgressListener listener;

    private static class FindLeakInput {
        final HeapGraph graph;
        final List<ReferenceMatcher> referenceMatchers;
        final boolean computeRetainedHeapSize;
        final List<ObjectInspector> objectInspectors;

        FindLeakInput(
                HeapGraph graph,
                List<ReferenceMatcher> referenceMatchers,
                boolean computeRetainedHeapSize,
                List<ObjectInspector> objectInspectors
        ) {
            this.graph = graph;
            this.referenceMatchers = referenceMatchers;
            this.computeRetainedHeapSize = computeRetainedHeapSize;
            this.objectInspectors = objectInspectors;
        }
    }

    public HeapAnalyzer(OnAnalysisProgressListener listener) {
        this.listener = listener;
    }

    public HeapAnalysis analyze(
            File heapDumpFile,
            LeakingObjectFinder leakingObjectFinder,
            List<ReferenceMatcher> referenceMatchers,
            boolean computeRetainedHeapSize,
            List<ObjectInspector> objectInspectors,
            MetadataExtractor metadataExtractor,
            ProguardMapping proguardMapping
    ) {
        long analysisStartNanoTime = System.nanoTime();

        if (!heapDumpFile.exists()) {
            IllegalArgumentException exception = new IllegalArgumentException("File does not exist: " + heapDumpFile);
            return new HeapAnalysisFailure(
                    heapDumpFile, System.currentTimeMillis(), since(analysisStartNanoTime),
                    new HeapAnalysisException(exception)
            );
        }

        try {
            listener.onAnalysisProgress(OnAnalysisProgressListener.Step.PARSING_HEAP_DUMP);
            try (HeapGraph graph = heapDumpFile.openHeapGraph(proguardMapping)) {
                FindLeakInput helpers = new FindLeakInput(graph, referenceMatchers, computeRetainedHeapSize, objectInspectors);
                return helpers.analyzeGraph(metadataExtractor, leakingObjectFinder, heapDumpFile, analysisStartNanoTime);
            }
        } catch (Throwable exception) {
            return new HeapAnalysisFailure(
                    heapDumpFile, System.currentTimeMillis(), since(analysisStartNanoTime),
                    new HeapAnalysisException(exception)
            );
        }
    }

    public HeapAnalysis analyze(
            File heapDumpFile,
            HeapGraph graph,
            LeakingObjectFinder leakingObjectFinder,
            List<ReferenceMatcher> referenceMatchers,
            boolean computeRetainedHeapSize,
            List<ObjectInspector> objectInspectors,
            MetadataExtractor metadataExtractor
    ) {
        long analysisStartNanoTime = System.nanoTime();
        try {
            FindLeakInput helpers = new FindLeakInput(graph, referenceMatchers, computeRetainedHeapSize, objectInspectors);
            return helpers.analyzeGraph(metadataExtractor, leakingObjectFinder, heapDumpFile, analysisStartNanoTime);
        } catch (Throwable exception) {
            return new HeapAnalysisFailure(
                    heapDumpFile, System.currentTimeMillis(), since(analysisStartNanoTime),
                    new HeapAnalysisException(exception)
            );
        }
    }

    

   

    private static long since(long analysisStartNanoTime) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime);
    }

    private static class TrieNode {
        final long objectId;
        int retainedSize = 0;

        TrieNode(long objectId) {
            this.objectId = objectId;
        }
    }

    private static class ParentNode extends TrieNode {
        final Map<Long, TrieNode> children = new LinkedHashMap<>();

        ParentNode(long objectId) {
            super(objectId);
        }

        @Override
        public String toString() {
            return "ParentNode(objectId=" + objectId + ", children=" + children + ")";
        }
    }

    private static class LeafNode extends TrieNode {
        final ReferencePathNode pathNode;

        LeafNode(long objectId, ReferencePathNode pathNode) {
            super(objectId);
            this.pathNode = pathNode;
        }
    }

    private HeapAnalysisSuccess analyzeGraph(
            MetadataExtractor metadataExtractor,
            LeakingObjectFinder leakingObjectFinder,
            File heapDumpFile,
            long analysisStartNanoTime
    ) {
        listener.onAnalysisProgress(OnAnalysisProgressListener.Step.EXTRACTING_METADATA);
        Metadata metadata = metadataExtractor.extractMetadata(graph);

        listener.onAnalysisProgress(OnAnalysisProgressListener.Step.FINDING_RETAINED_OBJECTS);
        Set<Long> leakingObjectIds = leakingObjectFinder.findLeakingObjectIds(graph);

        Pair<List<ApplicationLeak>, List<LibraryLeak>> leaks = findLeaks(leakingObjectIds);

        return new HeapAnalysisSuccess(
                heapDumpFile, System.currentTimeMillis(), since(analysisStartNanoTime),
                metadata, leaks.getFirst(), leaks.getSecond()
        );
    }

    private Pair<List<ApplicationLeak>, List<LibraryLeak>> findLeaks(Set<Long> leakingObjectIds) {
        PathFinder pathFinder = new PathFinder(graph, listener, referenceMatchers);
        PathFindingResults pathFindingResults = pathFinder.findPathsFromGcRoots(leakingObjectIds, computeRetainedHeapSize);

        SharkLog.d(() -> "Found " + leakingObjectIds.size() + " retained objects");

        return buildLeakTraces(pathFindingResults);
    }

    private List<Pair<ReferencePathNode, Integer>> deduplicateShortestPaths(List<ReferencePathNode> inputPathResults, List<Integer> retainedSizes) {
        ParentNode rootTrieNode = new ParentNode(0);

        for (int index = 0; index < inputPathResults.size(); index++) {
            ReferencePathNode pathNode = inputPathResults.get(index);
            List<Long> path = new ArrayList<>();
            ReferencePathNode leakNode = pathNode;
            while (leakNode instanceof ChildNode) {
                path.add(0, leakNode.objectId);
                leakNode = leakNode.parent;
            }
            path.add(0, leakNode.objectId);
            updateTrie(pathNode, path, 0, rootTrieNode, retainedSizes != null ? retainedSizes.get(index) : 0);
        }

        List<Pair<ReferencePathNode, Integer>> outputPathResults = new ArrayList<>();
        findResultsInTrie(rootTrieNode, outputPathResults);
        return

 outputPathResults;
    }

    private void updateTrie(
            ReferencePathNode pathNode,
            List<Long> path,
            int pathIndex,
            ParentNode parentNode,
            int retainedSize
    ) {
        long objectId = path.get(pathIndex);
        if (pathIndex == path.size() - 1) {
            int previousRetained = parentNode.children.containsKey(objectId) ? parentNode.children.get(objectId).retainedSize : 0;
            LeafNode leafNode = new LeafNode(objectId, pathNode);
            leafNode.retainedSize = previousRetained + retainedSize;
            parentNode.children.put(objectId, leafNode);
        } else {
            TrieNode childNode = parentNode.children.computeIfAbsent(objectId, k -> new ParentNode(objectId));
            childNode.retainedSize += retainedSize;
            if (childNode instanceof ParentNode) {
                updateTrie(pathNode, path, pathIndex + 1, (ParentNode) childNode, retainedSize);
            }
        }
    }

    private void findResultsInTrie(ParentNode parentNode, List<Pair<ReferencePathNode, Integer>> outputPathResults) {
        for (TrieNode childNode : parentNode.children.values()) {
            if (childNode instanceof ParentNode) {
                findResultsInTrie((ParentNode) childNode, outputPathResults);
            } else if (childNode instanceof LeafNode) {
                outputPathResults.add(new Pair<>(((LeafNode) childNode).pathNode, childNode.retainedSize));
            }
        }
    }

    private List<Integer> computeRetainedSizes(PathFindingResults pathFindingResults) {
        if (!computeRetainedHeapSize) {
            return null;
        }
        List<ReferencePathNode> pathsToLeakingInstances = pathFindingResults.pathsToLeakingObjects;
        LongLongScatterMap dominatedInstances = pathFindingResults.dominatedObjectIds;

        listener.onAnalysisProgress(OnAnalysisProgressListener.Step.COMPUTING_NATIVE_RETAINED_SIZE);

        Map<Long, Integer> nativeSizes = new LinkedHashMap<>();

        for (HeapInstance cleaner : graph.instances) {
            FieldReference thunkField = cleaner["sun.misc.Cleaner", "thunk"];
            Long thunkId = thunkField != null ? thunkField.value.asNonNullObjectId : null;
            Long referentId = cleaner["java.lang.ref.Reference", "referent"] != null ? cleaner["java.lang.ref.Reference", "referent"].value.asNonNullObjectId : null;
            if (thunkId != null && referentId != null) {
                HeapInstance thunkRecord = (HeapInstance) thunkField.value.asObject;
                if (thunkRecord != null && thunkRecord.instanceOf("libcore.util.NativeAllocationRegistry$CleanerThunk")) {
                    FieldReference allocationRegistryIdField = thunkRecord["libcore.util.NativeAllocationRegistry$CleanerThunk", "this$0"];
                    if (allocationRegistryIdField != null && allocationRegistryIdField.value.isNonNullReference) {
                        HeapInstance allocationRegistryRecord = (HeapInstance) allocationRegistryIdField.value.asObject;
                        if (allocationRegistryRecord != null && allocationRegistryRecord.instanceOf("libcore.util.NativeAllocationRegistry")) {
                            int nativeSize = nativeSizes.getOrDefault(referentId, 0);
                            nativeSize += allocationRegistryRecord["libcore.util.NativeAllocationRegistry", "size"] != null ? (int) (long) allocationRegistryRecord["libcore.util.NativeAllocationRegistry", "size"].value.asLong : 0;
                            nativeSizes.put(referentId, nativeSize);
                        }
                    }
                }
            }
        }

        listener.onAnalysisProgress(OnAnalysisProgressListener.Step.COMPUTING_RETAINED_SIZE);

        Map<Long, Integer> sizeByDominator = new LinkedHashMap<>();

        Set<Long> leakingInstanceIds = new HashSet<>();
        for (ReferencePathNode pathNode : pathsToLeakingInstances) {
            long leakingInstanceObjectId = pathNode.objectId;
            leakingInstanceIds.add(leakingInstanceObjectId);

            int shallowSize;
            HeapObject heapObject = graph.findObjectById(leakingInstanceObjectId);
            if (heapObject instanceof HeapInstance) {
                shallowSize = ((HeapInstance) heapObject).byteSize;
            } else if (heapObject instanceof HeapObjectArray) {
                shallowSize = ((HeapObjectArray) heapObject).readByteSize();
            } else if (heapObject instanceof HeapPrimitiveArray) {
                shallowSize = ((HeapPrimitiveArray) heapObject).readByteSize();
            } else {
                throw new IllegalStateException("Unexpected class record " + heapObject);
            }

            int retainedSize = sizeByDominator.getOrDefault(leakingInstanceObjectId, 0);
            retainedSize += shallowSize;
            sizeByDominator.put(leakingInstanceObjectId, retainedSize);
        }

        dominatedInstances.forEach(new ForEachCallback() {
            @Override
            public void onEntry(long instanceId, long dominatorId) {
                if (!leakingInstanceIds.contains(instanceId)) {
                    int currentSize = sizeByDominator.getOrDefault(dominatorId, 0);
                    int nativeSize = nativeSizes.getOrDefault(instanceId, 0);
                    int shallowSize;
                    HeapObject heapObject = graph.findObjectById(instanceId);
                    if (heapObject instanceof HeapInstance) {
                        shallowSize = ((HeapInstance) heapObject).byteSize;
                    } else if (heapObject instanceof HeapObjectArray) {
                        shallowSize = ((HeapObjectArray) heapObject).readByteSize();
                    } else if (heapObject instanceof HeapPrimitiveArray) {
                        shallowSize = ((HeapPrimitiveArray) heapObject).readByteSize();
                    } else {
                        throw new IllegalStateException("Unexpected class record " + heapObject);
                    }
                    sizeByDominator.put(dominatorId, currentSize + nativeSize + shallowSize);
                }
            }
        });

        boolean sizedMoved;
        do {
            sizedMoved = false;
            for (ReferencePathNode pathNode : pathsToLeakingInstances) {
                long leakingInstanceId = pathNode.objectId;
                int dominatorSlot = dominatedInstances.getSlot(leakingInstanceId);
                if (dominatorSlot != -1) {
                    long dominator = dominatedInstances.getSlotValue(dominatorSlot);
                    int retainedSize = sizeByDominator.getOrDefault(leakingInstanceId, 0);
                    if (retainedSize > 0) {
                        sizeByDominator.put(leakingInstanceId, 0);
                        int dominatorRetainedSize = sizeByDominator.getOrDefault(dominator, 0);
                        sizeByDominator.put(dominator, retainedSize + dominatorRetainedSize);
                        sizedMoved = true;
                    }
                }
            }
        } while (sizedMoved);
        dominatedInstances.release();

        List<Integer> result = new ArrayList<>();
        for (ReferencePathNode pathNode : pathsToLeakingInstances) {
            result.add(sizeByDominator.get(pathNode.objectId));
        }
        return result;
    }

    private Pair<List<ApplicationLeak>, List<LibraryLeak>> buildLeakTraces(PathFindingResults pathFindingResults) {
        List<Integer> retainedSizes = computeRetainedSizes(pathFindingResults);

        listener.onAnalysisProgress(OnAnalysisProgressListener.Step.BUILDING_LEAK_TRACES);

        Map<String, List<LeakTrace>> applicationLeaksMap = new LinkedHashMap<>();
        Map<String, Pair<LibraryLeakReferenceMatcher, List<LeakTrace>>> libraryLeaksMap = new LinkedHashMap<>();

        List<Pair<

ReferencePathNode, Integer>> deduplicatedPaths = deduplicateShortestPaths(pathFindingResults.pathsToLeakingObjects, retainedSizes);

        if (deduplicatedPaths.size() != pathFindingResults.pathsToLeakingObjects.size()) {
            SharkLog.d("Found " + pathFindingResults.pathsToLeakingObjects.size() + " paths to retained objects, down to " + deduplicatedPaths.size() + " after removing duplicated paths");
        } else {
            SharkLog.d("Found " + deduplicatedPaths.size() + " paths to retained objects");
        }

        for (int i = 0; i < deduplicatedPaths.size(); i++) {
            Pair<ReferencePathNode, Integer> pair = deduplicatedPaths.get(i);
            ReferencePathNode retainedObjectNode = pair.getFirst();
            int retainedSize = pair.getSecond();

            List<HeapObject> pathHeapObjects = new ArrayList<>();
            List<ChildNode> shortestChildPath = new ArrayList<>();
            ReferencePathNode node = retainedObjectNode;
            while (node instanceof ChildNode) {
                shortestChildPath.add(0, (ChildNode) node);
                pathHeapObjects.add(0, graph.findObjectById(node.objectId));
                node = node.parent;
            }
            RootNode rootNode = (RootNode) node;
            pathHeapObjects.add(0, graph.findObjectById(rootNode.objectId));

            List<LeakTraceObject> leakTraceObjects = buildLeakTraceObjects(objectInspectors, pathHeapObjects);

            List<LeakTraceReference> referencePath = buildReferencePath(shortestChildPath, leakTraceObjects);

            LeakTrace leakTrace = new LeakTrace(
                    GcRootType.fromGcRoot(rootNode.gcRoot),
                    referencePath,
                    leakTraceObjects.get(leakTraceObjects.size() - 1),
                    retainedSizes == null ? null : retainedSize
            );

            LibraryLeakNode firstLibraryLeakNode = rootNode instanceof LibraryLeakNode ? (LibraryLeakNode) rootNode : shortestChildPath.stream().filter(n -> n instanceof LibraryLeakNode).map(n -> (LibraryLeakNode) n).findFirst().orElse(null);

            if (firstLibraryLeakNode != null) {
                LibraryLeakReferenceMatcher matcher = firstLibraryLeakNode.matcher;
                String signature = matcher.pattern.toString().createSHA1Hash();
                libraryLeaksMap.computeIfAbsent(signature, k -> new Pair<>(matcher, new ArrayList<>())).getSecond().add(leakTrace);
            } else {
                applicationLeaksMap.computeIfAbsent(leakTrace.signature, k -> new ArrayList<>()).add(leakTrace);
            }
        }
        List<ApplicationLeak> applicationLeaks = applicationLeaksMap.entrySet().stream().map(entry -> new ApplicationLeak(entry.getValue())).collect(Collectors.toList());
        List<LibraryLeak> libraryLeaks = libraryLeaksMap.entrySet().stream().map(entry -> new LibraryLeak(entry.getValue().getSecond(), entry.getValue().getFirst().pattern, entry.getValue().getFirst().description)).collect(Collectors.toList());
        return new Pair<>(applicationLeaks, libraryLeaks);
    }

    private List<LeakTraceObject> buildLeakTraceObjects(List<ObjectInspector> objectInspectors, List<HeapObject> pathHeapObjects) {
        List<ObjectReporter> leakReporters = pathHeapObjects.stream().map(ObjectReporter::new).collect(Collectors.toList());
        objectInspectors.forEach(inspector -> leakReporters.forEach(inspector::inspect));
        List<Pair<LeakingStatus, String>> leakStatuses = computeLeakStatuses(leakReporters);
        return IntStream.range(0, pathHeapObjects.size()).mapToObj(i -> {
            HeapObject heapObject = pathHeapObjects.get(i);
            ObjectReporter leakReporter = leakReporters.get(i);
            Pair<LeakingStatus, String> statusPair = leakStatuses.get(i);
            LeakingStatus leakStatus = statusPair.getFirst();
            String leakStatusReason = statusPair.getSecond();
            String className = recordClassName(heapObject);

            LeakTraceObject.ObjectType objectType;
            if (heapObject instanceof HeapClass) {
                objectType = LeakTraceObject.ObjectType.CLASS;
            } else if (heapObject instanceof HeapObjectArray || heapObject instanceof HeapPrimitiveArray) {
                objectType = LeakTraceObject.ObjectType.ARRAY;
            } else {
                objectType = LeakTraceObject.ObjectType.INSTANCE;
            }

            return new LeakTraceObject(
                    objectType,
                    className,
                    leakReporter.labels,
                    leakStatus,
                    leakStatusReason
            );
        }).collect(Collectors.toList());
    }

    private List<LeakTraceReference> buildReferencePath(List<ChildNode> shortestChildPath, List<LeakTraceObject> leakTraceObjects) {
        return IntStream.range(0, shortestChildPath.size()).mapToObj(i -> new LeakTraceReference(
                leakTraceObjects.get(i),
                shortestChildPath.get(i).refFromParentType,
                shortestChildPath.get(i).refFromParentName
        )).collect(Collectors.toList());
    }

    private List<Map.Entry<LeakingStatus, String>> computeLeakStatuses(List<ObjectReporter> leakReporters) {
        int lastElementIndex = leakReporters.size() - 1;

        int lastNotLeakingElementIndex = -1;
        int firstLeakingElementIndex = lastElementIndex;

        List<Map.Entry<LeakingStatus, String>> leakStatuses = new ArrayList<>();

        for (int index = 0; index < leakReporters.size(); index++) {
            ObjectReporter reporter = leakReporters.get(index);
            Map.Entry<LeakingStatus, String> resolvedStatusPair = resolveStatus(reporter, index == lastElementIndex);

            if (index == lastElementIndex) {
                switch (resolvedStatusPair.getKey()) {
                    case LEAKING:
                        break;
                    case UNKNOWN:
                        resolvedStatusPair = Map.entry(LeakingStatus.LEAKING, "This is the leaking object");
                        break;
                    case NOT_LEAKING:
                        resolvedStatusPair = Map.entry(LeakingStatus.LEAKING, "This is the leaking object. Conflicts with " + resolvedStatusPair.getValue());
                        break;
                }
            }

            leakStatuses.add(resolvedStatusPair);
            LeakingStatus leakStatus = resolvedStatusPair.getKey();
            if (leakStatus == LeakingStatus.NOT_LEAKING) {
                lastNotLeakingElementIndex = index;
                firstLeakingElementIndex = lastElementIndex;
            } else if (leakStatus == LeakingStatus.LEAKING && firstLeakingElementIndex == lastElementIndex) {
                firstLeakingElementIndex = index;
            }
        }

        List<String> simpleClassNames = new ArrayList<>();
        for (ObjectReporter reporter : leakReporters) {
            simpleClassNames.add(recordClassName(reporter.getHeapObject()));
        }

        for (int i = 0; i < lastNotLeakingElementIndex; i++) {
            Map.Entry<LeakingStatus, String> entry = leakStatuses.get(i);
            LeakingStatus leakStatus = entry.getKey();
            String leakStatusReason = entry.getValue();

            int nextNotLeakingIndex = i + 1;
            while (nextNotLeakingIndex < lastNotLeakingElementIndex && leakStatuses.get(nextNotLeakingIndex).getKey() != LeakingStatus.NOT_LEAKING) {
                nextNotLeakingIndex++;
            }

            String nextNotLeakingName = simpleClassNames.get(nextNotLeakingIndex);
            switch (leakStatus) {
                case UNKNOWN:
                    entry = Map.entry(LeakingStatus.NOT_LEAKING, nextNotLeakingName + "↓ is not leaking");
                    break;
                case NOT_LEAKING:
                    entry = Map.entry(LeakingStatus.NOT_LEAKING, nextNotLeakingName + "↓ is not leaking and " + leakStatusReason);
                    break;
                case LEAKING:
                    entry = Map.entry(LeakingStatus.NOT_LEAKING, nextNotLeakingName + "↓ is not leaking. Conflicts with " + leakStatusReason);
                    break;
            }
            leakStatuses.set(i, entry);
        }

        if (firstLeakingElementIndex < lastElementIndex - 1) {
            for (int i = lastElementIndex - 1; i > firstLeakingElementIndex; i--) {
                Map.Entry<LeakingStatus, String> entry = leakStatuses.get(i);
                LeakingStatus leakStatus = entry.getKey();

                int previousLeakingIndex = i - 1;
                while (previousLeakingIndex > firstLeakingElementIndex && leakStatuses.get(previousLeakingIndex).getKey() != LeakingStatus.LEAKING) {
                    previousLeakingIndex--;
                }

                String previousLeakingName = simpleClassNames.get(previousLeakingIndex);
                switch (leakStatus) {
                    case UNKNOWN:
                        entry = Map.entry(LeakingStatus.LEAKING, previousLeakingName + "↑ is leaking");
                        break;
                    case LEAKING:
                        entry = Map.entry(LeakingStatus.LEAKING, previousLeakingName + "↑ is leaking and " + entry.getValue());
                        break;
                    case NOT_LEAKING:
                        throw new IllegalStateException("Should never happen");
                }
                leakStatuses.set(i, entry);
            }
        }
        return leakStatuses;
    }

    private Map.Entry<LeakingStatus, String> resolveStatus(ObjectReporter reporter, boolean leakingWins) {
        LeakingStatus status = LeakingStatus.UNKNOWN;
        String reason = "";
        if (!reporter.getNotLeakingReasons().isEmpty()) {
            status = LeakingStatus.NOT_LEAKING;
            reason = String.join(" and ", reporter.getNotLeakingReasons());
        }
        List<String> leakingReasons = reporter.getLeakingReasons();
        if (!leakingReasons.isEmpty()) {
            String winReasons = String.join(" and ", leakingReasons);
            if (status == LeakingStatus.NOT_LEAKING) {
                if (leakingWins) {
                    status = LeakingStatus.LEAKING;
                    reason = winReasons + ". Conflicts with " + reason;
                } else {
                    reason += ". Conflicts with " + winReasons;
                }
            } else {
                status = LeakingStatus.LEAKING;
                reason = winReasons;
            }
        }
        return Map.entry(status, reason);
    }

    private String recordClassName(HeapObject heap) {
        if (heap instanceof HeapClass) {
            return ((HeapClass) heap).getName();
        } else if (heap instanceof HeapInstance) {
            return ((HeapInstance) heap).getInstanceClassName();
        } else if (heap instanceof HeapObjectArray) {
            return ((HeapObjectArray) heap).getArrayClassName();
        } else if (heap instanceof HeapPrimitiveArray) {
            return ((HeapPrimitiveArray) heap).getArrayClassName();
        }
        throw new IllegalArgumentException("Unexpected heap object type");
    }

    private long since(long analysisStartNanoTime) {
        return (System.nanoTime() - analysisStartNanoTime) / 1_000_000;
    }
}