package shark;

import shark.HeapAnalyzer.TrieNode;
import shark.HeapAnalyzer.TrieNode.LeafNode;
import shark.HeapAnalyzer.TrieNode.ParentNode;
import shark.HeapObject;
import shark.HeapObject.HeapClass;
import shark.HeapObject.HeapInstance;
import shark.HeapObject.HeapObjectArray;
import shark.HeapObject.HeapPrimitiveArray;
import shark.HprofHeapGraph;
import shark.HprofHeapGraph.Companion;
import shark.LeakTrace.GcRootType;
import shark.LeakTraceObject.LeakingStatus;
import shark.LeakTraceObject.LeakingStatus.LEAKING;
import shark.LeakTraceObject.LeakingStatus.NOT_LEAKING;
import shark.LeakTraceObject.LeakingStatus.UNKNOWN;
import shark.LeakTraceObject.ObjectType;
import shark.LeakTraceObject.ObjectType.ARRAY;
import shark.LeakTraceObject.ObjectType.CLASS;
import shark.LeakTraceObject.ObjectType.INSTANCE;
import shark.OnAnalysisProgressListener.Step;
import shark.OnAnalysisProgressListener.Step.BUILDING_LEAK_TRACES;
import shark.OnAnalysisProgressListener.Step.COMPUTING_NATIVE_RETAINED_SIZE;
import shark.OnAnalysisProgressListener.Step.COMPUTING_RETAINED_SIZE;
import shark.OnAnalysisProgressListener.Step.EXTRACTING_METADATA;
import shark.OnAnalysisProgressListener.Step.FINDING_RETAINED_OBJECTS;
import shark.OnAnalysisProgressListener.Step.PARSING_HEAP_DUMP;
import shark.internal.PathFinder;
import shark.internal.PathFinder.PathFindingResults;
import shark.internal.ReferencePathNode;
import shark.internal.ReferencePathNode.ChildNode;
import shark.internal.ReferencePathNode.LibraryLeakNode;
import shark.internal.ReferencePathNode.RootNode;
import shark.internal.createSHA1Hash;
import shark.internal.hppc.LongLongScatterMap.ForEachCallback;
import shark.internal.lastSegment;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

class HeapAnalyzer {

    private class FindLeakInput {
        final HeapGraph graph;
        final ArrayList<ReferenceMatcher> referenceMatchers;
        final boolean computeRetainedHeapSize;
        final ArrayList<ObjectInspector> objectInspectors;

        FindLeakInput(HeapGraph graph, ArrayList<ReferenceMatcher> referenceMatchers, boolean computeRetainedHeapSize, ArrayList<ObjectInspector> objectInspectors) {
            this.graph = graph;
            this.referenceMatchers = referenceMatchers;
            this.computeRetainedHeapSize = computeRetainedHeapSize;
            this.objectInspectors = objectInspectors;
        }
    }

        static HeapAnalysis analyze(
            File heapDumpFile,
            LeakingObjectFinder leakingObjectFinder,
            List<ReferenceMatcher> referenceMatchers,
            boolean computeRetainedHeapSize,
            List<ObjectInspector> objectInspectors,
            MetadataExtractor metadataExtractor,
            ProguardMapping proguardMapping) throws Throwable {
        long analysisStartNanoTime = System.nanoTime();

        if (!heapDumpFile.exists()) {
            IllegalArgumentException exception = new IllegalArgumentException("File does not exist: " + heapDumpFile);
            return new HeapAnalysisFailure(
                    heapDumpFile, System.currentTimeMillis(), System.nanoTime() - analysisStartNanoTime,
                    new HeapAnalysisException(exception)
            );
        }

        try {
            if (listener != null) {
                listener.onAnalysisProgress("PARSING_HEAP_DUMP");
            }
            HeapGraph graph = heapDumpFile.openHeapGraph(proguardMapping);
            FindLeakInput helpers = new FindLeakInput(graph, referenceMatchers, computeRetainedHeapSize, objectInspectors);
            return helpers.analyzeGraph(
                    metadataExtractor, leakingObjectFinder, heapDumpFile, analysisStartNanoTime
            );
        } catch (Throwable exception) {
            return new HeapAnalysisFailure(
                    heapDumpFile, System.currentTimeMillis(), System.nanoTime() - analysisStartNanoTime,
                    new HeapAnalysisException(exception)
            );
        }
    }

    static HeapAnalysis analyze(
            File heapDumpFile,
            HeapGraph graph,
            LeakingObjectFinder leakingObjectFinder,
            List<ReferenceMatcher> referenceMatchers,
            boolean computeRetainedHeapSize,
            List<ObjectInspector> objectInspectors,
            MetadataExtractor metadataExtractor) throws Throwable {
        long analysisStartNanoTime = System.nanoTime();

        FindLeakInput helpers = new FindLeakInput(graph, referenceMatchers, computeRetainedHeapSize, objectInspectors);
        return helpers.analyzeGraph(
                metadataExtractor, leakingObjectFinder, heapDumpFile, analysisStartNanoTime
        );
    }


private HeapAnalysisSuccess analyzeGraph(MetadataExtractor metadataExtractor, LeakingObjectFinder leakingObjectFinder, File heapDumpFile, long analysisStartNanoTime) {
  listener.onAnalysisProgress(EXTRACTING_METADATA);
  Metadata metadata = metadataExtractor.extractMetadata(graph);

  listener.onAnalysisProgress(FINDING_RETAINED_OBJECTS);
  Set leakingObjectIds = leakingObjectFinder.findLeakingObjectIds(graph);

  Pair < List, List > leaks = findLeaks(leakingObjectIds);

  return new HeapAnalysisSuccess(
    heapDumpFile,
    System.currentTimeMillis(),
    since(analysisStartNanoTime),
    metadata,
    leaks.first,
    leaks.second
  );
}

    public Pair<List<ApplicationLeak>, List<LibraryLeak>> findLeaks(Set<Long> leakingObjectIds) {
        PathFinder pathFinder = new PathFinder(graph, listener, referenceMatchers);
        List<PathFindingResult> pathFindingResults = pathFinder.findPathsFromGcRoots(leakingObjectIds, this::computeRetainedHeapSize);

        SharkLog.d("Found " + leakingObjectIds.size() + " retained objects");

        return buildLeakTraces(pathFindingResults);
    }

    private static class TrieNode {
        final long objectId;
        int retainedSize;

        TrieNode(long objectId) {
            this.objectId = objectId;
        }

        static class ParentNode extends TrieNode {
            final LinkedHashMap<Long, TrieNode> children;

            ParentNode(long objectId) {
                super(objectId);
                this.children = new LinkedHashMap<>();
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
    private static List<Pair<ReferencePathNode, Integer>> deduplicateShortestPaths(
            List<ReferencePathNode> inputPathResults, List<Integer> retainedSizes) {
        ParentNode rootTrieNode = new ParentNode(0);

        for (int index = 0; index < inputPathResults.size(); index++) {
            ReferencePathNode pathNode = inputPathResults.get(index);

            List<Long> path = new ArrayList<>();
            ReferencePathNode leakNode = pathNode;
            while (leakNode instanceof ChildNode) {
                path.add(0, leakNode.getObjectId());
                leakNode = leakNode.getParent();
            }
            path.add(0, leakNode.getObjectId());

            updateTrie(pathNode, path, 0, rootTrieNode, retainedSizes != null && retainedSizes.size() > index ? retainedSizes.get(index) : 0);
        }

        List<Pair<ReferencePathNode, Integer>> outputPathResults = new ArrayList<>();
        findResultsInTrie(rootTrieNode, outputPathResults);
        return outputPathResults;
    }

    private static void updateTrie(
            ReferencePathNode pathNode, List<Long> path, int pathIndex, ParentNode parentNode, int retainedSize) {
        long objectId = path.get(pathIndex);
        if (pathIndex == path.size() - 1) {
            int previousRetained = parentNode.children.getOrDefault(objectId, new Node()).retainedSize;
            LeafNode leafNode = new LeafNode(objectId, pathNode);
            leafNode.apply(previousRetained);
            parentNode.children.put(objectId, leafNode);
        } else {
            ParentNode childNode = (ParentNode) parentNode.children.getOrDefault(objectId, new ParentNode(objectId));
            childNode.retainedSize += retainedSize;
            if (childNode instanceof ParentNode) {
                updateTrie(pathNode, path, pathIndex + 1, (ParentNode) childNode, retainedSize);
            }
        }
    }

    private static void findResultsInTrie(
            ParentNode parentNode, List<Pair<ReferencePathNode, Integer>> outputPathResults) {
        for (Node childNode : parentNode.children.values()) {
            if (childNode instanceof ParentNode) {
                findResultsInTrie((ParentNode) childNode, outputPathResults);
            } else if (childNode instanceof LeafNode) {
                LeafNode leafNode = (LeafNode) childNode;
                outputPathResults.add(new Pair<>(leafNode.pathNode, leafNode.retainedSize));
            }
        }
    }

    @Nullable
    private List<Integer> computeRetainedSizes(PathFindingResults pathFindingResults) {
        if (!computeRetainedHeapSize) {
            return null;
        }

        List<PathsToLeakingObjects> pathsToLeakingInstances = pathFindingResults.getPathsToLeakingObjects();
        List<Long> dominatedInstances = pathFindingResults.getDominatedObjectIds();

        listener.onAnalysisProgress(COMPUTING_NATIVE_RETAINED_SIZE);

        Map<Long, Integer> nativeSizes = new HashMap<Long, Integer>() {{
            put(0L, 0);
        }};

        for (Instance instance : pathFindingResults.getGraph().getInstances()) {
            if (instance.getInstanceClassName().equals("sun.misc.Cleaner")) {
                Object thunkField = getFieldValue(instance, "sun.misc.Cleaner", "thunk");
                Long thunkId = getObjectId(thunkField);
                Long referentId = getObjectId(getFieldValue(instance, "java.lang.ref.Reference", "referent"));

                if (thunkId != null && referentId != null) {
                    Object thunkRecord = getFieldValue(thunkField, "libcore.util.NativeAllocationRegistry$CleanerThunk", "this$0");
                    if (thunkRecord instanceof HeapInstance && thunkRecord.instanceOf("libcore.util.NativeAllocationRegistry$CleanerThunk")) {
                        Object allocationRegistryIdField = getFieldValue(thunkRecord, "libcore.util.NativeAllocationRegistry$CleanerThunk", "this$0");
                        if (allocationRegistryIdField != null && getFieldValue(allocationRegistryIdField, "value").isNonNullReference()) {
                            Object allocationRegistryRecord = getFieldValue(allocationRegistryIdField, "value").asObject();
                            if (allocationRegistryRecord instanceof HeapInstance && allocationRegistryRecord.instanceOf("libcore.util.NativeAllocationRegistry")) {
                                int nativeSize = nativeSizes.get(referentId);
                                nativeSize += getFieldValue(allocationRegistryRecord, "libcore.util.NativeAllocationRegistry", "size").asLong().toInt();
                                nativeSizes.put(referentId, nativeSize);
                            }
                        }
                    }
                }
            }
        }

        listener.onAnalysisProgress(COMPUTING_RETAINED_SIZE);

        Map<Long, Integer> sizeByDominator = new LinkedHashMap<Long, Integer>() {{
            put(0L, 0);
        }};

        Set<Long> leakingInstanceIds = new HashSet<Long>();
        for (ReferencePathNode pathNode : pathsToLeakingInstances) {
            Long leakingInstanceObjectId = pathNode.getObjectId();
            leakingInstanceIds.add(leakingInstanceObjectId);

            int shallowSize = 0;
            HeapObject heapObject = pathFindingResults.getGraph().findObjectById(leakingInstanceObjectId);
            if (heapObject instanceof HeapInstance) {
                shallowSize = heapObject.getByteSize();
            } else if (heapObject instanceof HeapObjectArray) {
                shallowSize = heapObject.readByteSize();
            } else if (heapObject instanceof HeapPrimitiveArray) {
                shallowSize = heapObject.readByteSize();
            } else if (heapObject instanceof HeapClass) {
                throw new IllegalStateException("Unexpected class record " + heapObject);
            }
            int retainedSize = sizeByDominator.getOrDefault(leakingInstanceObjectId, 0);

            retainedSize += shallowSize;
            sizeByDominator.put(leakingInstanceObjectId, retainedSize);
        }

        dominatedInstances.forEach(new ForEachCallback() {
            @Override
            public void onEntry(Long instanceId, Long dominatorId) {
                if (!leakingInstanceIds.contains(instanceId)) {
                    int currentSize = sizeByDominator.get(dominatorId);
                    int nativeSize = nativeSizes.get(instanceId);
                    int shallowSize = 0;
                    HeapObject heapObject = pathFindingResults.getGraph().findObjectById(instanceId);
                    if (heapObject instanceof HeapInstance) {
                        shallowSize = heapObject.getByteSize();
                    } else if (heapObject instanceof HeapObjectArray) {
                        shallowSize = heapObject.readByteSize();
                    } else if (heapObject instanceof HeapPrimitiveArray) {
                        shallowSize = heapObject.readByteSize();
                    } else if (heapObject instanceof HeapClass) {
                        throw new IllegalStateException("Unexpected class record " + heapObject);
                    }
                    sizeByDominator.put(dominatorId, currentSize + nativeSize + shallowSize);
                }
            }
        });

        boolean sizedMoved;
        do {
            sizedMoved = false;
            for (Long leakingInstanceId : pathFindingResults.getPathsToLeakingObjects().stream().map(ReferencePathNode::getObjectId).collect(Collectors.toList())) {
                int dominatorSlot = dominatedInstances.getSlot(leakingInstanceId);
                if (dominatorSlot != -1) {
                    Long dominator = dominatedInstances.getSlotValue(dominatorSlot);
                    int retainedSize = sizeByDominator.get(leakingInstanceId);
                    if (retainedSize > 0) {
                        sizeByDominator.put(leakingInstanceId, 0);
                        int dominatorRetainedSize = sizeByDominator.get(dominator);
                        sizeByDominator.put(dominator, retainedSize + dominatorRetainedSize);
                        sizedMoved = true;
                    }
                }
            }
        } while (sizedMoved);
        dominatedInstances.release();
        return pathsToLeakingInstances.stream().map(pathNode -> sizeByDominator.get(pathNode.getObjectId())).collect(Collectors.toList());
    }

    private Pair<List<ApplicationLeak>, List<LibraryLeak>> buildLeakTraces(PathFindingResults pathFindingResults) {
        List<Integer> retainedSizes = computeRetainedSizes(pathFindingResults);

        listener.onAnalysisProgress(BUILDING_LEAK_TRACES);

        Map<String, List<LeakTrace>> applicationLeaksMap = new HashMap<>();
        Map<String, Pair<LibraryLeakReferenceMatcher, List<LeakTrace>>> libraryLeaksMap = new HashMap<>();

        List<ReferencePathNode> deduplicatedPaths = deduplicateShortestPaths(pathFindingResults.getPathsToLeakingObjects(), retainedSizes);

        if (deduplicatedPaths.size() != pathFindingResults.getPathsToLeakingObjects().size()) {
            SharkLog.d("Found " + pathFindingResults.getPathsToLeakingObjects().size() + " paths to retained objects, down to " + deduplicatedPaths.size() + " after removing duplicated paths");
        } else {
            SharkLog.d("Found " + deduplicatedPaths.size() + " paths to retained objects");
        }

        for (int index = 0; index < deduplicatedPaths.size(); index++) {
            ReferencePathNode retainedObjectNode = deduplicatedPaths.get(index);
            int retainedSize = retainedSizes.get(index);

            List<HeapObject> pathHeapObjects = new ArrayList<>();
            List<ChildNode> shortestChildPath = new ArrayList<>();
            ReferencePathNode node = retainedObjectNode;
            while (node instanceof ChildNode) {
                shortestChildPath.add(0, node);
                pathHeapObjects.add(0, pathFindingResults.getGraph().findObjectById(node.getObjectId()));
                node = node.getParent();
            }
            ReferencePathNode rootNode = (ReferencePathNode) node;
            pathHeapObjects.add(0, pathFindingResults.getGraph().findObjectById(rootNode.getObjectId()));

            List<LeakTraceObject> leakTraceObjects = buildLeakTraceObjects(objectInspectors, pathHeapObjects);

            List<LeakTraceReference> referencePath = buildReferencePath(shortestChildPath, leakTraceObjects);

            LeakTrace leakTrace = new LeakTrace(
                    gcRootType(rootNode.getGcRoot()),
                    referencePath,
                    leakTraceObjects.get(leakTraceObjects.size() - 1),
                    retainedHeapByteSize(retainedSizes)
            );

            ReferencePathNode firstLibraryLeakNode = null;
            if (rootNode instanceof LibraryLeakNode) {
                firstLibraryLeakNode = (ReferencePathNode) rootNode;
            } else {
                for (ChildNode childNode : shortestChildPath) {
                    if (childNode instanceof LibraryLeakNode) {
                        firstLibraryLeakNode = (ReferencePathNode) childNode;
                        break;
                    }
                }
            }

            if (firstLibraryLeakNode != null) {
                LibraryLeakReferenceMatcher matcher = firstLibraryLeakNode.getMatcher();
                String signature = matcher.getPattern().toString().createSHA1Hash();
                libraryLeaksMap.computeIfAbsent(signature, key -> new Pair<>(matcher, new ArrayList<>())).second.add(leakTrace);
            } else {
                applicationLeaksMap.computeIfAbsent(leakTrace.getSignature(), key -> new ArrayList<>()).add(leakTrace);
            }
        }
        List<ApplicationLeak> applicationLeaks = applicationLeaksMap.values().stream().map(leakTraces -> new ApplicationLeak(leakTraces)).collect(Collectors.toList());
        List<LibraryLeak> libraryLeaks = libraryLeaksMap.values().stream().map(entry -> new LibraryLeak(entry.second, entry.first.getPattern(), entry.first.getDescription())).collect(Collectors.toList());
        return new Pair<>(applicationLeaks, libraryLeaks);
    }
private List<LeakTraceObject> buildLeakTraceObjects(
    List<ObjectInspector> objectInspectors,
    List<HeapObject> pathHeapObjects
) {
    List<ObjectReporter> leakReporters = pathHeapObjects.stream()
        .map(heapObject -> new ObjectReporter(heapObject))
        .collect(toList());

    objectInspectors.forEach(inspector ->
        leakReporters.forEach(reporter ->
            inspector.inspect(reporter)
        )
    );

    List<Pair<LeakingStatus, String>> leakStatuses = computeLeakStatuses(leakReporters);

    return pathHeapObjects.stream()
        .map((heapObject, index) -> {
            ObjectReporter leakReporter = leakReporters.get(index);
            Pair<LeakingStatus, String> leakStatusPair = leakStatuses.get(index);
            String className = recordClassName(heapObject);

            ObjectType objectType;
            if (heapObject instanceof HeapClass) {
                objectType = ObjectType.CLASS;
            } else if (heapObject instanceof HeapObjectArray || heapObject instanceof HeapPrimitiveArray) {
                objectType = ObjectType.ARRAY;
            } else {
                objectType = ObjectType.INSTANCE;
            }

            return new LeakTraceObject(
                objectType,
                className,
                leakReporter.getLabels(),
                leakStatusPair.first,
                leakStatusPair.second
            );
        })
        .collect(toList());
}

private List<LeakTraceReference> buildReferencePath(
    List<ChildNode> shortestChildPath,
    List<LeakTraceObject> leakTraceObjects
) {
    return shortestChildPath.stream()
        .map((childNode, index) -> new LeakTraceReference(
            leakTraceObjects.get(index),
            childNode.getRefFromParentType(),
            childNode.getRefFromParentName()
        ))
        .collect(toList());
}

private List<Pair<LeakingStatus, String>> computeLeakStatuses(List<ObjectReporter> leakReporters) {
    int lastElementIndex = leakReporters.size() - 1;

    int lastNotLeakingElementIndex = -1;
    int firstLeakingElementIndex = lastElementIndex;

    List<Pair<LeakingStatus, String>> leakStatuses = new ArrayList<>();

    for (int index = 0; index < leakReporters.size(); index++) {
        ObjectReporter reporter = leakReporters.get(index);
        Pair<LeakingStatus, String> resolvedStatusPair = resolveStatus(reporter, index == lastElementIndex);

        leakStatuses.add(resolvedStatusPair);
        LeakingStatus leakStatus = resolvedStatusPair.first;
        if (leakStatus == LeakingStatus.NOT_LEAKING) {
            lastNotLeakingElementIndex = index;

            if (firstLeakingElementIndex == lastElementIndex) {
                firstLeakingElementIndex = index;
            }
        } else if (leakStatus == LeakingStatus.LEAKING && firstLeakingElementIndex == lastElementIndex) {
            firstLeakingElementIndex = index;
        }
    }

    List<String> simpleClassNames = leakReporters.stream()
        .map(reporter -> recordClassName(reporter.getHeapObject()).lastSegment('.'))
        .collect(toList());

    for (int i = 0; i < lastNotLeakingElementIndex; i++) {
        Pair<LeakingStatus, String> leakStatusPair = leakStatuses.get(i);
        LeakingStatus leakStatus = leakStatusPair.first;
        String leakStatusReason = leakStatusPair.second;

        int nextNotLeakingIndex = IntStream.iterate(i + 1, n -> n + 1)
            .limit(lastNotLeakingElementIndex - i)
            .filter(n -> leakStatuses.get(n).first == LeakingStatus.NOT_LEAKING)
            .findFirst()
            .orElse(lastNotLeakingElementIndex + 1);

        String nextNotLeakingName = simpleClassNames.get(nextNotLeakingIndex);

        leakStatuses.set(i, when (leakStatus) {
            LeakingStatus.UNKNOWN -> new Pair<>(LeakingStatus.NOT_LEAKING, "$nextNotLeakingName↓ is not leaking")
            LeakingStatus.NOT_LEAKING -> new Pair<>(LeakingStatus.NOT_LEAKING, "$nextNotLeakingName↓ is not leaking and $leakStatusReason")
            LeakingStatus.LEAKING -> new Pair<>(LeakingStatus.NOT_LEAKING, "$nextNotLeakingName↓ is not leaking. Conflicts with $leakStatusReason")
        });
    }

    if (firstLeakingElementIndex < lastElementIndex - 1) {
        for (int i = lastElementIndex - 1; i > firstLeakingElementIndex; i--) {
            Pair<LeakingStatus, String> leakStatusPair = leakStatuses.get(i);
            LeakingStatus leakStatus = leakStatusPair.first;
            String leakStatusReason = leakStatusPair.second;

            int previousLeakingIndex = IntStream.iterate(i - 1, n -> n - 1)
                .limit(i - firstLeakingElementIndex)
                .filter(n -> leakStatuses.get(n).first == LeakingStatus.LEAKING)
                .findFirst()
                .orElse(firstLeakingElementIndex - 1);

            String previousLeakingName = simpleClassNames.get(previousLeakingIndex);

            leakStatuses.set(i, when (leakStatus) {
                LeakingStatus.UNKNOWN -> new Pair<>(LeakingStatus.LEAKING, "$previousLeakingName↑ is leaking")
                LeakingStatus.LEAKING -> new Pair<>(LeakingStatus.LEAKING, "$previousLeakingName↑ is leaking and $leakStatusReason")
                LeakingStatus.NOT_LEAKING -> throw new IllegalStateException("Should never happen")
            });
        }
    }
    return leakStatuses;
}

private Pair<LeakingStatus, String> resolveStatus(
    ObjectReporter reporter,
    boolean leakingWins
) {
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
                reason = "$winReasons. Conflicts with $reason";
            } else {
                reason += ". Conflicts with $winReasons";
            }
        } else {
            status = LeakingStatus.LEAKING;
            reason = winReasons;
        }
    }
    return new Pair<>(status, reason);
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
    throw new IllegalArgumentException("Unsupported HeapObject type");
}

private long since(long analysisStartNanoTime) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime);
}
}