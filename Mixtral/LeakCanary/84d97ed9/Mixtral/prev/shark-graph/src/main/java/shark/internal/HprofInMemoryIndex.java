

package shark.internal;

import shark.GcRoot;
import shark.GcRoot.JavaFrame;
import shark.GcRoot.JniGlobal;
import shark.GcRoot.JniLocal;
import shark.GcRoot.JniMonitor;
import shark.GcRoot.MonitorUsed;
import shark.GcRoot.NativeStack;
import shark.GcRoot.StickyClass;
import shark.GcRoot.ThreadBlock;
import shark.GcRoot.ThreadObject;
import shark.HprofReader;
import shark.HprofRecord;
import shark.HprofRecord.HeapDumpRecord.GcRootRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.InstanceDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ObjectArrayDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.BooleanArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.ByteArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.CharArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.DoubleArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.FloatArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.IntArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.LongArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.ShortArrayDump;
import shark.HprofRecord.LoadClassRecord;
import shark.HprofRecord.StringRecord;
import shark.OnHprofRecordListener;
import shark.PrimitiveType;
import shark.internal.IndexedObject.IndexedClass;
import shark.internal.IndexedObject.IndexedInstance;
import shark.internal.IndexedObject.IndexedObjectArray;
import shark.internal.IndexedObject.IndexedPrimitiveArray;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class HprofInMemoryIndex {

    private final Map<Long, String> hprofStringCache;
    private final Map<Long, Long> classNames;
    private final Map<Long, IndexedObject> objectIndex;
    private final List<GcRoot> gcRoots;
    private final Set<Long> primitiveWrapperTypes;

    private HprofInMemoryIndex(Map<Long, String> hprofStringCache,
                               Map<Long, Long> classNames,
                               Map<Long, IndexedObject> objectIndex,
                               List<GcRoot> gcRoots,
                               Set<Long> primitiveWrapperTypes) {
        this.hprofStringCache = hprofStringCache;
        this.classNames = classNames;
        this.objectIndex = objectIndex;
        this.gcRoots = gcRoots;
        this.primitiveWrapperTypes = primitiveWrapperTypes;
    }

    public String hprofStringById(long id) {
        String string = hprofStringCache.get(id);
        if (string == null) {
            throw new IllegalArgumentException("Hprof string " + id + " not in cache");
        }
        return string;
    }

    public String className(long classId) {
        return hprofStringById(classNames.get(classId));
    }

    public Long classId(String className) {
        Long stringId = hprofStringCache.get(className);
        if (stringId == null) {
            return null;
        }
        return classNames.get(stringId);
    }

    public Stream<Map.Entry<Long, IndexedClass>> indexedClassSequence() {
        return objectIndex.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof IndexedClass)
                .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), (IndexedClass) entry.getValue()));
    }

    public Stream<Map.Entry<Long, IndexedInstance>> indexedInstanceSequence() {
        return objectIndex.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof IndexedInstance)
                .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), (IndexedInstance) entry.getValue()));
    }

    public Stream<Map.Entry<Long, IndexedObject>> indexedObjectSequence() {
        return objectIndex.entrySet().stream();
    }

    public List<GcRoot> gcRoots() {
        return gcRoots;
    }

    public IndexedObject indexedObject(long objectId) {
        IndexedObject indexedObject = objectIndex.get(objectId);
        if (indexedObject == null) {
            throw new IllegalArgumentException("Object id " + objectId + " not found in heap dump.");
        }
        return indexedObject;
    }

    public boolean objectIdIsIndexed(long objectId) {
        return objectIndex.containsKey(objectId);
    }

    public static class Builder implements OnHprofRecordListener {

        private final Set<Class<? extends GcRoot>> indexedGcRootsTypes;
        private final Map<Long, String> hprofStringCache;
        private final Map<Long, Long> classNames;
        private final Map<Long, IndexedObject> objectIndex;
        private final Set<Long> primitiveWrapperTypes;

        public Builder(Set<Class<? extends GcRoot>> indexedGcRootsTypes) {
            this.indexedGcRootsTypes = indexedGcRootsTypes;
            this.hprofStringCache = new LinkedHashMap<>(60000);
            this.classNames = new HashMap<>(20000);
            this.objectIndex = new HashMap<>(250000);
            this.primitiveWrapperTypes = new LinkedHashSet<>();
        }

        @Override
        public void onHprofRecord(long position, HprofRecord record) {
            if (record instanceof StringRecord) {
                StringRecord stringRecord = (StringRecord) record;
                if (PRIMITIVE_WRAPPER_TYPES.contains(stringRecord.string)) {
                    primitiveWrapperTypes.add(stringRecord.id);
                }
                hprofStringCache.put(stringRecord.id, stringRecord.string.replace('/', '.'));
            } else if (record instanceof LoadClassRecord) {
                LoadClassRecord loadClassRecord = (LoadClassRecord) record;
                classNames.put(loadClassRecord.id, loadClassRecord.classNameStringId);
                if (primitiveWrapperTypes.contains(loadClassRecord.classNameStringId)) {
                    primitiveWrapperTypes.add(loadClassRecord.id);
                }
            } else if (record instanceof GcRootRecord) {
                GcRootRecord gcRootRecord = (GcRootRecord) record;
                GcRoot gcRoot = gcRootRecord.gcRoot;
                if (gcRoot.id != 0 && indexedGcRootsTypes.contains(gcRoot.getClass())) {
                    gcRoots.add(gcRoot);
                }
            } else if (record instanceof ClassDumpRecord) {
                ClassDumpRecord classDumpRecord = (ClassDumpRecord) record;
                objectIndex.put(classDumpRecord.id, new IndexedClass(position, classDumpRecord.superClassId, classDumpRecord.instanceSize));
            } else if (record instanceof InstanceDumpRecord) {
                InstanceDumpRecord instanceDumpRecord = (InstanceDumpRecord) record;
                objectIndex.put(instanceDumpRecord.id, new IndexedInstance(position, instanceDumpRecord.classId));
            } else if (record instanceof ObjectArrayDumpRecord) {
                ObjectArrayDumpRecord objectArrayDumpRecord = (ObjectArrayDumpRecord) record;
                objectIndex.put(objectArrayDumpRecord.id, new IndexedObjectArray(position, objectArrayDumpRecord.arrayClassId));
            } else if (record instanceof PrimitiveArrayDumpRecord) {
                PrimitiveArrayDumpRecord primitiveArrayDumpRecord = (PrimitiveArrayDumpRecord) record;
                PrimitiveType primitiveType;
                if (primitiveArrayDumpRecord instanceof BooleanArrayDump) {
                    primitiveType = PrimitiveType.BOOLEAN;
                } else if (primitiveArrayDumpRecord instanceof ByteArrayDump) {
                    primitiveType = PrimitiveType.BYTE;
                } else if (primitiveArrayDumpRecord instanceof CharArrayDump) {
                    primitiveType = PrimitiveType.CHAR;
                } else if (primitiveArrayDumpRecord instanceof DoubleArrayDump) {
                    primitiveType = PrimitiveType.DOUBLE;
                } else if (primitiveArrayDumpRecord instanceof FloatArrayDump) {
                    primitiveType = PrimitiveType.FLOAT;
                } else if (primitiveArrayDumpRecord instanceof IntArrayDump) {
                    primitiveType = PrimitiveType.INT;
                } else if (primitiveArrayDumpRecord instanceof LongArrayDump) {
                    primitiveType = PrimitiveType.LONG;
                } else if (primitiveArrayDumpRecord instanceof ShortArrayDump) {
                    primitiveType = PrimitiveType.SHORT;
                } else {
                    throw new IllegalStateException("Unsupported primitive array type: " + primitiveArrayDumpRecord.getClass());
                }
                objectIndex.put(primitiveArrayDumpRecord.id, new IndexedPrimitiveArray(position, primitiveType));
            }
        }

        public HprofInMemoryIndex buildIndex() {
            return new HprofInMemoryIndex(hprofStringCache, classNames, objectIndex, gcRoots, primitiveWrapperTypes);
        }
    }

    public static HprofInMemoryIndex createReadingHprof(HprofReader reader,
                                                        Set<Class<GcRoot>> indexedGcRootTypes) {
        Set<Class<? extends HprofRecord>> recordTypes = Set.of(
                StringRecord.class,
                LoadClassRecord.class,
                ClassDumpRecord.class,
                InstanceDumpRecord.class,
                ObjectArrayDumpRecord.class,
                PrimitiveArrayDumpRecord.class,
                GcRootRecord.class
        );
        Builder indexBuilderListener = new Builder(indexedGcRootTypes);
        reader.readHprofRecords(recordTypes, indexBuilderListener);
        return indexBuilderListener.buildIndex();
    }

    private static final Set<String> PRIMITIVE_WRAPPER_TYPES;

    static {
        PRIMITIVE_WRAPPER_TYPES = Set.of(
                Boolean.class.getName(),
                Character.class.getName(),
                Float.class.getName(),
                Double.class.getName(),
                Byte.class.getName(),
                Short.class.getName(),
                Integer.class.getName(),
                Long.class.getName()
        );
    }
}