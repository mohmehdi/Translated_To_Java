

package leakcanary;

import com.squareup.haha.perflib.ArrayInstance;
import com.squareup.haha.perflib.ClassInstance;
import com.squareup.haha.perflib.ClassObj;
import com.squareup.haha.perflib.Instance;
import com.squareup.haha.perflib.RootObj;
import com.squareup.haha.perflib.RootType;
import com.squareup.haha.perflib.Snapshot;
import com.squareup.haha.perflib.Type;
import com.squareup.haha.perflib.allocatingThread;
import leakcanary.HahaHelper;
import leakcanary.ExcludedRefs;
import leakcanary.LeakTraceElement.Type;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public class ShortestPathFinder {
    private final ExcludedRefs excludedRefs;
    private final Deque<LeakNode> toVisitQueue;
    private final Deque<LeakNode> toVisitIfNoPathQueue;
    private final LinkedHashSet<Instance> toVisitSet;
    private final LinkedHashSet<Instance> toVisitIfNoPathSet;
    private final LinkedHashSet<Instance> visitedSet;
    private boolean canIgnoreStrings;

    public ShortestPathFinder(ExcludedRefs excludedRefs) {
        this.excludedRefs = excludedRefs;
        this.toVisitQueue = new ArrayDeque<>();
        this.toVisitIfNoPathQueue = new ArrayDeque<>();
        this.toVisitSet = new LinkedHashSet<>();
        this.toVisitIfNoPathSet = new LinkedHashSet<>();
        this.visitedSet = new LinkedHashSet<>();
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
        canIgnoreStrings = !HahaHelper.isString(leakingRef);

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

            if (node.instance.equals(leakingRef)) {
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
                    Thread allocatingThread = rootObj.allocatingThread();
                    String threadName = HahaHelper.threadName(allocatingThread);
                    Exclusion.Params params = excludedRefs.threadNames.get(threadName);
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
                    throw new UnsupportedOperationException("Unknown root type:" + rootObj.rootType);
            }
        }
    }

    private boolean checkSeen(LeakNode node) {
        return visitedSet.add(node.instance);
    }

    private void visitRootObj(LeakNode node) {
        RootObj rootObj = (RootObj) node.instance;
        Instance child = rootObj.referredInstance;

        if (rootObj.rootType == RootType.JAVA_LOCAL) {
            Thread holder = rootObj.allocatingThread();

            Exclusion exclusion = null;
            if (node.exclusion != null) {
                exclusion = node.exclusion;
            }
            LeakNode parent = new LeakNode(null, holder, null, null);
            enqueue(exclusion, parent, child, new LeakReference(Type.LOCAL, null, null));
        } else {
            enqueue(null, node, child, null);
        }
    }

    private void visitClassObj(LeakNode node) {
        ClassObj classObj = (ClassObj) node.instance;
        LinkedHashMap<String, Exclusion> ignoredStaticFields = excludedRefs.staticFieldNameByClassName.get(classObj.className);
        for (Instance.FieldValue field : classObj.staticFieldValues) {
            if (field.type != Type.OBJECT) {
                continue;
            }
            String fieldName = field.field.name;
            if (fieldName.equals("\$staticOverhead")) {
                continue;
            }
            Instance child = field.value;
            boolean visit = true;
            String fieldValue = child != null ? child.toString() : "null";
            LeakReference leakReference = new LeakReference(Type.STATIC_FIELD, fieldName, fieldValue);
            if (ignoredStaticFields != null) {
                Exclusion.Params params = ignoredStaticFields.get(fieldName);
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
            Exclusion.Params params = excludedRefs.classNames.get(superClassObj.className);
            if (params != null) {
                if (classExclusion == null || !classExclusion.alwaysExclude) {
                    classExclusion = new Exclusion(params.className, params.reason, params.alwaysExclude);
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

        for (Instance.FieldValue fieldValue : classInstance.values) {
            Exclusion fieldExclusion = classExclusion;
            Instance.Field field = fieldValue.field;
            if (field.type != Type.OBJECT) {
                continue;
            }
            Instance child = fieldValue.value;
            String fieldName = field.name;
            Exclusion.Params params = ignoredFields.get(fieldName);
            if (params != null) {
                if (fieldExclusion == null || !fieldExclusion.alwaysExclude && params.alwaysExclude) {
                    fieldExclusion = new Exclusion(params.className, params.reason, params.alwaysExclude);
                }
            }
            String value = child != null ? child.toString() : "null";
            enqueue(fieldExclusion, node, child, new LeakReference(Type.INSTANCE_FIELD, fieldName, value));
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
                enqueue(null, node, child, new LeakReference(Type.ARRAY_ENTRY, name, value));
            }
        }
    }

    private void enqueue(Exclusion exclusion, LeakNode parent, Instance child, LeakReference leakReference) {
        if (child == null) {
            return;
        }
        if (HahaHelper.isPrimitiveOrWrapperArray(child) || HahaHelper.isPrimitiveWrapper(child)) {
            return;
        }

        if (toVisitSet.contains(child)) {
            return;
        }
        boolean visitNow = exclusion == null;
        if (!visitNow && toVisitIfNoPathSet.contains(child)) {
            return;
        }
        if (canIgnoreStrings && HahaHelper.isString(child)) {
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