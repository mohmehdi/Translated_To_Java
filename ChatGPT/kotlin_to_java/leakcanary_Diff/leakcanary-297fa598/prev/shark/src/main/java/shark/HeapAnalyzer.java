
package shark;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class HeapAnalyzer {
    private final OnAnalysisProgressListener listener;

    public HeapAnalyzer(OnAnalysisProgressListener listener) {
        this.listener = listener;
    }

    private static class FindLeakInput {
        final HeapGraph graph;
        final List<ReferenceMatcher> referenceMatchers;
        final boolean computeRetainedHeapSize;
        final List<ObjectInspector> objectInspectors;

        public FindLeakInput(
                HeapGraph graph,
                List<ReferenceMatcher> referenceMatchers,
                boolean computeRetainedHeapSize,
                List<ObjectInspector> objectInspectors) {
            this.graph = graph;
            this.referenceMatchers = referenceMatchers;
            this.computeRetainedHeapSize = computeRetainedHeapSize;
            this.objectInspectors = objectInspectors;
        }
    }

    public HeapAnalysis analyze(
            File heapDumpFile,
            LeakingObjectFinder leakingObjectFinder,
            List<ReferenceMatcher> referenceMatchers,
            boolean computeRetainedHeapSize,
            List<ObjectInspector> objectInspectors,
            MetadataExtractor metadataExtractor,
            ProguardMapping proguardMapping) {

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
            try (Hprof hprof = Hprof.open(heapDumpFile)) {
                HeapGraph graph = HprofHeapGraph.indexHprof(hprof, proguardMapping);
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
            MetadataExtractor metadataExtractor) {

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

    private HeapAnalysisSuccess analyzeGraph(
            MetadataExtractor metadataExtractor,
            LeakingObjectFinder leakingObjectFinder,
            File heapDumpFile,
            long analysisStartNanoTime) {
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

    private static class TrieNode {
        final long objectId;

        TrieNode(long objectId) {
            this.objectId = objectId;
        }

        static class ParentNode extends TrieNode {
            final Map<Long, TrieNode> children = new HashMap<>();

            ParentNode(long objectId) {
                super(objectId);
            }

            @Override
            public String toString() {
                return "ParentNode(objectId=" + objectId + ", children=" + children + ")";
            }
        }

        static class LeafNode extends TrieNode {
            final ReferencePathNode pathNode;

            LeafNode(long objectId, ReferencePathNode pathNode) {
                super(objectId);
                this.pathNode = pathNode;
            }
        }
    }

    private List<ReferencePathNode> deduplicateShortestPaths(List<ReferencePathNode> inputPathResults) {
        ParentNode rootTrieNode = new ParentNode(0);

        for (ReferencePathNode pathNode : inputPathResults) {
            List<Long> path = new ArrayList<>();
            ReferencePathNode leakNode = pathNode;
            while (leakNode instanceof ChildNode) {
                path.add(0, leakNode.objectId);
                leakNode = leakNode.parent;
            }
            path.add(0, leakNode.objectId);
            updateTrie(pathNode, path, 0, rootTrieNode);
        }

        List<ReferencePathNode> outputPathResults = new ArrayList<>();
        findResultsInTrie(rootTrieNode, outputPathResults);
        return outputPathResults;
    }

    private void updateTrie(ReferencePathNode pathNode, List<Long> path, int pathIndex, ParentNode parentNode) {
        long objectId = path.get(pathIndex);
        if (pathIndex == path.size() - 1) {
            parentNode.children.put(objectId, new LeafNode(objectId, pathNode));
        } else {
            ParentNode childNode = (ParentNode) parentNode.children.computeIfAbsent(objectId, k -> new ParentNode(objectId));
            updateTrie(pathNode, path, pathIndex + 1, childNode);
        }
    }

    private void findResultsInTrie(ParentNode parentNode, List<ReferencePathNode> outputPathResults) {
        parentNode.children.values().forEach(childNode -> {
            if (childNode instanceof ParentNode) {
                findResultsInTrie((ParentNode) childNode, outputPathResults);
            } else if (childNode instanceof LeafNode) {
                outputPathResults.add(((LeafNode) childNode).pathNode);
            }
        });
    }

    private List<Integer> computeRetainedSizes(PathFindingResults pathFindingResults) {
        if (!computeRetainedHeapSize) {
            return null;
        }

        List<ReferencePathNode> pathsToLeakingInstances = pathFindingResults.pathsToLeakingObjects;
        LongLongScatterMap dominatedInstances = pathFindingResults.dominatedObjectIds;

        listener.onAnalysisProgress(OnAnalysisProgressListener.Step.COMPUTING_NATIVE_RETAINED_SIZE);

        Map<Long, Integer> nativeSizes = new HashMap<>();

        graph.instances.stream()
                .filter(it -> it.instanceClassName.equals("sun.misc.Cleaner"))
                .forEach(cleaner -> {
                    ValueHolder thunkField = cleaner.get("sun.misc.Cleaner", "thunk");
                    long thunkId = thunkField != null ? thunkField.value.asNonNullObjectId() : 0;
                    ValueHolder referentIdField = cleaner.get("java.lang.ref.Reference", "referent");
                    long referentId = referentIdField != null ? referentIdField.value.asNonNullObjectId() : 0;
                    if (thunkId != 0 && referentId != 0) {
                        HeapInstance thunkRecord = (HeapInstance) thunkField.value.asObject();
                        if (thunkRecord instanceof HeapInstance && thunkRecord.instanceOf("libcore.util.NativeAllocationRegistry$CleanerThunk")) {
                            ValueHolder allocationRegistryIdField = thunkRecord.get("libcore.util.NativeAllocationRegistry$CleanerThunk", "this$0");
                            if (allocationRegistryIdField != null && allocationRegistryIdField.value.isNonNullReference()) {
                                HeapInstance allocationRegistryRecord = (HeapInstance) allocationRegistryIdField.value.asObject();
                                if (allocationRegistryRecord instanceof HeapInstance && allocationRegistryRecord.instanceOf("libcore.util.NativeAllocationRegistry")) {
                                    int nativeSize = nativeSizes.getOrDefault(referentId, 0);
                                    nativeSize += allocationRegistryRecord.get("libcore.util.NativeAllocationRegistry", "size").value.asLong().intValue();
                                    nativeSizes.put(referentId, nativeSize);
                                }
                            }
                        }
                    }
                });

        listener.onAnalysisProgress(OnAnalysisProgressListener.Step.COMPUTING_RETAINED_SIZE);

        LinkedHashMap<Long, Integer> sizeByDominator = new LinkedHashMap<>();
        pathsToLeakingInstances.forEach(pathNode -> {
            long leakingInstanceObjectId = pathNode.objectId;
            int retainedSize = sizeByDominator.getOrDefault(leakingInstanceObjectId, 0);
            retainedSize += graph.findObjectById(leakingInstanceObjectId).asInstance.instanceByteSize;
            sizeByDominator.put(leakingInstanceObjectId, retainedSize);
        });

        dominatedInstances.forEach(new ForEachCallback() {
            @Override
            public void onEntry(long instanceId, long dominatorId) {
                if (!pathsToLeakingInstances.stream().map(ReferencePathNode::objectId).collect(Collectors.toSet()).contains(instanceId)) {
                    int currentSize = sizeByDominator.getOrDefault(dominatorId, 0);
                    int nativeSize = nativeSizes.getOrDefault(instanceId, 0);
                    int shallowSize;
                    HeapObject obj = graph.findObjectById(instanceId);
                    if (obj instanceof HeapInstance) {
                        shallowSize = ((HeapInstance) obj).byteSize;
                    } else if (obj instanceof HeapObjectArray) {
                        shallowSize = ((HeapObjectArray) obj).readByteSize();
                    } else if (obj instanceof HeapPrimitiveArray) {
                        shallowSize = ((HeapPrimitiveArray) obj).readByteSize();
                    } else {
                        throw new IllegalStateException("Unexpected class record");
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

        return pathsToLeakingInstances.stream().map(pathNode -> sizeByDominator.get(pathNode.objectId)).collect(Collectors.toList());
    }

    private Pair<List<ApplicationLeak>, List<LibraryLeak>> buildLeakTraces(PathFindingResults pathFindingResults) {
        List<Integer> retainedSizes = computeRetainedSizes(pathFindingResults);

        listener.onAnalysisProgress(OnAnalysisProgressListener.Step.BUILDING_LEAK_TRACES);

        Map<String, List<LeakTrace>> applicationLeaksMap = new HashMap<>();
        Map<String, Pair<LibraryLeakReferenceMatcher, List<LeakTrace>>> libraryLeaksMap = new HashMap<>();

        List<ReferencePathNode> deduplicatedPaths = deduplicateShortestPaths(pathFindingResults.pathsToLeakingObjects);

        if (deduplicatedPaths.size() != pathFindingResults.pathsToLeakingObjects.size()) {
            SharkLog.d(() -> "Found " + pathFindingResults.pathsToLeakingObjects.size() + " paths to retained objects, down to " +
                    deduplicatedPaths.size() + " after removing duplicated paths");
        } else {
            SharkLog.d(() -> "Found " + deduplicatedPaths.size() + " paths to retained objects");
        }

        for (int index = 0; index < deduplicatedPaths.size(); index++) {
            ReferencePathNode retainedObjectNode = deduplicatedPaths.get(index);

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
                    retainedSizes != null ? retainedSizes.get(index) : null
            );

            LibraryLeakNode firstLibraryLeakNode = rootNode instanceof LibraryLeakNode ? (LibraryLeakNode) rootNode : shortestChildPath.stream()
                    .filter(it -> it instanceof LibraryLeakNode)
                    .map(it -> (LibraryLeakNode) it)
                    .findFirst().orElse(null);

            if (firstLibraryLeakNode != null) {
                LibraryLeakReferenceMatcher matcher = firstLibraryLeakNode.matcher;
                String signature = matcher.pattern.toString().createSHA1Hash();
                libraryLeaksMap.computeIfAbsent(signature, k -> new Pair<>(matcher, new ArrayList<>()))
                        .getSecond().add(leakTrace);
            } else {
                applicationLeaksMap.computeIfAbsent(leakTrace.signature, k -> new ArrayList<>()).add(leakTrace);
            }
        }

        List<ApplicationLeak> applicationLeaks = applicationLeaksMap.entrySet().stream()
                .map(entry -> new ApplicationLeak(entry.getValue()))
                .collect(Collectors.toList());
        List<LibraryLeak> libraryLeaks = libraryLeaksMap.entrySet().stream()
                .map(entry -> new LibraryLeak(entry.getValue().getSecond(), entry.getValue().getFirst().pattern, entry.getValue().getFirst().description))
                .collect(Collectors.toList());
        return new Pair<>(applicationLeaks, libraryLeaks);
    }

    private List<LeakTraceObject> buildLeakTraceObjects(List<ObjectInspector> objectInspectors, List<HeapObject> pathHeapObjects) {
        List<ObjectReporter> leakReporters = pathHeapObjects.stream().map(ObjectReporter::new).collect(Collectors.toList());

        objectInspectors.forEach(inspector -> leakReporters.forEach(inspector::inspect));

        List<Pair<LeakingStatus, String>> leakStatuses = computeLeakStatuses(leakReporters);

        return IntStream.range(0, pathHeapObjects.size()).mapToObj(index -> {
            ObjectReporter leakReporter = leakReporters.get(index);
            Pair<LeakingStatus, String> statusPair = leakStatuses.get(index);
            String className = recordClassName(pathHeapObjects.get(index));

            LeakTraceObject.ObjectType objectType;
            if (pathHeapObjects.get(index) instanceof HeapClass) {
                objectType = LeakTraceObject.ObjectType.CLASS;
            } else if (pathHeapObjects.get(index) instanceof HeapObjectArray || pathHeapObjects.get(index) instanceof HeapPrimitiveArray) {
                objectType = LeakTraceObject.ObjectType.ARRAY;
            } else {
                objectType = LeakTraceObject.ObjectType.INSTANCE;
            }

            return new LeakTraceObject(
                    objectType,
                    className,
                    leakReporter.labels,
                    statusPair.getFirst(),
                    statusPair.getSecond()
            );
        }).collect(Collectors.toList());
    }

    private List<LeakTraceReference> buildReferencePath(List<ChildNode> shortestChildPath, List<LeakTraceObject> leakTraceObjects) {
        return IntStream.range(0, shortestChildPath.size()).mapToObj(index -> new LeakTraceReference(
                leakTraceObjects.get(index),
                shortestChildPath.get(index).refFromParentType,
                shortestChildPath.get(index).refFromParentName
        )).collect(Collectors.toList());
    }

    private List<Pair<LeakingStatus, String>> computeLeakStatuses(List<ObjectReporter> leakReporters) {
        int lastElementIndex = leakReporters.size() - 1;

        int lastNotLeakingElementIndex = -1;
        int firstLeakingElementIndex = lastElementIndex;

        List<Pair<LeakingStatus, String>> leakStatuses = new ArrayList<>();

        for (int index = 0; index < leakReporters.size(); index++) {
            ObjectReporter reporter = leakReporters.get(index);
            Pair<LeakingStatus, String> resolvedStatusPair = resolveStatus(reporter, index == lastElementIndex);
            if (index == lastElementIndex) {
                if (resolvedStatusPair.getFirst() == LeakingStatus.LEAKING) {
                    leakStatuses.add(resolvedStatusPair);
                } else if (resolvedStatusPair.getFirst() == LeakingStatus.UNKNOWN) {
                    leakStatuses.add(new Pair<>(LeakingStatus.LEAKING, "This is the leaking object"));
                } else if (resolvedStatusPair.getFirst() == LeakingStatus.NOT_LEAKING) {
                    leakStatuses.add(new Pair<>(LeakingStatus.LEAKING, "This is the leaking object. Conflicts with " + resolvedStatusPair.getSecond()));
                }
            } else {
                leakStatuses.add(resolvedStatusPair);
                if (resolvedStatusPair.getFirst() == LeakingStatus.NOT_LEAKING) {
                    lastNotLeakingElementIndex = index;
                    firstLeakingElementIndex = lastElementIndex;
                } else if (resolvedStatusPair.getFirst() == LeakingStatus.LEAKING && firstLeakingElementIndex == lastElementIndex) {
                    firstLeakingElementIndex = index;
                }
            }
        }

        List<String> simpleClassNames = leakReporters.stream()
                .map(reporter -> recordClassName(reporter.heapObject).lastSegment('.'))
                .collect(Collectors.toList());

        for (int i = 0; i < lastNotLeakingElementIndex; i++) {
            Pair<LeakingStatus, String> statusPair = leakStatuses.get(i);
            int nextNotLeakingIndex = IntStream.iterate(i + 1, index -> index < lastNotLeakingElementIndex, index -> index + 1)
                    .filter(index -> leakStatuses.get(index).getFirst() == LeakingStatus.NOT_LEAKING)
                    .findFirst().orElseThrow();

            Pair<LeakingStatus, String> finalStatusPair;
            if (statusPair.getFirst() == LeakingStatus.UNKNOWN) {
                finalStatusPair = new Pair<>(LeakingStatus.NOT_LEAKING, simpleClassNames.get(nextNotLeakingIndex) + "↓ is not leaking");
            } else if (statusPair.getFirst() == LeakingStatus.NOT_LEAKING) {
                finalStatusPair = new Pair<>(LeakingStatus.NOT_LEAKING, simpleClassNames.get(nextNotLeakingIndex) + "↓ is not leaking and " + statusPair.getSecond());
            } else {
                finalStatusPair = new Pair<>(LeakingStatus.NOT_LEAKING, simpleClassNames.get(nextNotLeakingIndex) + "↓ is not leaking. Conflicts with " + statusPair.getSecond());
            }
            leakStatuses.set(i, finalStatusPair);
        }

        if (firstLeakingElementIndex < lastElementIndex - 1) {
            for (int i = lastElementIndex - 1; i > firstLeakingElementIndex; i--) {
                Pair<LeakingStatus, String> statusPair = leakStatuses.get(i);
                int previousLeakingIndex = IntStream.iterate(i - 1, index -> index > firstLeakingElementIndex, index -> index - 1)
                        .filter(index -> leakStatuses.get(index).getFirst() == LeakingStatus.LEAKING)
                        .findFirst().orElseThrow();

                Pair<LeakingStatus, String> finalStatusPair;
                if (statusPair.getFirst() == LeakingStatus.UNKNOWN) {
                    finalStatusPair = new Pair<>(LeakingStatus.LEAKING, simpleClassNames.get(previousLeakingIndex) + "↑ is leaking");
                } else if (statusPair.getFirst() == LeakingStatus.LEAKING) {
                    finalStatusPair = new Pair<>(LeakingStatus.LEAKING, simpleClassNames.get(previousLeakingIndex) + "↑ is leaking and " + statusPair.getSecond());
                } else {
                    throw new IllegalStateException("Should never happen");
                }
                leakStatuses.set(i, finalStatusPair);
            }
        }

        return leakStatuses;
    }

    private Pair<LeakingStatus, String> resolveStatus(ObjectReporter reporter, boolean leakingWins) {
        LeakingStatus status = LeakingStatus.UNKNOWN;
        String reason = "";
        if (!reporter.notLeakingReasons.isEmpty()) {
            status = LeakingStatus.NOT_LEAKING;
            reason = reporter.notLeakingReasons.stream().collect(Collectors.joining(" and "));
        }
        List<String> leakingReasons = reporter.leakingReasons;
        if (!leakingReasons.isEmpty()) {
            String winReasons = leakingReasons.stream().collect(Collectors.joining(" and "));
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
        return new Pair<>(status, reason);
    }

    private String recordClassName(HeapObject heapObject) {
        return heapObject instanceof HeapClass ? ((HeapClass) heapObject).name : heapObject.getClassRecord().classHierarchy;
    }

    private long since(long analysisStartNanoTime) {
        return NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime);
    }
}