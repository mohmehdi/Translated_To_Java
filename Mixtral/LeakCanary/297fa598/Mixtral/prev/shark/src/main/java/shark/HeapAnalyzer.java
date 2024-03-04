

package shark;

import shark.HeapAnalyzer.TrieNode;
import shark.HeapAnalyzer.TrieNode.LeafNode;
import shark.HeapAnalyzer.TrieNode.ParentNode;
import shark.HeapObject;
import shark.HeapObject.HeapClass;
import shark.HeapObject.HeapInstance;
import shark.HeapObject.HeapObjectArray;
import shark.HeapObject.HeapPrimitiveArray;
import shark.LeakTrace;
import shark.LeakTrace.GcRootType;
import shark.LeakTraceObject;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

class HeapAnalyzer {

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
    }

    HeapAnalysis analyze(
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
                    heapDumpFile,
                    System.currentTimeMillis(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime),
                    new HeapAnalysisException(exception)
            );
        }

        return try {
            listener.onAnalysisProgress(PARSING_HEAP_DUMP);
            Hprof.open(heapDumpFile)
                    .use(hprof -> {
                        HeapGraph graph = HprofHeapGraph.indexHprof(hprof, proguardMapping);
                        FindLeakInput helpers = new FindLeakInput(graph, referenceMatchers, computeRetainedHeapSize, objectInspectors);
                        helpers.analyzeGraph(
                                metadataExtractor, leakingObjectFinder, heapDumpFile, analysisStartNanoTime
                        );
                    });
        } catch (Exception exception) {
            HeapAnalysisFailure(
                    heapDumpFile,
                    System.currentTimeMillis(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime),
                    new HeapAnalysisException(exception)
            );
        }
    }

        public static HeapAnalysis analyze(
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
                    heapDumpFile,
                    System.currentTimeMillis(),
                    since(analysisStartNanoTime, TimeUnit.NANOSECONDS),
                    new HeapAnalysisException(exception)
            );
        }
    }
        public Pair<List<ApplicationLeak>, List<LibraryLeak>> findLeaks(Set<Long> leakingObjectIds) {
        PathFinder pathFinder = new PathFinder(graph, listener, referenceMatchers);
        List<PathFindingResult> pathFindingResults = pathFinder.findPathsFromGcRoots(leakingObjectIds, this::computeRetainedHeapSize);

        SharkLog.d("Found " + leakingObjectIds.size() + " retained objects");

        return buildLeakTraces(pathFindingResults);
    }

        public List<Integer> computeRetainedSizes(PathFindingResults pathFindingResults, ComputeRetainedSizesListener listener) {
        this.pathFindingResults = pathFindingResults;
        this.listener = listener;
        this.computeRetainedHeapSize = true; // assuming this is true in Kotlin

        if (!computeRetainedHeapSize) {
            return null;
        }

        List<PathNode> pathsToLeakingInstances = pathFindingResults.getPathsToLeakingObjects();
        Set<Long> dominatedInstances = pathFindingResults.getDominatedObjectIds();

        listener.onAnalysisProgress(COMPUTING_NATIVE_RETAINED_SIZE);

        Map<Long, Integer> nativeSizes = new HashMap<Long, Integer>() {{
            put(0L, 0);
        }};

        for (Instance cleaner : graph.getInstances().stream()
                .filter(instance -> instance.getInstanceClassName().equals("sun.misc.Cleaner"))
                .toList()) {
            Field thunkField = cleaner.getField("sun.misc.Cleaner", "thunk");
            Object thunkId = thunkField.getValue();
            Field referentField = cleaner.getField("java.lang.ref.Reference", "referent");
            Object referentId = referentField.getValue();

            if (thunkId != null && referentId != null) {
                Object thunkRecord = thunkField.getValue().asObject();
                if (thunkRecord instanceof HeapInstance && thunkRecord.instanceOf("libcore.util.NativeAllocationRegistry$CleanerThunk")) {
                    Field allocationRegistryIdField = thunkRecord.getField("libcore.util.NativeAllocationRegistry$CleanerThunk", "this$0");
                    if (allocationRegistryIdField != null && allocationRegistryIdField.getValue() != null && allocationRegistryIdField.getValue().isNonNullReference()) {
                        Object allocationRegistryRecord = allocationRegistryIdField.getValue().asObject();
                        if (allocationRegistryRecord instanceof HeapInstance && allocationRegistryRecord.instanceOf("libcore.util.NativeAllocationRegistry")) {
                            int nativeSize = nativeSizes.getOrDefault(referentId, 0);
                            long size = (long) allocationRegistryRecord.getField("libcore.util.NativeAllocationRegistry", "size").getValue().asLong();
                            nativeSizes.put(referentId, nativeSize + (int) size);
                        }
                    }
                }
            }
        }

        listener.onAnalysisProgress(COMPUTING_RETAINED_SIZE);

        Map<Long, Integer> sizeByDominator = new LinkedHashMap<Long, Integer>() {{
            put(0L, 0);
        }};

        Set<Long> leakingInstanceIds = new HashSet<>();
        for (PathNode pathNode : pathsToLeakingInstances) {
            Long leakingInstanceObjectId = pathNode.getObjectId();
            leakingInstanceIds.add(leakingInstanceObjectId);
            HeapInstance instanceRecord = (HeapInstance) graph.findObjectById(leakingInstanceObjectId).asInstance();
            HeapClass heapClass = instanceRecord.getInstanceClass();
            int retainedSize = sizeByDominator.getOrDefault(leakingInstanceObjectId, 0);

            retainedSize += heapClass.getInstanceByteSize();
            sizeByDominator.put(leakingInstanceObjectId, retainedSize);
        }

        for (Long instanceId : dominatedInstances) {
            if (!leakingInstanceIds.contains(instanceId)) {
                int currentSize = sizeByDominator.getOrDefault(instanceId, 0);
                int nativeSize = nativeSizes.getOrDefault(instanceId, 0);
                int shallowSize = 0;
                Object objectRecord = graph.findObjectById(instanceId);
                if (objectRecord instanceof HeapInstance) {
                    shallowSize = ((HeapInstance) objectRecord).getByteSize();
                } else if (objectRecord instanceof HeapObjectArray) {
                    shallowSize = ((HeapObjectArray) objectRecord).readByteSize();
                } else if (objectRecord instanceof HeapPrimitiveArray) {
                    shallowSize = ((HeapPrimitiveArray) objectRecord).readByteSize();
                } else if (objectRecord instanceof HeapClass) {
                    throw new IllegalStateException("Unexpected class record " + objectRecord);
                }
                sizeByDominator.put(instanceId, currentSize + nativeSize + shallowSize);
            }
        }

        boolean sizedMoved;
        do {
            sizedMoved = false;
            for (Long leakingInstanceId : pathsToLeakingInstances.stream().map(PathNode::getObjectId).toList()) {
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
        return pathsToLeakingInstances.stream().map(pathNode -> sizeByDominator.get(pathNode.getObjectId())).toList();
    }

        private Pair<List<ApplicationLeak>, List<LibraryLeak>> buildLeakTraces(PathFindingResults pathFindingResults) {
        this.pathFindingResults = pathFindingResults;
        List<RetainedSizes> retainedSizes = computeRetainedSizes(pathFindingResults);

        listener.onAnalysisProgress(BUILDING_LEAK_TRACES);

        Map<String, List<LeakTrace>> applicationLeaksMap = new HashMap<>();
        Map<String, Map.Entry<LibraryLeakReferenceMatcher, List<LeakTrace>>> libraryLeaksMap = new HashMap<>();

        List<RetainedObjectNode> deduplicatedPaths = deduplicateShortestPaths(pathFindingResults.pathsToLeakingObjects);

        if (deduplicatedPaths.size() != pathFindingResults.pathsToLeakingObjects.size()) {
            SharkLog.d("Found " + pathFindingResults.pathsToLeakingObjects.size() + " paths to retained objects, down to " +
                    deduplicatedPaths.size() + " after removing duplicated paths");
        } else {
            SharkLog.d("Found " + deduplicatedPaths.size() + " paths to retained objects");
        }

        for (int index = 0; index < deduplicatedPaths.size(); index++) {
            RetainedObjectNode retainedObjectNode = deduplicatedPaths.get(index);

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

            List<ReferencePathElement> referencePath = buildReferencePath(shortestChildPath, leakTraceObjects);

            LeakTrace leakTrace = new LeakTrace(
                    GcRootType.fromGcRoot(rootNode.gcRoot),
                    referencePath,
                    leakTraceObjects.get(leakTraceObjects.size() - 1),
                    retainedSizes.get(index) != null ? retainedSizes.get(index).getRetainedHeapByteSize() : null
            );

            LibraryLeakNode firstLibraryLeakNode = rootNode instanceof LibraryLeakNode ? (LibraryLeakNode) rootNode :
                    shortestChildPath.stream().filter(n -> n instanceof LibraryLeakNode).map(n -> (LibraryLeakNode) n).findFirst().orElse(null);

            if (firstLibraryLeakNode != null) {
                LibraryLeakReferenceMatcher matcher = firstLibraryLeakNode.getMatcher();
                String signature = getSHA1(matcher.getPattern().toString());
                libraryLeaksMap.computeIfAbsent(signature, s -> Map.entry(matcher, new ArrayList<>())).getValue().add(leakTrace);
            } else {
                applicationLeaksMap.computeIfAbsent(leakTrace.getSignature(), s -> new ArrayList<>()).add(leakTrace);
            }
        }

        List<ApplicationLeak> applicationLeaks = new ArrayList<>();
        for (Map.Entry<String, List<LeakTrace>> entry : applicationLeaksMap.entrySet()) {
            applicationLeaks.add(new ApplicationLeak(entry.getValue()));
        }

        List<LibraryLeak> libraryLeaks = new ArrayList<>();
        for (Map.Entry<String, Map.Entry<LibraryLeakReferenceMatcher, List<LeakTrace>>> entry : libraryLeaksMap.entrySet()) {
            LibraryLeakReferenceMatcher matcher = entry.getValue().getKey();
            List<LeakTrace> leakTraces = entry.getValue().getValue();
            libraryLeaks.add(new LibraryLeak(leakTraces, matcher.getPattern(), matcher.getDescription()));
        }

        return new Pair<>(applicationLeaks, libraryLeaks);
    }

  private static List<LeakTraceObject> buildLeakTraceObjects(
      List<ObjectInspector> objectInspectors,
      List<HeapObject> pathHeapObjects
  ) {
    List<ObjectReporter> leakReporters = pathHeapObjects.stream()
        .map(heapObject -> new ObjectReporter(heapObject))
        .collect(Collectors.toList());

    objectInspectors.forEach(inspector ->
        leakReporters.forEach(reporter -> inspector.inspect(reporter))
    );

    List<Pair<LeakingStatus, String>> leakStatuses = computeLeakStatuses(leakReporters);

    return IntStream.range(0, pathHeapObjects.size())
        .mapToObj(index -> {
          HeapObject heapObject = pathHeapObjects.get(index);
          ObjectReporter leakReporter = leakReporters.get(index);
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
              leakStatuses.get(index).first,
              leakStatuses.get(index).second
          );
        })
        .collect(Collectors.toList());
  }

  private static List<LeakTraceReference> buildReferencePath(
      List<ChildNode> shortestChildPath,
      List<LeakTraceObject> leakTraceObjects
  ) {
    return IntStream.range(0, shortestChildPath.size())
        .mapToObj(index -> {
          ChildNode childNode = shortestChildPath.get(index);
          LeakTraceObject originObject = leakTraceObjects.get(index);

          return new LeakTraceReference(
              originObject,
              childNode.getRefFromParentType(),
              childNode.getRefFromParentName()
          );
        })
        .collect(Collectors.toList());
  }

  private static List<Pair<LeakingStatus, String>> computeLeakStatuses(List<ObjectReporter> leakReporters) {
    int lastElementIndex = leakReporters.size() - 1;

    int lastNotLeakingElementIndex = -1;
    int firstLeakingElementIndex = lastElementIndex;

    List<Pair<LeakingStatusString>> leakStatuses = new ArrayList<>();

    for (int index = 0; index < leakReporters.size(); index++) {
      Pair<LeakingStatus, String> resolvedStatusPair = resolveStatus(leakReporters.get(index), index == lastElementIndex);

      leakStatuses.add(resolvedStatusPair);
      LeakingStatus leakStatus = resolvedStatusPair.first;
      if (leakStatus == LeakingStatus.NOT_LEAKING) {
        lastNotLeakingElementIndex = index;

        firstLeakingElementIndex = lastElementIndex;
      } else if (leakStatus == LeakingStatus.LEAKING && firstLeakingElementIndex == lastElementIndex) {
        firstLeakingElementIndex = index;
      }
    }

    List<String> simpleClassNames = leakReporters.stream()
        .map(reporter -> recordClassName(reporter.getHeapObject()).lastSegment('.'))
        .collect(Collectors.toList());

    for (int i = 0; i < lastNotLeakingElementIndex; i++) {
      Pair<LeakingStatus, String> leakStatusReason = leakStatuses.get(i);
      int nextNotLeakingIndex = IntStream.range(i + 1, lastNotLeakingElementIndex + 1)
          .filter(index -> leakStatuses.get(index).first == LeakingStatus.NOT_LEAKING)
          .findFirst()
          .orElse(lastNotLeakingElementIndex);

      String nextNotLeakingName = simpleClassNames.get(nextNotLeakingIndex);
      leakStatuses.set(i, new Pair<>(LeakingStatus.NOT_LEAKING, "$nextNotLeakingName↓ is not leaking"));
    }

    if (firstLeakingElementIndex < lastElementIndex - 1) {
      for (int i = lastElementIndex - 1; i > firstLeakingElementIndex; i--) {
        Pair<LeakingStatus, String> leakStatusReason = leakStatuses.get(i);
        int previousLeakingIndex = IntStream.range(i - 1, firstLeakingElementIndex - 1, -1)
            .filter(index -> leakStatuses.get(index).first == LeakingStatus.LEAKING)
            .findFirst()
            .orElse(firstLeakingElementIndex);

        String previousLeakingName = simpleClassNames.get(previousLeakingIndex);
        leakStatuses.set(i, new Pair<>(LeakingStatus.LEAKING, "$previousLeakingName↑ is leaking"));
      }
    }
    return leakStatuses;
  }

    private static class TrieNode {
        long objectId;

        static class ParentNode extends TrieNode {
            Map<Long, TrieNode> children;

            ParentNode(long objectId) {
                this.objectId = objectId;
                this.children = new HashMap<>();
            }

            @Override
            public String toString() {
                return "ParentNode(objectId=" + objectId + ", children=" + children + ")";
            }
        }

        static class LeafNode extends TrieNode {
            ReferencePathNode pathNode;

            LeafNode(long objectId, ReferencePathNode pathNode) {
                this.objectId = objectId;
                this.pathNode = pathNode;
            }
        }
    }

    private List<ReferencePathNode> deduplicateShortestPaths(List<ReferencePathNode> inputPathResults) {
        TrieNode rootTrieNode = new TrieNode.ParentNode(0);

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

    private void updateTrie(
            ReferencePathNode pathNode,
            List<Long> path,
            int pathIndex,
            TrieNode.ParentNode parentNode
    ) {
        long objectId = path.get(pathIndex);
        if (pathIndex == path.size() - 1) {
            parentNode.children.put(objectId, new TrieNode.LeafNode(objectId, pathNode));
        } else {
            TrieNode childNode = parentNode.children.get(objectId);
            if (childNode == null) {
                TrieNode.ParentNode newChildNode = new TrieNode.ParentNode(objectId);
                parentNode.children.put(objectId, newChildNode);
                childNode = newChildNode;
            }
            if (childNode instanceof TrieNode.ParentNode) {
                updateTrie(pathNode, path, pathIndex + 1, (TrieNode.ParentNode) childNode);
            }
        }
    }

    private void findResultsInTrie(
            TrieNode.ParentNode parentNode,
            List<ReferencePathNode> outputPathResults
    ) {
        for (TrieNode childNode : parentNode.children.values()) {
            if (childNode instanceof TrieNode.ParentNode) {
                findResultsInTrie((TrieNode.ParentNode) childNode, outputPathResults);
            } else if (childNode instanceof TrieNode.LeafNode) {
                outputPathResults.add(((TrieNode.LeafNode) childNode).pathNode);
            }
        }
    }

    private Pair < LeakingStatus, String > resolveStatus(ObjectReporter reporter, boolean leakingWins) {
  String reason = "";
  LeakingStatus status = LeakingStatus.UNKNOWN;
  if (!reporter.notLeakingReasons.isEmpty()) {
    status = LeakingStatus.NOT_LEAKING;
    List notLeakingReasonsList = reporter.notLeakingReasons;
    for (int i = 0; i < notLeakingReasonsList.size() - 1; i++) {
      reason += notLeakingReasonsList.get(i) + " and ";
    }
    reason += notLeakingReasonsList.get(notLeakingReasonsList.size() - 1);
  }
  List leakingReasons = reporter.leakingReasons;
  if (!leakingReasons.isEmpty()) {
    StringBuilder winReasons = new StringBuilder();
    for (int i = 0; i < leakingReasons.size() - 1; i++) {
      winReasons.append(leakingReasons.get(i));
      winReasons.append(" and ");
    }
    winReasons.append(leakingReasons.get(leakingReasons.size() - 1));
    if (status == LeakingStatus.NOT_LEAKING) {
      if (leakingWins) {
        status = LeakingStatus.LEAKING;
        reason = "Leaking reasons: " + winReasons + ". Conflicts with " + reason;
      } else {
        reason += ". Conflicts with Leaking reasons: " + winReasons;
      }
    } else {
      status = LeakingStatus.LEAKING;
      reason = "Leaking reasons: " + winReasons;
    }
  }
  return new Pair < > (status, reason);
}

private String recordClassName(HeapObject heap) {
  String className;
  if (heap instanceof HeapClass) {
    className = ((HeapClass) heap).name;
  } else if (heap instanceof HeapInstance) {
    className = ((HeapInstance) heap).instanceClassName;
  } else if (heap instanceof HeapObjectArray) {
    className = ((HeapObjectArray) heap).arrayClassName;
  } else if (heap instanceof HeapPrimitiveArray) {
    className = ((HeapPrimitiveArray) heap).arrayClassName;
  } else {
    className = "";
  }
  return className;
}

private long since(long analysisStartNanoTime) {
  return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime);
}


}