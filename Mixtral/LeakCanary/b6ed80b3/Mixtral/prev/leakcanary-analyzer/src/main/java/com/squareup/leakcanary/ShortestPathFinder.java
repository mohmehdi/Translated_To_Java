

package com.squareup.leakcanary;

import com.squareup.haha.perflib.ArrayInstance;
import com.squareup.haha.perflib.ClassInstance;
import com.squareup.haha.perflib.ClassObj;
import com.squareup.haha.perflib.Instance;
import com.squareup.haha.perflib.RootObj;
import com.squareup.haha.perflib.RootType;
import com.squareup.haha.perflib.Snapshot;
import com.squareup.haha.perflib.Type;
import com.squareup.haha.perflib.allocatingThread;
import com.squareup.leakcanary.HahaHelper.IsPrimitiveOrWrapperArray;
import com.squareup.leakcanary.HahaHelper.IsPrimitiveWrapper;
import com.squareup.leakcanary.LeakTraceElement.Type.ARRAY_ENTRY;
import com.squareup.leakcanary.LeakTraceElement.Type.INSTANCE_FIELD;
import com.squareup.leakcanary.LeakTraceElement.Type.LOCAL;
import com.squareup.leakcanary.LeakTraceElement.Type.STATIC_FIELD;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public class ShortestPathFinder {
  private final Deque<LeakNode> toVisitQueue;
  private final Deque<LeakNode> toVisitIfNoPathQueue;
  private final LinkedHashSet<Instance> toVisitSet;
  private final LinkedHashSet<Instance> toVisitIfNoPathSet;
  private final LinkedHashSet<Instance> visitedSet;
  private boolean canIgnoreStrings;

  public ShortestPathFinder(ExcludedRefs excludedRefs) {
    toVisitQueue = new ArrayDeque<>();
    toVisitIfNoPathQueue = new ArrayDeque<>();
    toVisitSet = new LinkedHashSet<>();
    toVisitIfNoPathSet = new LinkedHashSet<>();
    visitedSet = new LinkedHashSet<>();
    canIgnoreStrings = !IsPrimitiveOrWrapperArray.INSTANCE.apply(excludedRefs.leakingRef);

    enqueueGcRoots(excludedRefs, snapshot);
  }

  public static class Result {
    public final LeakNode leakingNode;
    public final boolean excludingKnownLeaks;

    public Result(LeakNode leakingNode, boolean excludingKnownLeaks) {
      this.leakingNode = leakingNode;
      this.excludingKnownLeaks = excludingKnownLeaks;
    }
  }

  public Result findPath(Snapshot snapshot, Instance leakingRef) {
    clearState();
    canIgnoreStrings = !isString(leakingRef);

    enqueueGcRoots(snapshot, excludedRefs);

    boolean excludingKnownLeaks = false;
    LeakNode leakingNode = null;
    while (!toVisitQueue.isEmpty() || !toVisitIfNoPathQueue.isEmpty()) {
      LeakNode node;
      if (!toVisitQueue.isEmpty()) {
        node = toVisitQueue.poll();
      } else {
        node = toVisitIfNoPathQueue.poll();
        if (node.exclusion == null) {
          throw new IllegalStateException("Expected node to have an exclusion " + node);
        }
        excludingKnownLeaks = true;
      }

      if (node.instance == leakingRef) {
        leakingNode = node;
        break;
      }

      if (checkSeen(node)) {
        continue;
      }

      switch (node.instance.getType()) {
        case ROOT_OBJ:
          visitRootObj(node);
          break;
        case CLASS_OBJ:
          visitClassObj(node);
          break;
        case CLASS_INSTANCE:
          visitClassInstance(node);
          break;
        case ARRAY_INSTANCE:
          visitArrayInstance(node);
          break;
        default:
          throw new IllegalStateException("Unexpected type for " + node.instance);
      }
    }
    return new Result(leakingNode, excludingKnownLeaks);
  }

  private void clearState() {
    toVisitQueue.clear();
    toVisitIfNoPathQueue.clear();
    toVisitSet.clear();
    toVisitIfNoPathSet.clear();
    visitedSet.clear();
  }

  public void enqueueGcRoots(Snapshot snapshot) {
    for (GcRoot rootObj : snapshot.gcRoots) {
        RootType rootType = rootObj.getRootType();
        if (rootType == RootType.JAVA_LOCAL) {
            Thread thread = rootObj.allocatingThread();
            String threadName = HahaHelper.threadName(thread);
            Map<String, ExcludedRefsParams> excludedRefs = HahaHelper.getExcludedRefs();
            ExcludedRefsParams params = excludedRefs.get(threadName);
            if (params == null || !params.isAlwaysExclude()) {
                enqueue(params, null, rootObj, null);
            }
        } else if (rootType == RootType.THREAD_OBJECT
                || rootType == RootType.INTERNED_STRING
                || rootType == RootType.DEBUGGER
                || rootType == RootType.INVALID_TYPE
                || rootType == RootType.UNREACHABLE
                || rootType == RootType.UNKNOWN
                || rootType == RootType.FINALIZING
                || rootType == RootType.SYSTEM_CLASS
                || rootType == RootType.VM_INTERNAL
                || rootType == RootType.NATIVE_LOCAL
                || rootType == RootType.NATIVE_STATIC
                || rootType == RootType.THREAD_BLOCK
                || rootType == RootType.BUSY_MONITOR
                || rootType == RootType.NATIVE_MONITOR
                || rootType == RootType.REFERENCE_CLEANUP
                || rootType == RootType.NATIVE_STACK
                || rootType == RootType.JAVA_STATIC) {
            enqueue(null, null, rootObj, null);
        } else {
            throw new UnsupportedOperationException("Unknown root type:" + rootType);
        }
    }
  }

  private boolean checkSeen(LeakNode node) {
    return visitedSet.add(node.instance);
  }

  private void visitRootObj(LeakNode node) {
    RootObj rootObj = (RootObj) node.instance;
    Instance child = rootObj.getReferredInstance();

    if (rootObj.getRootType() == RootType.JAVA_LOCAL) {
      Thread holder = rootObj.allocatingThread();

      Exclusion exclusion = null;
      if (node.exclusion != null) {
        exclusion = node.exclusion;
      }
      LeakNode parent = new LeakNode(exclusion, holder, null, null);
      enqueue(exclusion, parent, child, new LeakReference(LOCAL, null, null));
    } else {
      enqueue(null, node, child, null);
    }
  }

  private void visitClassObj(LeakNode node) {
    ClassObj classObj = (ClassObj) node.instance;
    LinkedHashMap<String, Exclusion> ignoredStaticFields = excludedRefs.staticFieldNameByClassName.get(classObj.getClassName());
    for (Map.Entry<Field, Instance> entry : classObj.staticFieldValues.entrySet()) {
      Field field = entry.getKey();
      if (field.getType() != Type.OBJECT) {
        continue;
      }
      Instance child = entry.getValue();
      boolean visit = true;
      String fieldValue = child != null ? child.toString() : "null";
      LeakReference leakReference = new LeakReference(STATIC_FIELD, field.getName(), fieldValue);
      if (ignoredStaticFields != null) {
        ExclusionParams params = ignoredStaticFields.get(field.getName());
        if (params != null) {
          visit = false;
          if (!params.alwaysExclude) {
            enqueue(params, node, child, leakReference);
          }
        }
      }
      if (visit) {
        enqueue(null, node, child, leakReference);
      }
    }
  }

  private void visitClassInstance(LeakNode node) {
    ClassInstance classInstance = (ClassInstance) node.instance;
    LinkedHashMap<String, Exclusion> ignoredFields = new LinkedHashMap<>();
    ClassObj superClassObj = classInstance.getClassObj();
    Exclusion classExclusion = null;
    while (superClassObj != null) {
      ExclusionParams params = excludedRefs.classNames.get(superClassObj.getClassName());
      if (params != null) {

        if (classExclusion == null || !classExclusion.alwaysExclude) {
          classExclusion = new Exclusion(params.className, params.alwaysExclude);
        }
      }
      LinkedHashMap<String, Exclusion> classIgnoredFields = excludedRefs.fieldNameByClassName.get(superClassObj.getClassName());
      if (classIgnoredFields != null) {
        ignoredFields.putAll(classIgnoredFields);
      }
      superClassObj = superClassObj.getSuperClassObj();
    }

    if (classExclusion != null && classExclusion.alwaysExclude) {
      return;
    }

    for (FieldValue fieldValue : classInstance.values) {
      Exclusion fieldExclusion = classExclusion;
      Field field = fieldValue.field;
      if (field.getType() != Type.OBJECT) {
        continue;
      }
      Instance child = fieldValue.value;
      String fieldName = field.getName();
      ExclusionParams params = ignoredFields.get(fieldName);

      if (params != null && (fieldExclusion == null || params.alwaysExclude && !fieldExclusion.alwaysExclude)) {
        fieldExclusion = params;
      }
      String value = child != null ? child.toString() : "null";
      enqueue(fieldExclusion, node, child, new LeakReference(INSTANCE_FIELD, fieldName, value));
    }
  }

  private void visitArrayInstance(LeakNode node) {
    ArrayInstance arrayInstance = (ArrayInstance) node.instance;
    Type arrayType = arrayInstance.getArrayType();
    if (arrayType == Type.OBJECT) {
      Instance[] values = arrayInstance.values;
      for (int i = 0; i < values.length; i++) {
        Instance child = values[i];
        String name = Integer.toString(i);
        String value = child != null ? child.toString() : "null";
        enqueue(null, node, child, new LeakReference(ARRAY_ENTRY, name, value));
      }
    }
  }

  private void enqueue(
      Exclusion exclusion,
      LeakNode parent,
      Instance child,
      LeakReference leakReference) {
    if (child == null) {
      return;
    }
    if (IsPrimitiveOrWrapperArray.INSTANCE.apply(child) || IsPrimitiveWrapper.INSTANCE.apply(child)) {
      return;
    }

    if (toVisitSet.contains(child)) {
      return;
    }
    boolean visitNow = exclusion == null;
    if (!visitNow && toVisitIfNoPathSet.contains(child)) {
      return;
    }
    if (canIgnoreStrings && isString(child)) {
      return;
    }
    if (visitedSet.contains(child)) {
      return;
    }
    LeakNode childNode = new LeakNode(exclusion, child, parent, leakReference);
    if (visitNow) {
      toVisitSet.add(child);
      toVisitQueue.add(childNode);
    } else {
      toVisitIfNoPathSet.add(child);
      toVisitIfNoPathQueue.add(childNode);
    }
  }

  private boolean isString(Instance instance) {
    return instance.getClassObj() != null && instance.getClassObj().getClassName().equals(String.class.getName());
  }
}