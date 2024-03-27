
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
import com.squareup.leakcanary.HahaHelper;
import com.squareup.leakcanary.LeakTraceElement;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

class ShortestPathFinder {

    private Deque<LeakNode> toVisitQueue;
    private Deque<LeakNode> toVisitIfNoPathQueue;
    private LinkedHashSet<Instance> toVisitSet;
    private LinkedHashSet<Instance> toVisitIfNoPathSet;
    private LinkedHashSet<Instance> visitedSet;
    private boolean canIgnoreStrings = false;

    ShortestPathFinder(ExcludedRefs excludedRefs) {
        toVisitQueue = new ArrayDeque<>();
        toVisitIfNoPathQueue = new ArrayDeque<>();
        toVisitSet = new LinkedHashSet<>();
        toVisitIfNoPathSet = new LinkedHashSet<>();
        visitedSet = new LinkedHashSet<>();
    }

    static class Result {
        final LeakNode leakingNode;
        final boolean excludingKnownLeaks;

        Result(LeakNode leakingNode, boolean excludingKnownLeaks) {
            this.leakingNode = leakingNode;
            this.excludingKnownLeaks = excludingKnownLeaks;
        }
    }

    Result findPath(Snapshot snapshot, Instance leakingRef) {
        clearState();
        canIgnoreStrings = !isString(leakingRef);

        enqueueGcRoots(snapshot);

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

            if (node.instance instanceof RootObj) {
                visitRootObj(node);
            } else if (node.instance instanceof ClassObj) {
                visitClassObj(node);
            } else if (node.instance instanceof ClassInstance) {
                visitClassInstance(node);
            } else if (node.instance instanceof ArrayInstance) {
                visitArrayInstance(node);
            } else {
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

    private void enqueueGcRoots(Snapshot snapshot) {
        for (RootObj rootObj : snapshot.gcRoots) {
            switch (rootObj.rootType) {
                case JAVA_LOCAL:
                    Thread thread = rootObj.allocatingThread();
                    String threadName = HahaHelper.threadName(thread);
                    Exclusion params = excludedRefs.threadNames.get(threadName);
                    if (params == null || !params.alwaysExclude) {
                        enqueue(params, null, rootObj, null);
                    }
                    break;
                case THREAD_OBJECT:
                case INTERNED_STRING:
                case DEBUGGER:
                case INVALID_TYPE:
                case UNREACHABLE:
                case UNKNOWN:
                case FINALIZING:
                    break;
                case SYSTEM_CLASS:
                case VM_INTERNAL:
                case NATIVE_LOCAL:
                case NATIVE_STATIC:
                case THREAD_BLOCK:
                case BUSY_MONITOR:
                case NATIVE_MONITOR:
                case REFERENCE_CLEANUP:
                case NATIVE_STACK:
                case JAVA_STATIC:
                    enqueue(null, null, rootObj, null);
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown root type: " + rootObj.rootType);
            }
        }
    }

    private boolean checkSeen(LeakNode node) {
        return !visitedSet.add(node.instance);
    }

    private void visitRootObj(LeakNode node) {
        RootObj rootObj = (RootObj) node.instance;
        Instance child = rootObj.referredInstance;

        if (rootObj.rootType == RootType.JAVA_LOCAL) {
            Thread holder = rootObj.allocatingThread();
            Exclusion exclusion = node.exclusion;
            LeakNode parent = new LeakNode(null, holder, null, null);
            enqueue(exclusion, parent, child, new LeakReference(LeakTraceElement.Type.LOCAL, null, null));
        } else {
            enqueue(null, node, child, null);
        }
    }

    private void visitClassObj(LeakNode node) {
        ClassObj classObj = (ClassObj) node.instance;
        LinkedHashMap<String, Exclusion> ignoredStaticFields = excludedRefs.staticFieldNameByClassName.get(classObj.className);
        for (Map.Entry<Field, Object> entry : classObj.staticFieldValues.entrySet()) {
            Field field = entry.getKey();
            Object value = entry.getValue();
            if (field.type != Type.OBJECT) {
                continue;
            }
            String fieldName = field.name;
            if (fieldName.equals("$staticOverhead")) {
                continue;
            }
            Instance child = (Instance) value;
            boolean visit = true;
            String fieldValue = child != null ? child.toString() : "null";
            LeakReference leakReference = new LeakReference(LeakTraceElement.Type.STATIC_FIELD, fieldName, fieldValue);
            if (ignoredStaticFields != null) {
                Exclusion params = ignoredStaticFields.get(fieldName);
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
        ClassObj superClassObj = classInstance.classObj;
        Exclusion classExclusion = null;
        while (superClassObj != null) {
            Exclusion params = excludedRefs.classNames.get(superClassObj.className);
            if (params != null) {
                if (classExclusion == null || !classExclusion.alwaysExclude) {
                    classExclusion = params;
                }
            }
            LinkedHashMap<String, Exclusion> classIgnoredFields = excludedRefs.fieldNameByClassName.get(superClassObj.className);
            if (classIgnoredFields != null) {
                ignoredFields.putAll(classIgnoredFields);
            }
            superClassObj = superClassObj.superClassObj;
        }

        if (classExclusion != null && classExclusion.alwaysExclude) {
            return;
        }

        for (FieldValue fieldValue : classInstance.values) {
            Exclusion fieldExclusion = classExclusion;
            Field field = fieldValue.field;
            if (field.type != Type.OBJECT) {
                continue;
            }
            Instance child = (Instance) fieldValue.value;
            String fieldName = field.name;
            Exclusion params = ignoredFields.get(fieldName);
            if (params != null && (fieldExclusion == null || (params.alwaysExclude && !fieldExclusion.alwaysExclude))) {
                fieldExclusion = params;
            }
            String value = fieldValue.value == null ? "null" : fieldValue.value.toString();
            enqueue(fieldExclusion, node, child, new LeakReference(LeakTraceElement.Type.INSTANCE_FIELD, fieldName, value));
        }
    }

    private void visitArrayInstance(LeakNode node) {
        ArrayInstance arrayInstance = (ArrayInstance) node.instance;
        Type arrayType = arrayInstance.arrayType;
        if (arrayType == Type.OBJECT) {
            Instance[] values = arrayInstance.values;
            for (int i = 0; i < values.length; i++) {
                Instance child = values[i];
                String name = Integer.toString(i);
                String value = child != null ? child.toString() : "null";
                enqueue(null, node, child, new LeakReference(LeakTraceElement.Type.ARRAY_ENTRY, name, value));
            }
        }
    }

    private void enqueue(Exclusion exclusion, LeakNode parent, Instance child, LeakReference leakReference) {
        if (child == null) {
            return;
        }
        if (isPrimitiveOrWrapperArray(child) || isPrimitiveWrapper(child)) {
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
        return instance.classObj != null && instance.classObj.className.equals(String.class.getName());
    }
}