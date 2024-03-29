
package shark;

import shark.AnalyzerProgressListener.Step;
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
import shark.Leak.ApplicationLeak;
import shark.Leak.LibraryLeak;
import shark.LeakNodeStatus;
import shark.LeakNodeStatus.LEAKING;
import shark.LeakNodeStatus.NOT_LEAKING;
import shark.LeakNodeStatus.UNKNOWN;
import shark.LeakTraceElement.Holder;
import shark.internal.ReferencePathNode;
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

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class HeapAnalyzer {

public abstract class TrieNode {
    public abstract long objectId;

    public static class ParentNode extends TrieNode {
        public final long objectId;
        public final Map<Long, TrieNode> children = new HashMap<>();

        public ParentNode(long objectId) {
            this.objectId = objectId;
        }

        @Override
        public String toString() {
            return "ParentNode(objectId=" + objectId + ", children=" + children + ")";
        }
    }

    public static class LeafNode extends TrieNode {
        public final long objectId;
        public final ReferencePathNode pathNode;

        public LeafNode(long objectId, ReferencePathNode pathNode) {
            this.objectId = objectId;
            this.pathNode = pathNode;
        }
    }
}
    private final AnalyzerProgressListener listener;

    public HeapAnalyzer(AnalyzerProgressListener listener) {
        this.listener = listener;
    }

    public HeapAnalysis checkForLeaks(File heapDumpFile, List<ReferenceMatcher> referenceMatchers, boolean computeRetainedHeapSize, List<ObjectInspector> objectInspectors, List<ObjectInspector> leakFinders) {
        long analysisStartNanoTime = System.nanoTime();

        if (!heapDumpFile.exists()) {
            IllegalArgumentException exception = new IllegalArgumentException("File does not exist: " + heapDumpFile);
            return new HeapAnalysisFailure(heapDumpFile, System.currentTimeMillis(), since(analysisStartNanoTime), new HeapAnalysisException(exception));
        }

        try {
            listener.onProgressUpdate(Step.PARSING_HEAP_DUMP);
            try (Hprof hprof = Hprof.open(heapDumpFile)) {
                HeapGraph graph = HeapGraph.indexHprof(hprof);
                listener.onProgressUpdate(Step.FINDING_LEAKING_INSTANCES);

                Set<Long> leakingInstanceObjectIds = findLeakingInstances(graph, leakFinders);

                Results results = findShortestPaths(graph, referenceMatchers, leakingInstanceObjectIds, computeRetainedHeapSize);

                List<Integer> retainedSizes = computeRetainedHeapSize ? computeRetainedSizes(graph, results, dominatedInstances) : null;

                Pair<List<ApplicationLeak>, List<LibraryLeak>> leaks = buildLeakTraces(objectInspectors, results, graph, retainedSizes);

                return new HeapAnalysisSuccess(heapDumpFile, System.currentTimeMillis(), since(analysisStartNanoTime), leaks.getFirst(), leaks.getSecond());
            }
        } catch (Throwable exception) {
            return new HeapAnalysisFailure(heapDumpFile, System.currentTimeMillis(), since(analysisStartNanoTime), new HeapAnalysisException(exception));
        }
    }

    private Set<Long> findLeakingInstances(HeapGraph graph, List<ObjectInspector> objectInspectors) {
        return graph.objects
                .filter(objectRecord -> {
                    ObjectReporter reporter = new ObjectReporter(objectRecord);
                    objectInspectors.forEach(inspector -> inspector.inspect(graph, reporter));
                    return !reporter.leakingStatuses.isEmpty();
                })
                .map(objectRecord -> objectRecord.objectId)
                .collect(Collectors.toSet());
    }

    private Results findShortestPaths(HeapGraph graph, List<ReferenceMatcher> referenceMatchers, Set<Long> leakingInstanceObjectIds, boolean computeDominators) {
        ShortestPathFinder pathFinder = new ShortestPathFinder();
        return pathFinder.findPaths(graph, referenceMatchers, leakingInstanceObjectIds, computeDominators, listener);
    }

    private List<ReferencePathNode> deduplicateShortestPaths(List<ReferencePathNode> inputPathResults) {
        ParentNode rootTrieNode = new ParentNode(0);

        for (ReferencePathNode pathNode : inputPathResults) {
            List<Long> path = new ArrayList<>();
            ReferencePathNode leakNode = pathNode;
            while (leakNode instanceof ChildNode) {
                path.add(0, leakNode.instance);
                leakNode = leakNode.parent;
            }
            path.add(0, leakNode.instance);
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
            ParentNode childNode = parentNode.children.get(objectId);
            if (childNode == null) {
                childNode = new ParentNode(objectId);
                parentNode.children.put(objectId, childNode);
            }
            if (childNode instanceof ParentNode) {
                updateTrie(pathNode, path, pathIndex + 1, (ParentNode) childNode);
            }
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

    private List<Integer> computeRetainedSizes(HeapGraph graph, List<ReferencePathNode> results, LongLongScatterMap dominatedInstances) {
        listener.onProgressUpdate(Step.COMPUTING_NATIVE_RETAINED_SIZE);

        Map<Long, Integer> nativeSizes = new HashMap<>();

        graph.instances
                .filter(instance -> instance.instanceClassName.equals("sun.misc.Cleaner"))
                .forEach(cleaner -> {
                    HprofRecordField thunkField = cleaner.get("sun.misc.Cleaner", "thunk");
                    long thunkId = thunkField != null ? thunkField.value.asNonNullObjectId : 0;
                    long referentId = cleaner.get("java.lang.ref.Reference", "referent") != null ? cleaner.get("java.lang.ref.Reference", "referent").value.asNonNullObjectId : 0;
                    if (thunkId != 0 && referentId != 0) {
                        HeapInstance thunkRecord = (HeapInstance) thunkField.value.asObject;
                        if (thunkRecord instanceof HeapInstance && thunkRecord.instanceOf("libcore.util.NativeAllocationRegistry$CleanerThunk")) {
                            HprofRecordField allocationRegistryIdField = thunkRecord.get("libcore.util.NativeAllocationRegistry$CleanerThunk", "this$0");
                            if (allocationRegistryIdField != null && allocationRegistryIdField.value.isNonNullReference) {
                                HeapInstance allocationRegistryRecord = (HeapInstance) allocationRegistryIdField.value.asObject;
                                if (allocationRegistryRecord instanceof HeapInstance && allocationRegistryRecord.instanceOf("libcore.util.NativeAllocationRegistry")) {
                                    int nativeSize = nativeSizes.getOrDefault(referentId, 0);
                                    nativeSize += allocationRegistryRecord.get("libcore.util.NativeAllocationRegistry", "size") != null ? (int) allocationRegistryRecord.get("libcore.util.NativeAllocationRegistry", "size").value.asLong : 0;
                                    nativeSizes.put(referentId, nativeSize);
                                }
                            }
                        }
                    }
                });

        listener.onProgressUpdate(Step.COMPUTING_RETAINED_SIZE);

        Map<Long, Integer> sizeByDominator = new LinkedHashMap<>();

        Set<Long> leakingInstanceIds = new HashSet<>();
        results.forEach(pathNode -> {
            long leakingInstanceObjectId = pathNode.instance;
            leakingInstanceIds.add(leakingInstanceObjectId);
            HeapInstance instanceRecord = (HeapInstance) graph.findObjectById(leakingInstanceObjectId);
            HeapClass heapClass = instanceRecord.instanceClass;
            int retainedSize = sizeByDominator.getOrDefault(leakingInstanceObjectId, 0);

            retainedSize += heapClass.instanceSize;
            sizeByDominator.put(leakingInstanceObjectId, retainedSize);
        });

        dominatedInstances.forEach((instanceId, dominatorId) -> {
            if (!leakingInstanceIds.contains(instanceId)) {
                int currentSize = sizeByDominator.getOrDefault(dominatorId, 0);
                int nativeSize = nativeSizes.getOrDefault(instanceId, 0);
                int shallowSize;
                HeapObject objectRecord = graph.findObjectById(instanceId);
                if (objectRecord instanceof HeapInstance) {
                    shallowSize = ((HeapInstance) objectRecord).size;
                } else if (objectRecord instanceof HeapObjectArray) {
                    shallowSize = ((HeapObjectArray) objectRecord).readSize();
                } else if (objectRecord instanceof HeapPrimitiveArray) {
                    shallowSize = ((HeapPrimitiveArray) objectRecord).readSize();
                } else {
                    throw new IllegalStateException("Unexpected class record " + objectRecord);
                }
                sizeByDominator.put(dominatorId, currentSize + nativeSize + shallowSize);
            }
        });

        boolean sizedMoved;
        do {
            sizedMoved = false;
            results.stream().map(pathNode -> pathNode.instance).forEach(leakingInstanceId -> {
                Long dominator = dominatedInstances.get(leakingInstanceId);
                if (dominator != null) {
                    int retainedSize = sizeByDominator.get(leakingInstanceId);
                    if (retainedSize > 0) {
                        sizeByDominator.put(leakingInstanceId, 0);
                        int dominatorRetainedSize = sizeByDominator.get(dominator);
                        sizeByDominator.put(dominator, retainedSize + dominatorRetainedSize);
                        sizedMoved = true;
                    }
                }
            });
        } while (sizedMoved);
        dominatedInstances.release();
        return results.stream().map(pathNode -> sizeByDominator.get(pathNode.instance)).collect(Collectors.toList());
    }

    private Pair<List<ApplicationLeak>, List<LibraryLeak>> buildLeakTraces(List<ObjectInspector> objectInspectors, List<ReferencePathNode> shortestPathsToLeakingInstances, HeapGraph graph, List<Integer> retainedSizes) {
        listener.onProgressUpdate(Step.BUILDING_LEAK_TRACES);

        List<ApplicationLeak> applicationLeaks = new ArrayList<>();
        List<LibraryLeak> libraryLeaks = new ArrayList<>();

        List<ReferencePathNode> deduplicatedPaths = deduplicateShortestPaths(shortestPathsToLeakingInstances);

        deduplicatedPaths.forEach((index, pathNode) -> {
            List<ChildNode> shortestChildPath = new ArrayList<>();

            ReferencePathNode node = pathNode;
            while (node instanceof ChildNode) {
                shortestChildPath.add(0, (ChildNode) node);
                node = node.parent;
            }
            RootNode rootNode = (RootNode) node;

            LeakTrace leakTrace = buildLeakTrace(graph, objectInspectors, rootNode, shortestChildPath);

            String className = recordClassName(graph.findObjectById(pathNode.instance));

            LibraryLeakNode firstLibraryLeakNode = shortestChildPath.stream().filter(it -> it instanceof LibraryLeakNode).findFirst().map(it -> (LibraryLeakNode) it).orElse(null);

            if (firstLibraryLeakNode != null) {
                ReferenceMatcher matcher = firstLibraryLeakNode.matcher;
                libraryLeaks.add(new LibraryLeak(className, leakTrace, retainedSizes.get(index), matcher.pattern, matcher.description));
            } else {
                applicationLeaks.add(new ApplicationLeak(className, leakTrace, retainedSizes.get(index)));
            }
        });
        return new Pair<>(applicationLeaks, libraryLeaks);
    }

    private LeakTrace buildLeakTrace(HeapGraph graph, List<ObjectInspector> objectInspectors, RootNode rootNode, List<ChildNode> shortestChildPath) {
        List<ReferencePathNode> shortestPath = new ArrayList<>(shortestChildPath);
        shortestPath.add(0, rootNode);

        List<ObjectReporter> leakReporters = shortestPath.stream()
                .map(pathNode -> new ObjectReporter(graph.findObjectById(pathNode.instance)))
                .collect(Collectors.toList());

        objectInspectors.forEach(inspector -> leakReporters.forEach(reporter -> inspector.inspect(graph, reporter)));

        List<LeakNodeStatusAndReason> leakStatuses = computeLeakStatuses(rootNode, leakReporters);

        List<LeakTraceElement> elements = IntStream.range(0, shortestPath.size())
                .mapToObj(index -> {
                    ReferencePathNode pathNode = shortestPath.get(index);
                    ObjectReporter leakReporter = leakReporters.get(index);
                    LeakNodeStatusAndReason leakStatus = leakStatuses.get(index);
                    LeakReference reference = index < shortestPath.size() - 1 ? shortestPath.get(index + 1).referenceFromParent : null;
                    return buildLeakElement(graph, pathNode, reference, leakReporter.labels, leakStatus);
                })
                .collect(Collectors.toList());

        return new LeakTrace(elements);
    }

    private List<LeakNodeStatusAndReason> computeLeakStatuses(RootNode rootNode, List<ObjectReporter> leakReporters) {
        int lastElementIndex = leakReporters.size() - 1;

        ObjectReporter rootNodeReporter = leakReporters.get(0);

        rootNodeReporter.addLabel("GC Root: " + switch (rootNode.gcRoot) {
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
            default -> throw new IllegalStateException("Unexpected gc root " + rootNode.gcRoot);
        });

        int lastNotLeakingElementIndex = 0;
        int firstLeakingElementIndex = lastElementIndex;

        List<LeakNodeStatusAndReason> leakStatuses = new ArrayList<>();

        for (int index = 0; index < leakReporters.size(); index++) {
            ObjectReporter reporter = leakReporters.get(index);
            LeakNodeStatusAndReason leakStatus = resolveStatus(reporter);
            leakStatuses.add(leakStatus);
            if (leakStatus.status == NOT_LEAKING) {
                lastNotLeakingElementIndex = index;
                firstLeakingElementIndex = lastElementIndex;
            } else if (firstLeakingElementIndex == lastElementIndex && leakStatus.status == LEAKING) {
                firstLeakingElementIndex = index;
            }
        }

        List<String> simpleClassNames = leakReporters.stream()
                .map(reporter -> recordClassName(reporter.heapObject).substring(reporter.heapObject.lastIndexOf('.') + 1))
                .collect(Collectors.toList());

        for (int i = 0; i <= lastElementIndex; i++) {
            LeakNodeStatusAndReason leakStatus = leakStatuses.get(i);
            if (i < lastNotLeakingElementIndex) {
                String nextNotLeakingName = simpleClassNames.get(i + 1);
                leakStatuses.set(i, switch (leakStatus.status) {
                    case UNKNOWN -> LeakNodeStatus.notLeaking(nextNotLeakingName + "↓ is not leaking");
                    case NOT_LEAKING -> LeakNodeStatus.notLeaking(nextNotLeakingName + "↓ is not leaking and " + leakStatus.reason);
                    case LEAKING -> LeakNodeStatus.notLeaking(nextNotLeakingName + "↓ is not leaking. Conflicts with " + leakStatus.reason);
                });
            } else if (i > firstLeakingElementIndex) {
                String previousLeakingName = simpleClassNames.get(i - 1);
                leakStatuses.set(i, switch (leakStatus.status) {
                    case UNKNOWN -> LeakNodeStatus.leaking(previousLeakingName + "↑ is leaking");
                    case LEAKING -> LeakNodeStatus.leaking(previousLeakingName + "↑ is leaking and " + leakStatus.reason);
                    case NOT_LEAKING -> throw new IllegalStateException("Should never happen");
                });
            }
        }
        return leakStatuses;
    }

    private LeakNodeStatusAndReason resolveStatus(ObjectReporter reporter) {
        LeakNodeStatusAndReason current = LeakNodeStatus.unknown();
        for (LeakNodeStatusAndReason statusAndReason : reporter.leakNodeStatuses) {
            current = switch (current.status) {
                case UNKNOWN -> statusAndReason;
                case LEAKING -> LeakNodeStatus.leaking(current.reason + " and " + statusAndReason.reason);
                case NOT_LEAKING -> LeakNodeStatus.notLeaking(current.reason + " and " + statusAndReason.reason);
                case NOT_LEAKING -> LeakNodeStatus.notLeaking(current.reason + ". Conflicts with " + statusAndReason.reason);
                case LEAKING -> LeakNodeStatus.notLeaking(statusAndReason.reason + ". Conflicts with " + current.reason);
                default -> throw new IllegalStateException("Should never happen " + current.status + " " + statusAndReason.reason);
            };
        }
        return current;
    }

    private LeakTraceElement buildLeakElement(HeapGraph graph, ReferencePathNode node, LeakReference reference, List<String> labels, LeakNodeStatusAndReason leakStatus) {
        long objectId = node.instance;

        HeapObject graphRecord = graph.findObjectById(objectId);

        String className = recordClassName(graphRecord);

        Holder holderType;
        if (graphRecord instanceof HeapClass) {
            holderType = Holder.CLASS;
        } else if (graphRecord instanceof HeapInstance) {
            holderType = Holder.OBJECT;
        } else if (graphRecord instanceof HeapObjectArray || graphRecord instanceof HeapPrimitiveArray) {
            holderType = Holder.ARRAY;
        } else {
            HeapInstance instanceRecord = (HeapInstance) graphRecord;
            if (instanceRecord.instanceClass.classHierarchy.stream().anyMatch(it -> it.name.equals(Thread.class.getName()))) {
                holderType = Holder.THREAD;
            } else {
                holderType = Holder.OBJECT;
            }
        }
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
    } else {
        throw new IllegalArgumentException("Unexpected heap object type");
    }
}

    private long since(long analysisStartNanoTime) {
        return NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime);
    }
}