

package shark;

import shark.AnalyzerProgressListener.Step;
import shark.AnalyzerProgressListener.Step.BUILDING_LEAK_TRACES;
import shark.AnalyzerProgressListener.Step.COMPUTING_NATIVE_RETAINED_SIZE;
import shark.AnalyzerProgressListener.Step.COMPUTING_RETAINED_SIZE;
import shark.AnalyzerProgressListener.Step.FINDING_LEAKING_INSTANCES;
import shark.AnalyzerProgressListener.Step.PARSING_HEAP_DUMP;
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
import shark.Leak.LeakNodeStatus.LEAKING;
import shark.Leak.LeakNodeStatus.NOT_LEAKING;
import shark.Leak.LeakNodeStatus.UNKNOWN;
import shark.LeakTraceElement.Holder.ARRAY;
import shark.LeakTraceElement.Holder.CLASS;
import shark.LeakTraceElement.Holder.OBJECT;
import shark.LeakTraceElement.Holder.THREAD;
import shark.internal.ReferencePathNode;
import shark.internal.ReferencePathNode.ChildNode;
import shark.internal.ReferencePathNode.ChildNode.LibraryLeakNode;
import shark.internal.ReferencePathNode.RootNode;
import shark.internal.ShortestPathFinder;
import shark.internal.ShortestPathFinder.Results;
import shark.internal.hppc.LongLongScatterMap;
import shark.internal.lastSegment;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit.NANOSECONDS;

class HeapAnalyzer {

  private final AnalyzerProgressListener listener;

  public HeapAnalyzer(AnalyzerProgressListener listener) {
    this.listener = listener;
  }

  public HeapAnalysis checkForLeaks(
    File heapDumpFile,
    List<ReferenceMatcher> referenceMatchers,
    boolean computeRetainedHeapSize,
    List<ObjectInspector> objectInspectors,
    List<ObjectInspector> leakFinders
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
      listener.onProgressUpdate(PARSING_HEAP_DUMP);
      Hprof.open(heapDumpFile).use { hprof ->
        HeapGraph graph = HeapGraph.indexHprof(hprof);
        listener.onProgressUpdate(FINDING_LEAKING_INSTANCES);

        Set<Long> leakingInstanceObjectIds = findLeakingInstances(graph, leakFinders);

        Results shortestPathsToLeakingInstances = findShortestPaths(
            graph, referenceMatchers, leakingInstanceObjectIds, computeRetainedHeapSize
        );

        List<Integer> retainedSizes = null;
        if (computeRetainedHeapSize) {
          retainedSizes = computeRetainedSizes(graph, shortestPathsToLeakingInstances, dominatedInstances);
        }

        List<ApplicationLeak> applicationLeaks = new ArrayList<>();
        List<LibraryLeak> libraryLeaks = new ArrayList<>();

        buildLeakTraces(
            objectInspectors, shortestPathsToLeakingInstances, graph, retainedSizes,
            applicationLeaks, libraryLeaks
        );

        return new HeapAnalysisSuccess(
            heapDumpFile, System.currentTimeMillis(), since(analysisStartNanoTime),
            applicationLeaks, libraryLeaks
        );
      }
    } catch (Throwable exception) {
      return new HeapAnalysisFailure(
          heapDumpFile, System.currentTimeMillis(), since(analysisStartNanoTime),
          new HeapAnalysisException(exception)
      );
    }
  }

  private Set<Long> findLeakingInstances(
    HeapGraph graph,
    List<ObjectInspector> objectInspectors
  ) {
    return graph.objects
        .filter(objectRecord -> {
          ObjectReporter reporter = new ObjectReporter(objectRecord);
          for (inspector : objectInspectors) {
            inspector.inspect(graph, reporter);
          }
          return !reporter.leakingStatuses.isEmpty();
        })
        .map(HeapObjectRecord::getObjectId)
        .toSet();
  }

  private Results findShortestPaths(
    HeapGraph graph,
    List<ReferenceMatcher> referenceMatchers,
    Set<Long> leakingInstanceObjectIds,
    boolean computeDominators
  ) {
    ShortestPathFinder pathFinder = new ShortestPathFinder();
    return pathFinder.findPaths(
        graph, referenceMatchers, leakingInstanceObjectIds, computeDominators, listener
    );
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
    TrieNode rootTrieNode = new TrieNode.ParentNode(0);

    for (ReferencePathNode pathNode : inputPathResults) {
      List<Long> path = new ArrayList<>();
      ReferencePathNode leakNode = pathNode;
      while (leakNode instanceof ChildNode) {
        path.add(0, leakNode.getInstance());
        leakNode = leakNode.getParent();
      }
      path.add(0, leakNode.getInstance());
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

  private List<Integer> computeRetainedSizes(
    HeapGraph graph,
    List<ReferencePathNode> results,
    LongLongScatterMap dominatedInstances
  ) {
    listener.onProgressUpdate(COMPUTING_NATIVE_RETAINED_SIZE);

    Map<Long, Integer> nativeSizes = new HashMap<>();
    nativeSizes.put(0L, 0);

    graph.instances
        .filter(heapObjectRecord -> heapObjectRecord.getInstanceClassName().equals("sun.misc.Cleaner"))
        .forEach(cleaner -> {
          HeapObject thunkField = cleaner.getField("sun.misc.Cleaner", "thunk");
          long thunkId = thunkField != null ? thunkField.getObjectId() : 0;
          long referentId =
            cleaner.getField("java.lang.ref.Reference", "referent") != null ? cleaner.getField("java.lang.ref.Reference", "referent").getValue().asNonNullObjectId() : 0;
          if (thunkId != 0 && referentId != 0) {
            HeapInstance thunkRecord = (HeapInstance) thunkField.asObject;
            if (thunkRecord.instanceOf("libcore.util.NativeAllocationRegistry$CleanerThunk")) {
              HeapInstance allocationRegistryIdField = (HeapInstance) thunkRecord.getField("libcore.util.NativeAllocationRegistry$CleanerThunk", "this$0");
              if (allocationRegistryIdField != null && allocationRegistryIdField.isNonNullReference()) {
                HeapInstance allocationRegistryRecord = (HeapInstance) allocationRegistryIdField.asObject;
                if (allocationRegistryRecord.instanceOf("libcore.util.NativeAllocationRegistry")) {
                  int nativeSize = nativeSizes.get(referentId);
                  nativeSize += allocationRegistryRecord.getField("libcore.util.NativeAllocationRegistry", "size").getValue().asLong.intValue();
                  nativeSizes.put(referentId, nativeSize);
                }
              }
            }
          }
        });

    listener.onProgressUpdate(COMPUTING_RETAINED_SIZE);

    Map<Long, Integer> sizeByDominator = new LinkedHashMap<>();
    sizeByDominator.put(0L, 0);

    Set<Long> leakingInstanceIds = new HashSet<>();
    for (ReferencePathNode pathNode : results) {
      long leakingInstanceObjectId = pathNode.getInstance();
      leakingInstanceIds.add(leakingInstanceObjectId);
      HeapInstance instanceRecord = (HeapInstance) graph.findObjectById(leakingInstanceObjectId).asInstance;
      HeapClass heapClass = instanceRecord.getInstanceClass();
      int retainedSize = sizeByDominator.get(leakingInstanceObjectId);

      retainedSize += heapClass.getInstanceSize();
      sizeByDominator.put(leakingInstanceObjectId, retainedSize);
    }

    dominatedInstances.forEach((instanceId, dominatorId) -> {
      if (!leakingInstanceIds.contains(instanceId)) {
        int currentSize = sizeByDominator.get(dominatorId);
        int nativeSize = nativeSizes.get(instanceId);
        int shallowSize = 0;
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
      for (long leakingInstanceId : leakingInstanceIds) {
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
      }
    } while (sizedMoved);
    dominatedInstances.release();
    return results.stream().map(pathNode -> sizeByDominator.get(pathNode.getInstance())).toList();
  }

  private List<LibraryLeak> buildLeakTraces(
    List<ObjectInspector> objectInspectors,
    List<ReferencePathNode> shortestPathsToLeakingInstances,
    HeapGraph graph,
    List<Integer> retainedSizes
  ) {
    listener.onProgressUpdate(BUILDING_LEAK_TRACES);

    List<ApplicationLeak> applicationLeaks = new ArrayList<>();
    List<LibraryLeak> libraryLeaks = new ArrayList<>();

    List<ReferencePathNode> deduplicatedPaths = deduplicateShortestPaths(shortestPathsToLeakingInstances);

    for (int i = 0; i < deduplicatedPaths.size(); i++) {
      ReferencePathNode pathNode = deduplicatedPaths.get(i);
      List<ChildNode> shortestChildPath = new ArrayList<>();

      ReferencePathNode node = pathNode;
      while (node instanceof ChildNode) {
        shortestChildPath.add(0, (ChildNode) node);
        node = node.getParent();
      }
      RootNode rootNode = (RootNode) node;

      LeakTrace leakTrace =
        buildLeakTrace(graph, objectInspectors, rootNode, shortestChildPath);

      String className =
        recordClassName(graph.findObjectById(pathNode.getInstance()));

      LibraryLeakNode firstLibraryLeakNode =
        shortestChildPath.stream().filter(childNode -> childNode instanceof LibraryLeakNode).map(childNode -> (LibraryLeakNode) childNode).findFirst().orElse(null);

      if (firstLibraryLeakNode != null) {
        ReferenceMatcher matcher = firstLibraryLeakNode.getMatcher();
        libraryLeaks.add(new LibraryLeak(
            className, leakTrace, retainedSizes.get(i), matcher.getPattern(), matcher.getDescription()
        ));
      } else {
        applicationLeaks.add(new ApplicationLeak(className, leakTrace, retainedSizes.get(i)));
      }
    }
    return applicationLeaks;
  }

  private LeakTrace buildLeakTrace(
    HeapGraph graph,
    List<ObjectInspector> objectInspectors,
    RootNode rootNode,
    List<ChildNode> shortestChildPath
  ) {
    List<ChildNode> shortestPath = new ArrayList<>(shortestChildPath);
    shortestPath.add(0, rootNode);

    List<ObjectReporter> leakReporters = shortestPath.stream().map(pathNode -> new ObjectReporter(graph.findObjectById(pathNode.getInstance()))).toList();

    objectInspectors.forEach(inspector -> {
      leakReporters.forEach(reporter -> {
        inspector.inspect(graph, reporter);
      });
    });

    List<LeakNodeStatusAndReason> leakStatuses = computeLeakStatuses(rootNode, leakReporters);

    List<LeakTraceElement> elements = shortestPath.stream().map((pathNode, index) -> {
      ObjectReporter leakReporter = leakReporters.get(index);
      LeakNodeStatusAndReason leakStatus = leakStatuses.get(index);
      LeakReference reference =
        index < shortestPath.size() - 1 ? (LeakReference) shortestPath.get(index + 1).getReferenceFromParent() : null;
      return buildLeakElement(graph, pathNode, reference, leakReporter.getLabels(), leakStatus);
    }).toList();
    return new LeakTrace(elements);
  }

  private List<LeakNodeStatusAndReason> computeLeakStatuses(
    RootNode rootNode,
    List<ObjectReporter> leakReporters
  ) {
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
        }
    );

    int lastNotLeakingElementIndex = 0;
    int firstLeakingElementIndex = lastElementIndex;

    List<LeakNodeStatusAndReason> leakStatuses = new ArrayList<>();

    for (int i = 0; i < leakReporters.size(); i++) {
      LeakNodeStatusAndReason leakStatus = resolveStatus(leakReporters.get(i));
      leakStatuses.add(leakStatus);
      if (leakStatus.getStatus() == NOT_LEAKING) {
        lastNotLeakingElementIndex = i;

        firstLeakingElementIndex = lastElementIndex;
      } else if (firstLeakingElementIndex == lastElementIndex && leakStatus.getStatus() == LEAKING) {
        firstLeakingElementIndex = i;
      }
    }

    List<String> simpleClassNames = leakReporters.stream().map(reporter -> recordClassName(reporter.getHeapObject()).lastSegment('.')).toList();

    for (int i = 0; i <= lastElementIndex; i++) {
      LeakNodeStatusAndReason leakStatus = leakStatuses.get(i);
      if (i < lastNotLeakingElementIndex) {
        String nextNotLeakingName = simpleClassNames.get(i + 1);
        leakStatuses.set(i, when (leakStatus.getStatus()) {
          UNKNOWN -> new LeakNodeStatus.NotLeaking("$nextNotLeakingName↓ is not leaking");
          NOT_LEAKING -> new LeakNodeStatus.NotLeaking("$nextNotLeakingName↓ is not leaking and ${leakStatus.reason}");
          LEAKING -> new LeakNodeStatus.NotLeaking("$nextNotLeakingName↓ is not leaking. Conflicts with ${leakStatus.reason}");
        });
      } else if (i > firstLeakingElementIndex) {
        String previousLeakingName = simpleClassNames.get(i - 1);
        leakStatuses.set(i, when (leakStatus.getStatus()) {
          UNKNOWN -> new LeakNodeStatus.Leaking("$previousLeakingName↑ is leaking");
          LEAKING -> new LeakNodeStatus.Leaking("$previousLeakingName↑ is leaking and ${leakStatus.reason}");
          NOT_LEAKING -> throw new IllegalStateException("Should never happen");
        });
      }
    }
    return leakStatuses;
  }

  private LeakNodeStatusAndReason resolveStatus(
    ObjectReporter reporter
  ) {
    LeakNodeStatusAndReason current = LeakNodeStatus.unknown();
    for (LeakNodeStatusAndReason statusAndReason : reporter.getLeakNodeStatuses()) {
      current = when {
        current.getStatus() == UNKNOWN -> statusAndReason;
        current.getStatus() == LEAKING && statusAndReason.getStatus() == LEAKING -> {
          new LeakNodeStatus.Leaking("${current.reason} and ${statusAndReason.reason}");
        }
        current.getStatus() == NOT_LEAKING && statusAndReason.getStatus() == NOT_LEAKING -> {
          new LeakNodeStatus.NotLeaking("${current.reason} and ${statusAndReason.reason}");
        }
        current.getStatus() == NOT_LEAKING && statusAndReason.getStatus() == LEAKING -> {
          new LeakNodeStatus.NotLeaking(
              "${current.reason}. Conflicts with ${statusAndReason.reason}"
          );
        }
        current.getStatus() == LEAKING && statusAndReason.getStatus() == NOT_LEAKING -> {
          new LeakNodeStatus.NotLeaking(
              "${statusAndReason.reason}. Conflicts with ${current.reason}"
          );
        }
        else -> throw new IllegalStateException(
            "Should never happen ${current.getStatus()} ${statusAndReason.reason}"
        );
      }
    }
    return current;
  }

  private LeakTraceElement buildLeakElement(
    HeapGraph graph,
    ReferencePathNode node,
    LeakReference reference,
    List<String> labels,
    LeakNodeStatusAndReason leakStatus
  ) {
    long objectId = node.getInstance();

    HeapObject heap = graph.findObjectById(objectId);

    String className = recordClassName(heap);

    Holder holderType = null;
    if (heap instanceof HeapClass) {
      holderType = CLASS;
    } else if (heap instanceof HeapObjectArray || heap instanceof HeapPrimitiveArray) {
      holderType = ARRAY;
    } else {
      HeapInstance instanceRecord = (HeapInstance) heap.asInstance;
      if (instanceRecord.getInstanceClass().classHierarchy.any(clazz -> clazz.name.equals(Thread.class.getName()))) {
        holderType = THREAD;
      } else {
        holderType = OBJECT;
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
      }
      return null;
  }

  private long since(long analysisStartNanoTime) {
    return NANOSECONDS.toMillis(System.nanoTime() - analysisStartNanoTime);
  }
}