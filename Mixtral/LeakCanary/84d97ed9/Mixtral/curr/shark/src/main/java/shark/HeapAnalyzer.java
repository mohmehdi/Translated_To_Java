

package shark;

import shark.OnAnalysisProgressListener.Step;
import shark.GcRoot.JavaFrame;
import shark.GcRoot.JniGlobal;
import shark.GcRoot.JniLocal;
import shark.GcRoot.JniMonitor;
import shark.GcRoot.MonitorUsed;
import shark.GcRoot.NativeStack;
import shark.GcRoot.ReferenceCleanup;
import shark.GcRoot.StickyClass;
import shark.GcRoot.ThreadBlock;
import shark.GcRoot.ThreadObject;
import shark.HeapAnalyzer.TrieNode.LeafNode;
import shark.HeapAnalyzer.TrieNode.ParentNode;
import shark.HeapObject.HeapClass;
import shark.HeapObject.HeapInstance;
import shark.HeapObject.HeapObjectArray;
import shark.HeapObject.HeapPrimitiveArray;
import shark.LeakNodeStatus.LEAKING;
import shark.LeakNodeStatus.NOT_LEAKING;
import shark.LeakNodeStatus.UNKNOWN;
import shark.LeakTraceElement.Holder.ARRAY;
import shark.LeakTraceElement.Holder.CLASS;
import shark.LeakTraceElement.Holder.OBJECT;
import shark.LeakTraceElement.Holder.THREAD;
import shark.internal.ReferencePathNode.ChildNode;
import shark.internal.ReferencePathNode.ChildNode.LibraryLeakNode;
import shark.internal.ReferencePathNode.RootNode;
import shark.internal.ShortestPathFinder;
import shark.internal.ShortestPathFinder.Results;
import shark.internal.hppc.LongLongScatterMap;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HeapAnalyzer {

    private final OnAnalysisProgressListener listener;

    public HeapAnalyzer(OnAnalysisProgressListener listener) {
        this.listener = listener;
    }

    public HeapAnalysis checkForLeaks(
            File heapDumpFile,
            List<ReferenceMatcher> referenceMatchers,
            boolean computeRetainedHeapSize,
            List<ObjectInspector> objectInspectors,
            List<ObjectInspector> leakFinders) {
        long analysisStartNanoTime = System.nanoTime();

        if (!heapDumpFile.exists()) {
            IllegalArgumentException exception = new IllegalArgumentException("File does not exist: " + heapDumpFile);
            return new HeapAnalysisFailure(
                    heapDumpFile, System.currentTimeMillis(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime),
                    new HeapAnalysisException(exception));
        }

        try {
            listener.onAnalysisProgress(Step.PARSING_HEAP_DUMP);
            // Hprof.open(heapDumpFile).use { hprof ->} is not directly translatable to Java
            // You need to replace the usage of 'hprof' with 'HeapGraph' after it's created
            HeapGraph graph = HprofHeapGraph.indexHprof(heapDumpFile);
            listener.onAnalysisProgress(Step.FINDING_LEAKING_INSTANCES);

            Set<Long> leakingInstanceObjectIds = findLeakingInstances(graph, leakFinders);

            Results results = findShortestPaths(
                    graph, referenceMatchers, leakingInstanceObjectIds, computeRetainedHeapSize);

            List<Integer> retainedSizes = null;
            if (computeRetainedHeapSize) {
                retainedSizes = computeRetainedSizes(graph, results.shortestPathsToLeakingInstances, results.dominatedInstances);
            }

            List<ApplicationLeak> applicationLeaks = new ArrayList<>();
            List<LibraryLeak> libraryLeaks = new ArrayList<>();

            buildLeakTraces(
                    objectInspectors, results.shortestPathsToLeakingInstances, graph, retainedSizes,
                    applicationLeaks, libraryLeaks);

            return new HeapAnalysisSuccess(
                    heapDumpFile, System.currentTimeMillis(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime),
                    applicationLeaks, libraryLeaks);
        } catch (Throwable exception) {
            return new HeapAnalysisFailure(
                    heapDumpFile, System.currentTimeMillis(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime),
                    new HeapAnalysisException(exception));
        }
    }

    private Set<Long> findLeakingInstances(HeapGraph graph, List<ObjectInspector> objectInspectors) {
        return graph.getObjects()
                .filter(objectRecord -> {
                    ObjectReporter reporter = new ObjectReporter(objectRecord);
                    objectInspectors.forEach(inspector -> inspector.inspect(reporter));
                    return !reporter.leakingStatuses.isEmpty();
                })
                .map(HeapObjectRecord::getObjectId)
                .toSet();
    }

    private Results findShortestPaths(
            HeapGraph graph,
            List<ReferenceMatcher> referenceMatchers,
            Set<Long> leakingInstanceObjectIds,
            boolean computeDominators) {
        ShortestPathFinder pathFinder = new ShortestPathFinder();
        return pathFinder.findPaths(
                graph, referenceMatchers, leakingInstanceObjectIds, computeDominators, listener);
    }

    private static class TrieNode {
        final long objectId;

        public TrieNode(long objectId) {
            this.objectId = objectId;
        }

        static class ParentNode extends TrieNode {
            final Map<Long, TrieNode> children = new HashMap<>();

            public ParentNode(long objectId) {
                super(objectId);
            }

            @Override
            public String toString() {
                return "ParentNode(objectId=" + objectId + ", children=" + children + ")";
            }
        }

        static class LeafNode extends TrieNode {
            final ReferencePathNode pathNode;

            public LeafNode(long objectId, ReferencePathNode pathNode) {
                super(objectId);
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
                path.add(0, leakNode.getInstance());
                leakNode = leakNode.getParent();
            }
            path.add(0, leakNode.getInstance());
            updateTrie(pathNode, path, 0, (TrieNode.ParentNode) rootTrieNode);
        }

        List<ReferencePathNode> outputPathResults = new ArrayList<>();
        findResultsInTrie(rootTrieNode, outputPathResults);
        return outputPathResults;
    }

    private void updateTrie(
            ReferencePathNode pathNode,
            List<Long> path,
            int pathIndex,
            TrieNode.ParentNode parentNode) {
        long objectId = path.get(pathIndex);
        if (pathIndex == path.size() - 1) {
            parentNode.children.put(objectId, new TrieNode.LeafNode(objectId, pathNode));
        } else {
            TrieNode childNode = parentNode.children.get(objectId);
            if (childNode == null) {
                childNode = new TrieNode.ParentNode(objectId);
                parentNode.children.put(objectId, childNode);
            }
            if (childNode instanceof TrieNode.ParentNode) {
                updateTrie(pathNode, path, pathIndex + 1, (TrieNode.ParentNode) childNode);
            }
        }
    }

    private void findResultsInTrie(
            TrieNode.ParentNode parentNode,
            List<ReferencePathNode> outputPathResults) {
        parentNode.children.values().forEach(childNode -> {
            if (childNode instanceof TrieNode.ParentNode) {
                findResultsInTrie((TrieNode.ParentNode) childNode, outputPathResults);
            } else if (childNode instanceof TrieNode.LeafNode) {
                outputPathResults.add(((TrieNode.LeafNode) childNode).pathNode);
            }
        });
    }

    private List<Integer> computeRetainedSizes(
            HeapGraph graph,
            List<ReferencePathNode> results,
            LongLongScatterMap dominatedInstances) {
        listener.onAnalysisProgress(Step.COMPUTING_NATIVE_RETAINED_SIZE);

        Map<Long, Integer> nativeSizes = new HashMap<>();
        nativeSizes.put(0L, 0);

        graph.getInstances()
                .filter(heapObjectRecord -> heapObjectRecord.getInstanceClassName().equals("sun.misc.Cleaner"))
                .forEach(cleaner -> {
                    HeapObjectRecord thunkField = cleaner.getField("sun.misc.Cleaner", "thunk");
                    long thunkId = thunkField != null ? thunkField.getValue().asNonNullObjectId() : -1;
                    long referentId = cleaner.getField("java.lang.ref.Reference", "referent")
                            .getValue().asNonNullObjectId();
                    if (thunkId != -1 && referentId != -1) {
                        HeapInstance thunkRecord = (HeapInstance) thunkField.getValue().asObject();
                        if (thunkRecord.instanceOf("libcore.util.NativeAllocationRegistry$CleanerThunk")) {
                            HeapObjectRecord allocationRegistryIdField = thunkRecord.getField(
                                    "libcore.util.NativeAllocationRegistry$CleanerThunk", "this$0");
                            if (allocationRegistryIdField != null && allocationRegistryIdField.getValue().isNonNullReference()) {
                                HeapInstance allocationRegistryRecord = (HeapInstance) allocationRegistryIdField.getValue().asObject();
                                if (allocationRegistryRecord.instanceOf("libcore.util.NativeAllocationRegistry")) {
                                    int nativeSize = nativeSizes.get(referentId);
                                    nativeSize += allocationRegistryRecord.getField(
                                            "libcore.util.NativeAllocationRegistry", "size")
                                            .getValue().asLong().intValue();
                                    nativeSizes.put(referentId, nativeSize);
                                }
                            }
                        }
                    }
                });

        listener.onAnalysisProgress(Step.COMPUTING_RETAINED_SIZE);

        Map<Long, Integer> sizeByDominator = new LinkedHashMap<>();

        Set<Long> leakingInstanceIds = new HashSet<>();
        results.forEach(pathNode -> {
            long leakingInstanceObjectId = pathNode.getInstance();
            leakingInstanceIds.add(leakingInstanceObjectId);
            HeapObjectRecord instanceRecord = graph.findObjectById(leakingInstanceObjectId).asInstance();
            HeapClass heapClass = instanceRecord.getInstanceClass();
            int retainedSize = sizeByDominator.getOrDefault(leakingInstanceObjectId, 0);

            retainedSize += heapClass.getInstanceByteSize();
            sizeByDominator.put(leakingInstanceObjectId, retainedSize);
        });

        dominatedInstances.forEach((instanceId, dominatorId) -> {
            if (!leakingInstanceIds.contains(instanceId)) {
                int currentSize = sizeByDominator.getOrDefault(dominatorId, 0);
                int nativeSize = nativeSizes.getOrDefault(instanceId, 0);
                int shallowSize = 0;
                HeapObjectRecord objectRecord = graph.findObjectById(instanceId);
                if (objectRecord instanceof HeapInstance) {
                    shallowSize = ((HeapInstance) objectRecord).getByteSize();
                } else if (objectRecord instanceof HeapObjectArray) {
                    shallowSize = ((HeapObjectArray) objectRecord).readByteSize();
                } else if (objectRecord instanceof HeapPrimitiveArray) {
                    shallowSize = ((HeapPrimitiveArray) objectRecord).readByteSize();
                } else {
                    throw new IllegalStateException("Unexpected class record " + objectRecord);
                }
                sizeByDominator.put(dominatorId, currentSize + nativeSize + shallowSize);
            }
        });

        boolean sizedMoved;
        do {
            sizedMoved = false;
            results.forEach(pathNode -> {
                long leakingInstanceId = pathNode.getInstance();
                HeapObjectRecord dominator = dominatedInstances.get(leakingInstanceId);
                if (dominator != null) {
                    int retainedSize = sizeByDominator.get(leakingInstanceId);
                    if (retainedSize > 0) {
                        sizeByDominator.put(leakingInstanceId, 0);
                        int dominatorRetainedSize = sizeByDominator.get(dominator.getObjectId());
                        sizeByDominator.put(dominator.getObjectId(), retainedSize + dominatorRetainedSize);
                        sizedMoved = true;
                    }
                }
            });
        } while (sizedMoved);
        dominatedInstances.release();
        return results.stream().map(pathNode -> sizeByDominator.get(pathNode.getInstance())).toList();
    }

    private List<LeakTrace> buildLeakTraces(
            List<ObjectInspector> objectInspectors,
            List<ReferencePathNode> shortestPathsToLeakingInstances,
            HeapGraph graph,
            List<Integer> retainedSizes) {
        listener.onAnalysisProgress(Step.BUILDING_LEAK_TRACES);

        List<ApplicationLeak> applicationLeaks = new ArrayList<>();
        List<LibraryLeak> libraryLeaks = new ArrayList<>();

        List<ReferencePathNode> deduplicatedPaths = deduplicateShortestPaths(shortestPathsToLeakingInstances);

        for (int index = 0; index < deduplicatedPaths.size(); index++) {
            List<ChildNode> shortestChildPath = new ArrayList<>();

            ReferencePathNode node = deduplicatedPaths.get(index);
            while (node instanceof ChildNode) {
                shortestChildPath.add(0, (ChildNode) node);
                node = node.getParent();
            }
            RootNode rootNode = (RootNode) node;

            LeakTrace leakTrace = buildLeakTrace(graph, objectInspectors, rootNode, shortestChildPath);

            String className = recordClassName(graph.findObjectById(node.getInstance()));

            LibraryLeakNode firstLibraryLeakNode = shortestChildPath.stream()
                    .filter(childNode -> childNode instanceof LibraryLeakNode)
                    .map(childNode -> (LibraryLeakNode) childNode)
                    .findFirst()
                    .orElse(null);

            if (firstLibraryLeakNode != null) {
                ReferenceMatcher matcher = firstLibraryLeakNode.getMatcher();
                libraryLeaks.add(new LibraryLeak(
                        className, leakTrace, retainedSizes.get(index), matcher.getPattern(), matcher.getDescription()));
            } else {
                applicationLeaks.add(new ApplicationLeak(className, leakTrace, retainedSizes.get(index)));
            }
        }
        return applicationLeaks;
    }

    private LeakTrace buildLeakTrace(
            HeapGraph graph,
            List<ObjectInspector> objectInspectors,
            RootNode rootNode,
            List<ChildNode> shortestChildPath) {
        List<ReferencePathNode> shortestPath = new ArrayList<>(shortestChildPath);
        shortestPath.add(0, rootNode);

        List<ObjectReporter> leakReporters = shortestPath.stream()
                .map(pathNode -> new ObjectReporter(graph.findObjectById(pathNode.getInstance())))
                .toList();

        objectInspectors.forEach(inspector -> {
            leakReporters.forEach(reporter -> inspector.inspect(reporter));
        });

        List<LeakNodeStatusAndReason> leakStatuses = computeLeakStatuses(rootNode, leakReporters);

        List<LeakTraceElement> elements = shortestPath.stream()
                .map((index, pathNode) -> buildLeakElement(graph, pathNode,
                        index < shortestPath.size() - 1 ? shortestPath.get(index + 1) : null,
                        leakReporters.get(index).getLabels(), leakStatuses.get(index)))
                .toList();
        return new LeakTrace(elements);
    }

    private List<LeakNodeStatusAndReason> computeLeakStatuses(
            RootNode rootNode,
            List<ObjectReporter> leakReporters) {
        int lastElementIndex = leakReporters.size() - 1;

        ObjectReporter rootNodeReporter = leakReporters.get(0);

        rootNodeReporter.addLabel(
                "GC Root: " + switch (rootNode.getGcRoot()) {
                    case ThreadObject -> "Thread object";
                    case JniGlobal -> "Global variable in native code";
                    case JniLocal -> "Local variable in native code";
                    case JavaFrame -> "Java local variable";
                    case NativeStack -> "Input or output parameters in native code";
                    case StickyClass -> "System class";
                    case ThreadBlock -> "Thread block";
                    case MonitorUsed -> "Monitor (anything that called the wait() or notify() methods, or that is synchronized.)";
                    case ReferenceCleanup -> "Reference cleanup";
                    case JniMonitor -> "Root JNI monitor";
                    default -> throw new IllegalStateException("Unexpected gc root " + rootNode.getGcRoot());
                });

        int lastNotLeakingElementIndex = 0;
        int firstLeakingElementIndex = lastElementIndex;

        List<LeakNodeStatusAndReason> leakStatuses = new ArrayList<>();

        for (int index = 0; index <= lastElementIndex; index++) {
            ObjectReporter reporter = leakReporters.get(index);
            LeakNodeStatusAndReason leakStatus = resolveStatus(reporter);
            leakStatuses.add(leakStatus);
            if (leakStatus.getStatus() == NOT_LEAKING) {
                lastNotLeakingElementIndex = index;
            } else if (firstLeakingElementIndex == lastElementIndex && leakStatus.getStatus() == LEAKING) {
                firstLeakingElementIndex = index;
            }
        }

        List<String> simpleClassNames = leakReporters.stream()
                .map(reporter -> recordClassName(reporter.getHeapObject()).split("\\.")[recordClassName(reporter.getHeapObject()).split("\\.").length - 1])
                .toList();

        for (int i = 0; i <= lastElementIndex; i++) {
            LeakNodeStatusAndReason leakStatus = leakStatuses.get(i);
            if (i < lastNotLeakingElementIndex) {
                String nextNotLeakingName = simpleClassNames.get(i + 1);
                leakStatuses.set(i, new LeakNodeStatusAndReason(
                        LEAKING, "Next object " + nextNotLeakingName + " is not leaking"));
            } else if (i > firstLeakingElementIndex) {
                String previousLeakingName = simpleClassNames.get(i - 1);
                leakStatuses.set(i, new LeakNodeStatusAndReason(
                        NOT_LEAKING, "Previous object " + previousLeakingName + " is leaking"));
            }
        }
        return leakStatuses;
    }

    private LeakNodeStatusAndReason resolveStatus(ObjectReporter reporter) {
        LeakNodeStatusAndReason current = LeakNodeStatusAndReason.unknown();
        for (LeakNodeStatusAndReason statusAndReason : reporter.leakNodeStatuses) {
            current = switch (current.getStatus()) {
                case UNKNOWN -> statusAndReason;
                case LEAKING -> new LeakNodeStatusAndReason(
                        LEAKING, current.getReason() + " and " + statusAndReason.getReason());
                case NOT_LEAKING -> new LeakNodeStatusAndReason(
                        NOT_LEAKING, current.getReason() + " and " + statusAndReason.getReason());
            };
        }
        return current;
    }

    private LeakTraceElement buildLeakElement(
            HeapGraph graph,
            ReferencePathNode node,
            LeakReference reference,
            List<String> labels,
            LeakNodeStatusAndReason leakStatus) {
        long objectId = node.getInstance();

        HeapObjectRecord graphRecord = graph.findObjectById(objectId);

        String className = recordClassName(graphRecord);

        Holder holderType = graphRecord instanceof HeapClass ? Holder.CLASS :
                graphRecord instanceof HeapObjectArray || graphRecord instanceof HeapPrimitiveArray ? Holder.ARRAY :
                graphRecord instanceof HeapInstance instanceRecord && instanceRecord.getInstanceClass().classHierarchy().any(it -> it.name().equals(Thread.class.getName())) ? Holder.THREAD :
                Holder.OBJECT;
        return new LeakTraceElement(reference, holderType, className, labels, leakStatus);
    }

    private String recordClassName(HeapObject heap) {
        if (heap instanceof HeapClass) {
            return ((HeapClass) heap).name;
        } else if (heap instanceof HeapInstance) {
            return ((HeapInstance) heap).instanceClassName;
        } else if (heap instanceof HeapObjectArray) {
            return ((HeapObjectArray) heap).arrayClassName;
        } else if (heap instanceof HeapPrimitiveArray) {
            return ((HeapPrimitiveArray) heap).arrayClassName;
        }
        return null;
    }

    private long since(long analysisStartNanoTime) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime);
    }
}