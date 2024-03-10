
package shark.internal;

import shark.AnalyzerProgressListener;
import shark.AnalyzerProgressListener.Step;
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
import shark.ReferenceMatcher.IgnoredReferenceMatcher;
import shark.ReferenceMatcher.LibraryLeakReferenceMatcher;
import shark.ReferencePattern;
import shark.ReferencePattern.InstanceFieldPattern;
import shark.ReferencePattern.StaticFieldPattern;
import shark.SharkLog;
import shark.ValueHolder;
import shark.internal.ReferencePathNode;
import shark.internal.ReferencePathNode.ChildNode;
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
import java.util.Objects;

public class ShortestPathFinder {

  private final Deque < ReferencePathNode > toVisitQueue = new ArrayDeque < > ();
  private final Deque < LibraryLeakNode > toVisitLastQueue = new ArrayDeque < > ();

  private final Set < Long > toVisitSet = new HashSet < > ();
  private final Set < Long > toVisitLastSet = new HashSet < > ();

  private final LongScatterSet visitedSet = new LongScatterSet();
  private Set < Long > leakingInstanceObjectIds;

  private LongLongScatterMap dominatedInstances = new LongLongScatterMap();
  private int sizeOfObjectInstances = 0;

  public static class Results {
    public final List < ReferencePathNode > shortestPathsToLeakingInstances;
    public final LongLongScatterMap dominatedInstances;

    public Results(List < ReferencePathNode > shortestPathsToLeakingInstances, LongLongScatterMap dominatedInstances) {
      this.shortestPathsToLeakingInstances = shortestPathsToLeakingInstances;
      this.dominatedInstances = dominatedInstances;
    }
  }

  public Results findPaths(
    HeapGraph graph,
    List < ReferenceMatcher > referenceMatchers,
    Set < Long > leakingInstanceObjectIds,
    boolean computeDominators,
    AnalyzerProgressListener listener) {

    listener.onProgressUpdate(Step.FINDING_PATHS_TO_LEAKING_INSTANCES);
    clearState();
    this.leakingInstanceObjectIds = leakingInstanceObjectIds;

    HeapClass objectClass = graph.findClassByName("java.lang.Object");
    sizeOfObjectInstances = objectClass != null ?
      (objectClass.readFieldsSize() + PrimitiveType.INT.byteSize) *
      (objectClass.readFieldsSize() == graph.objectIdByteSize + PrimitiveType.INT.byteSize ? 1 : 0) :
      0;

    Map < String, Map < String, ReferenceMatcher >> fieldNameByClassName = new LinkedHashMap < > ();
    Map < String, Map < String, ReferenceMatcher >> staticFieldNameByClassName = new LinkedHashMap < > ();
    Map < String, ReferenceMatcher > threadNames = new LinkedHashMap < > ();

    for (ReferenceMatcher referenceMatcher: referenceMatchers) {
      if (referenceMatcher instanceof IgnoredReferenceMatcher ||
        (referenceMatcher instanceof LibraryLeakReferenceMatcher &&
          referenceMatcher.patternApplies(graph))) {
        ReferencePattern pattern = referenceMatcher.pattern;
        switch (pattern.getType()) {
        case JAVA_LOCAL:
          threadNames.put(pattern.getThreadName(), referenceMatcher);
          break;
        case STATIC_FIELD:
          StaticFieldPattern staticFieldPattern = (StaticFieldPattern) pattern;
          Map < String, ReferenceMatcher > map =
            staticFieldNameByClassName.computeIfAbsent(staticFieldPattern.getClassName(), k -> new LinkedHashMap < > ());
          map.put(staticFieldPattern.getFieldName(), referenceMatcher);
          break;
        case INSTANCE_FIELD:
          InstanceFieldPattern instanceFieldPattern = (InstanceFieldPattern) pattern;
          map = fieldNameByClassName.computeIfAbsent(instanceFieldPattern.getClassName(), k -> new LinkedHashMap < > ());
          map.put(instanceFieldPattern.getFieldName(), referenceMatcher);
          break;
        default:
          throw new AssertionError("Unexpected pattern type: " + pattern.getType());
        }
      }
    }

    enqueueGcRoots(graph, threadNames, computeDominators);

    List < ReferencePathNode > shortestPathsToLeakingInstances = new ArrayList < > ();
    while (!toVisitQueue.isEmpty() || !toVisitLastQueue.isEmpty()) {
      ReferencePathNode node;
      if (!toVisitQueue.isEmpty()) {
        node = toVisitQueue.poll();
        toVisitSet.remove(node.instance);
      } else {
        node = toVisitLastQueue.poll();
        toVisitLastSet.remove(node.instance);
      }

      if (checkSeen(node)) {
        throw new IllegalStateException(
          "Node " + node + " objectId=" + node.instance +
          " should not be enqueued when already visited or enqueued");
      }

      if (leakingInstanceObjectIds.contains(node.instance)) {
        shortestPathsToLeakingInstances.add(node);

        if (shortestPathsToLeakingInstances.size() == leakingInstanceObjectIds.size()) {
          if (computeDominators) {
            listener.onProgressUpdate(Step.FINDING_DOMINATORS);
          } else {
            break;
          }
        }
      }

      HeapObject graphRecord = graph.findObjectById(node.instance);
      if (graphRecord instanceof HeapClass) {
        visitClassRecord(
          graph, (HeapClass) graphRecord, node, staticFieldNameByClassName, computeDominators);
      } else if (graphRecord instanceof HeapInstance) {
        visitInstanceRecord(
          graph, (HeapInstance) graphRecord, node, fieldNameByClassName, computeDominators);
      } else if (graphRecord instanceof HeapObjectArray) {
        visitObjectArrayRecord(graph, ((HeapObjectArray) graphRecord).readRecord(), node,
          computeDominators);
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
    leakingInstanceObjectIds = new HashSet < > ();
    sizeOfObjectInstances = 0;
  }

  private void enqueueGcRoots(
    HeapGraph graph,
    Map < String, ReferenceMatcher > threadNameReferenceMatchers,
    boolean computeDominators) {
    List < Pair < HeapObject, GcRoot >> gcRoots = sortedGcRoots(graph);

    Map < Integer, Pair < HeapInstance, ThreadObject >> threadsBySerialNumber = new LinkedHashMap < > ();
    for (Pair < HeapObject, GcRoot > gcRoot: gcRoots) {
      if (computeDominators) {
        undominateWithSkips(graph, gcRoot.second.id);
      }
      GcRoot gcRootValue = gcRoot.second;
      switch (gcRootValue.getType()) {
      case THREAD_OBJECT:
        ThreadObject threadObject = (ThreadObject) gcRootValue;
        threadsBySerialNumber.put(threadObject.threadSerialNumber,
          new Pair < > ((HeapInstance) gcRoot.first, threadObject));
        enqueue(graph, new RootNode(gcRootValue, gcRootValue.id));
        break;
      case JAVA_FRAME:
        JavaFrame javaFrame = (JavaFrame) gcRootValue;
        Pair < HeapInstance, ThreadObject > threadInstance = threadsBySerialNumber.get(
          javaFrame.threadSerialNumber);
        Objects.requireNonNull(threadInstance);
        String threadName =
          ((HeapInstance) threadInstance.first).get(Thread.class, "name").value.readAsJavaString();
        ReferenceMatcher referenceMatcher = threadNameReferenceMatchers.get(threadName);
        if (referenceMatcher == null || referenceMatcher instanceof IgnoredReferenceMatcher) {
          RootNode rootNode = new RootNode(gcRootValue, threadInstance.second.id);
          ChildNode childNode;
          if (referenceMatcher instanceof LibraryLeakReferenceMatcher) {
            childNode = new LibraryLeakNode(gcRootValue.id, rootNode,
              new LeakReference(Type.LOCAL, ""),
              (LibraryLeakReferenceMatcher) referenceMatcher);
          } else {
            childNode = new NormalNode(gcRootValue.id, rootNode,
              new LeakReference(Type.LOCAL, ""));
          }
          enqueue(graph, childNode);
        }
        break;
      default:
        enqueue(graph, new RootNode(gcRootValue, gcRootValue.id));
        break;
      }
    }
  }

  private List < Pair < HeapObject, GcRoot >> sortedGcRoots(HeapGraph graph) {
    Function < HeapObject, String > rootClassName = heapObject -> {
      if (heapObject instanceof HeapClass) {
        return ((HeapClass) heapObject).name;
      } else if (heapObject instanceof HeapInstance) {
        return ((HeapInstance) heapObject).instanceClassName;
      } else if (heapObject instanceof HeapObjectArray) {
        return ((HeapObjectArray) heapObject).arrayClassName;
      } else if (heapObject instanceof HeapPrimitiveArray) {
        return ((HeapPrimitiveArray) heapObject).arrayClassName;
      } else {
        throw new AssertionError("Unexpected HeapObject type: " + heapObject.getClass().getName());
      }
    };

    return graph.gcRoots
      .stream()
      .map(id -> new Pair < > (graph.findObjectById(id), graph.getGcRoot(id)))
      .sorted((o1, o2) -> {
        int gcRootTypeComparison = o2.second.getClass().getName().compareTo(o1.second.getClass().getName());
        if (gcRootTypeComparison != 0) {
          return gcRootTypeComparison;
        } else {
          return rootClassName.apply(o1.first).compareTo(rootClassName.apply(o2.first));
        }
      })
      .toList();
  }

  private void visitClassRecord(
    HeapGraph graph,
    HeapClass heapClass,
    ReferencePathNode parent,
    Map < String, Map < String, ReferenceMatcher >> staticFieldNameByClassName,
    boolean computeRetainedHeapSize) {
    Map < String, ReferenceMatcher > ignoredStaticFields = staticFieldNameByClassName.getOrDefault(
      heapClass.name, new LinkedHashMap < > ());

    for (HeapField staticField: heapClass.readStaticFields()) {
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
        enqueue(
          graph,
          new NormalNode(objectId, parent,
            new LeakReference(Type.STATIC_FIELD, fieldName))
        );
      } else if (referenceMatcher instanceof LibraryLeakReferenceMatcher) {
        enqueue(
          graph,
          new LibraryLeakNode(
            objectId, parent,
            new LeakReference(Type.STATIC_FIELD, fieldName),
            (LibraryLeakReferenceMatcher) referenceMatcher
          )
        );
      }
    }
  }

  private void visitInstanceRecord(
    HeapGraph graph,
    HeapInstance instance,
    ReferencePathNode parent,
    Map < String, Map < String, ReferenceMatcher >> fieldNameByClassName,
    boolean computeRetainedHeapSize) {
    Map < String, ReferenceMatcher > fieldReferenceMatchers = new LinkedHashMap < > ();

    for (HeapClass clazz: instance.instanceClass.classHierarchy) {
      Map < String, ReferenceMatcher > referenceMatcherByField = fieldNameByClassName.get(clazz.name);
      if (referenceMatcherByField != null) {
        fieldReferenceMatchers.putAll(referenceMatcherByField);
      }
    }

    List < HeapField > fieldNamesAndValues = new ArrayList < > (instance.readFields());
    fieldNamesAndValues.sort(Comparator.comparing(HeapField::getName));

    for (HeapField field: fieldNamesAndValues) {
      if (!field.value.isNonNullReference) {
        continue;
      }

      long objectId = field.value.asObjectId;
      if (computeRetainedHeapSize) {
        updateDominatorWithSkips(graph, parent.instance, objectId);
      }

      ReferenceMatcher referenceMatcher = fieldReferenceMatchers.get(field.name);
      if (referenceMatcher == null) {
        enqueue(
          graph,
          new NormalNode(objectId, parent,
            new LeakReference(Type.INSTANCE_FIELD, field.name))
        );
      } else if (referenceMatcher instanceof LibraryLeakReferenceMatcher) {
        enqueue(
          graph,
          new LibraryLeakNode(
            objectId, parent,
            new LeakReference(Type.INSTANCE_FIELD, field.name),
            (LibraryLeakReferenceMatcher) referenceMatcher
          )
        );
      }
    }
  }

  private void visitObjectArrayRecord(
    HeapGraph graph,
    ObjectArrayDumpRecord record,
    ReferencePathNode parentNode,
    boolean computeRetainedHeapSize) {
    for (int i = 0; i < record.elementIds.length; i++) {
      long elementId = record.elementIds[i];
      if (elementId != ValueHolder.NULL_REFERENCE && graph.objectExists(elementId)) {
        if (computeRetainedHeapSize) {
          updateDominatorWithSkips(graph, parentNode.instance, elementId);
        }
        enqueue(
          graph,
          new NormalNode(elementId, parentNode,
            new LeakReference(Type.ARRAY_ENTRY, Integer.toString(i)))
        );
      }
    }
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
          HeapObject graphObject = graph.findObjectById(node.instance);
          if (graphObject instanceof HeapClass) {
              skip = false;
          } else if (graphObject instanceof HeapInstance) {
              HeapInstance heapInstance = (HeapInstance) graphObject;
              if (heapInstance.isPrimitiveWrapper) {
                  skip = true;
              } else if (heapInstance.instanceClassName.equals("java.lang.String")) {
                  skip = true;
              } else if (heapInstance.instanceClass.instanceSize <= sizeOfObjectInstances) {
                  skip = true;
              } else {
                  skip = false;
              }
          } else if (graphObject instanceof HeapObjectArray) {
              if (((HeapObjectArray) graphObject).isPrimitiveWrapperArray) {
                  skip = true;
              } else {
                  skip = false;
              }
          } else if (graphObject instanceof HeapPrimitiveArray) {
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

    HeapObject graphObject = graph.findObjectById(objectId);
    if (graphObject instanceof HeapClass) {
      undominate(objectId, false);
    } else if (graphObject instanceof HeapInstance) {
      if (((HeapInstance) graphObject).instanceClassName.equals("java.lang.String")) {
        updateDominator(parentObjectId, objectId, true);
        Long valueId = ((HeapInstance) graphObject).get("java.lang.String", "value").value.asObjectId;
        if (valueId != null) {
          updateDominator(parentObjectId, valueId, true);
        }
      } else {
        updateDominator(parentObjectId, objectId, false);
      }
    } else if (graphObject instanceof HeapObjectArray) {
      if (((HeapObjectArray) graphObject).isPrimitiveWrapperArray) {
        updateDominator(parentObjectId, objectId, true);
        for (long wrapperId: ((HeapObjectArray) graphObject).readRecord().elementIds) {
          updateDominator(parentObjectId, wrapperId, true);
        }
      } else {
        updateDominator(parentObjectId, objectId, false);
      }
    } else {
      updateDominator(parentObjectId, objectId, false);
    }
  }

  private void updateDominator(
    long parent,
    long instance,
    boolean neverEnqueued
  ) {
    Long currentDominator = dominatedInstances.get(instance);
    if (currentDominator == null && (visitedSet.contains(instance) || toVisitSet.contains(instance) || toVisitLastSet.contains(instance))) {
      return;
    }
    Long parentDominator = dominatedInstances.get(parent);

    boolean parentIsRetainedInstance = leakingInstanceObjectIds.contains(parent);

    Long nextDominator = parentIsRetainedInstance ? parent : parentDominator;

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
      List < Long > parentDominators = new ArrayList < > ();
      List < Long > currentDominators = new ArrayList < > ();
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
      for (Long parentD: parentDominators) {
        for (Long currentD: currentDominators) {
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

  private void undominateWithSkips(
    HeapGraph graph,
    long objectId
  ) {
    Object graphObject = graph.findObjectById(objectId);
    if (graphObject instanceof HeapClass) {
      undominate(objectId, false);
    } else if (graphObject instanceof HeapInstance) {
      HeapInstance heapInstance = (HeapInstance) graphObject;
      String instanceClassName = heapInstance.getInstanceClassName();
      if (instanceClassName.equals("java.lang.String")) {
        undominate(objectId, true);
        Object valueId = heapInstance.getValue("java.lang.String", "value");
        if (valueId != null) {
          undominate(((Number) valueId).longValue(), true);
        }
      } else {
        undominate(objectId, false);
      }
    } else if (graphObject instanceof HeapObjectArray) {
      HeapObjectArray heapObjectArray = (HeapObjectArray) graphObject;
      if (heapObjectArray.isPrimitiveWrapperArray()) {
        undominate(objectId, true);
        for (long wrapperId: heapObjectArray.readRecord().elementIds) {
          undominate(wrapperId, true);
        }
      } else {
        undominate(objectId, false);
      }
    } else {
      undominate(objectId, false);
    }
  }

  private void undominate(
    long instance,
    boolean neverEnqueued
  ) {
    dominatedInstances.remove(instance);
    if (neverEnqueued) {
      visitedSet.add(instance);
    }
  }
}