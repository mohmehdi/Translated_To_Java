

package shark.internal;

import shark.GcRoot;
import shark.Hprof;
import shark.HprofRecord;
import shark.HprofRecord.HeapDumpRecord.GcRootRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassSkipContentRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.InstanceSkipContentRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ObjectArraySkipContentRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArraySkipContentRecord;
import shark.HprofRecord.LoadClassRecord;
import shark.HprofRecord.StringRecord;
import shark.OnHprofRecordListener;
import shark.PrimitiveType;
import shark.ProguardMapping;
import shark.ValueHolder;
import shark.internal.IndexedObject.IndexedClass;
import shark.internal.IndexedObject.IndexedInstance;
import shark.internal.IndexedObject.IndexedObjectArray;
import shark.internal.IndexedObject.IndexedPrimitiveArray;
import shark.internal.hppc.LongLongScatterMap;
import shark.internal.hppc.LongObjectScatterMap;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class HprofInMemoryIndex {

    private final int positionSize;
    private final LongObjectScatterMap<String> hprofStringCache;
    private final LongLongScatterMap classNames;
    private final SortedMap<Long, byte[]> classIndex;
    private final SortedMap<Long, byte[]> instanceIndex;
    private final SortedMap<Long, byte[]> objectArrayIndex;
    private final SortedMap<Long, byte[]> primitiveArrayIndex;
    private final List<GcRoot> gcRoots;
    private final ProguardMapping proguardMapping;
    private final Set<Long> primitiveWrapperTypes;

    public HprofInMemoryIndex(int positionSize, LongObjectScatterMap<String> hprofStringCache,
                              LongLongScatterMap classNames, SortedMap<Long, byte[]> classIndex,
                              SortedMap<Long, byte[]> instanceIndex,
                              SortedMap<Long, byte[]> objectArrayIndex,
                              SortedMap<Long, byte[]> primitiveArrayIndex, List<GcRoot> gcRoots,
                              ProguardMapping proguardMapping, Set<Long> primitiveWrapperTypes) {
        this.positionSize = positionSize;
        this.hprofStringCache = hprofStringCache;
        this.classNames = classNames;
        this.classIndex = classIndex;
        this.instanceIndex = instanceIndex;
        this.objectArrayIndex = objectArrayIndex;
        this.primitiveArrayIndex = primitiveArrayIndex;
        this.gcRoots = gcRoots;
        this.proguardMapping = proguardMapping;
        this.primitiveWrapperTypes = primitiveWrapperTypes;
    }

    public String fieldName(long classId, long id) {
        String fieldNameString = hprofStringById(id);
        if (proguardMapping != null) {
            long classNameStringId = classNames.get(classId);
            String classNameString = hprofStringById(classNameStringId);
            fieldNameString = proguardMapping.deobfuscateFieldName(classNameString, fieldNameString);
        }
        return fieldNameString;
    }

    public String className(long classId) {
        long classNameStringId = classNames.get(classId);
        String classNameString = hprofStringById(classNameStringId);
        return proguardMapping != null ? proguardMapping.deobfuscateClassName(classNameString) : classNameString;
    }

    public Long classId(String className) {
        Long hprofStringId = hprofStringCache.entrySet().stream()
                .filter(entry -> entry.getValue().equals(className))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        return hprofStringId != null ? classNames.get(hprofStringId) : null;
    }

    public Sequence<Pair<Long, IndexedClass>> indexedClassSequence() {
        return classIndex.entrySet().stream()
                .map(entry -> Pair.create(entry.getKey(), new IndexedClass(
                        entry.getValue(), positionSize, entry.getValue().length)));
    }

    public Sequence<Pair<Long, IndexedInstance>> indexedInstanceSequence() {
        return instanceIndex.entrySet().stream()
                .map(entry -> Pair.create(entry.getKey(), new IndexedInstance(
                        entry.getValue(), positionSize)));
    }

    public Sequence<Pair<Long, IndexedObjectArray>> indexedObjectArraySequence() {
        return objectArrayIndex.entrySet().stream()
                .map(entry -> Pair.create(entry.getKey(), new IndexedObjectArray(
                        entry.getValue(), positionSize, entry.getValue().length)));
    }

    public Sequence<Pair<Long, IndexedPrimitiveArray>> indexedPrimitiveArraySequence() {
        return primitiveArrayIndex.entrySet().stream()
                .map(entry -> Pair.create(entry.getKey(), new IndexedPrimitiveArray(
                        entry.getValue(), positionSize, PrimitiveType.values()[Byte.toUnsignedInt(entry.getValue()[entry.getValue().length - 1])])));
    }

    public Sequence<Pair<Long, IndexedObject>> indexedObjectSequence() {
        return Stream.concat(indexedClassSequence(), Stream.concat(indexedInstanceSequence(), Stream.concat(indexedObjectArraySequence(), indexedPrimitiveArraySequence())))
                .collect(Sequence.sequencesCollector());
    }

    public List<GcRoot> gcRoots() {
        return gcRoots;
    }

    public IndexedObject indexedObjectOrNull(long objectId) {
        byte[] array = classIndex.get(objectId);
        if (array != null) {
            return new IndexedClass(array, positionSize, array.length);
        }
        array = instanceIndex.get(objectId);
        if (array != null) {
            return new IndexedInstance(array, positionSize);
        }
        array = objectArrayIndex.get(objectId);
        if (array != null) {
            return new IndexedObjectArray(array, positionSize, array.length);
        }
        array = primitiveArrayIndex.get(objectId);
        if (array != null) {
            return new IndexedPrimitiveArray(array, positionSize, PrimitiveType.values()[Byte.toUnsignedInt(array[array.length - 1])]);
        }
        return null;
    }

    public boolean objectIdIsIndexed(long objectId) {
        return classIndex.containsKey(objectId)
                || instanceIndex.containsKey(objectId)
                || objectArrayIndex.containsKey(objectId)
                || primitiveArrayIndex.containsKey(objectId);
    }

    private String hprofStringById(long id) {
        return hprofStringCache.get(id).orElseThrow(() -> new IllegalArgumentException("Hprof string " + id + " not in cache"));
    }

    public static class Builder implements OnHprofRecordListener {

        private final int identifierSize;
        private final int positionSize;
        private final LongObjectScatterMap<String> hprofStringCache;
        private final LongLongScatterMap classNames;
        private final SortedMap<Long, byte[]> classIndex;
        private final SortedMap<Long, byte[]> instanceIndex;
        private final SortedMap<Long, byte[]> objectArrayIndex;
        private final SortedMap<Long, byte[]> primitiveArrayIndex;
        private final Set<Long> primitiveWrapperTypes;
        private final Set<Long> primitiveWrapperClassNames;
        private final List<GcRoot> gcRoots;

        public Builder(boolean longIdentifiers, long fileLength, int classCount, int instanceCount,
                        int objectArrayCount, int primitiveArrayCount,
                        Set<Class<? extends GcRoot>> indexedGcRootsTypes) {
            this.identifierSize = longIdentifiers ? 8 : 4;
            this.positionSize = byteSizeForUnsigned(fileLength);
            this.hprofStringCache = new LongObjectScatterMap<>();
            this.classNames = new LongLongScatterMap(classCount);
            this.classIndex = new TreeMap<>();
            this.instanceIndex = new TreeMap<>();
            this.objectArrayIndex = new TreeMap<>();
            this.primitiveArrayIndex = new TreeMap<>();
            this.primitiveWrapperTypes = new HashSet<>();
            this.primitiveWrapperClassNames = new HashSet<>();
            this.gcRoots = new ArrayList<>();
        }

        @Override
        public void onHprofRecord(long position, HprofRecord record) {
            if (record instanceof StringRecord) {
                StringRecord stringRecord = (StringRecord) record;
                if (PRIMITIVE_WRAPPER_TYPES.contains(stringRecord.string)) {
                    primitiveWrapperClassNames.add(stringRecord.id);
                }
                hprofStringCache.put(stringRecord.id, stringRecord.string.replace('/', '.'));
            } else if (record instanceof LoadClassRecord) {
                LoadClassRecord loadClassRecord = (LoadClassRecord) record;
                classNames.put(loadClassRecord.id, loadClassRecord.classNameStringId);
                if (primitiveWrapperClassNames.contains(loadClassRecord.classNameStringId)) {
                    primitiveWrapperTypes.add(loadClassRecord.id);
                }
            } else if (record instanceof GcRootRecord) {
                GcRootRecord gcRootRecord = (GcRootRecord) record;
                GcRoot gcRoot = gcRootRecord.gcRoot;
                if (gcRoot.id != ValueHolder.NULL_REFERENCE && indexedGcRootsTypes.contains(gcRoot.getClass())) {
                    gcRoots.add(gcRoot);
                }
            } else if (record instanceof ClassSkipContentRecord) {
                ClassSkipContentRecord classSkipContentRecord = (ClassSkipContentRecord) record;
                byte[] entry = new byte[positionSize + identifierSize + 4];
                writeTruncatedLong(entry, 0, position, positionSize);
                writeId(entry, positionSize, classSkipContentRecord.superclassId);
                writeInt(entry, positionSize + identifierSize, classSkipContentRecord.instanceSize);
                classIndex.put(classSkipContentRecord.id, entry);
            } else if (record instanceof InstanceSkipContentRecord) {
                InstanceSkipContentRecord instanceSkipContentRecord = (InstanceSkipContentRecord) record;
                byte[] entry = new byte[positionSize + identifierSize];
                writeTruncatedLong(entry, 0, position, positionSize);
                writeId(entry, positionSize, instanceSkipContentRecord.classId);
                instanceIndex.put(instanceSkipContentRecord.id, entry);
            } else if (record instanceof ObjectArraySkipContentRecord) {
                ObjectArraySkipContentRecord objectArraySkipContentRecord = (ObjectArraySkipContentRecord) record;
                byte[] entry = new byte[positionSize + identifierSize];
                writeTruncatedLong(entry, 0, position, positionSize);
                writeId(entry, positionSize, objectArraySkipContentRecord.arrayClassId);
                objectArrayIndex.put(objectArraySkipContentRecord.id, entry);
            } else if (record instanceof PrimitiveArraySkipContentRecord) {
                PrimitiveArraySkipContentRecord primitiveArraySkipContentRecord = (PrimitiveArraySkipContentRecord) record;
                byte[] entry = new byte[positionSize + 1];
                writeTruncatedLong(entry, 0, position, positionSize);
                entry[entry.length - 1] = (byte) primitiveArraySkipContentRecord.type.ordinal();
                primitiveArrayIndex.put(primitiveArraySkipContentRecord.id, entry);
            }
        }

        public HprofInMemoryIndex buildIndex(ProguardMapping proguardMapping) {
            SortedMap<Long, byte[]> sortedInstanceIndex = new TreeMap<>(instanceIndex);
            SortedMap<Long, byte[]> sortedObjectArrayIndex = new TreeMap<>(objectArrayIndex);
            SortedMap<Long, byte[]> sortedPrimitiveArrayIndex = new TreeMap<>(primitiveArrayIndex);
            SortedMap<Long, byte[]> sortedClassIndex = new TreeMap<>(classIndex);

            return new HprofInMemoryIndex(positionSize, hprofStringCache, classNames, sortedClassIndex, sortedInstanceIndex,
                    sortedObjectArrayIndex, sortedPrimitiveArrayIndex, gcRoots, proguardMapping, primitiveWrapperTypes);
        }


    }

    public static HprofInMemoryIndex createReadingHprof(Hprof hprof, ProguardMapping proguardMapping,
                                                        Set<Class<? extends GcRoot>> indexedGcRootTypes) {
        Function<Class<? extends HprofRecord>, BiFunction<Long, HprofRecord, Void>> recordTypes = recordClass -> (position, record) -> {
            if (record instanceof LoadClassRecord) {
                LoadClassRecord loadClassRecord = (LoadClassRecord) record;
                classNames.put(loadClassRecord.id, loadClassRecord.classNameStringId);
            }
            return null;
        };

        BiFunction<Long, HprofRecord, Void> onHprofRecordListener = (position, record) -> {
            if (record instanceof StringRecord) {
                StringRecord stringRecord = (StringRecord) record;
                if (PRIMITIVE_WRAPPER_TYPES.contains(stringRecord.string)) {
                    primitiveWrapperClassNames.add(stringRecord.id);
                }
                hprofStringCache.put(stringRecord.id, stringRecord.string.replace('/', '.'));
            } else if (record instanceof LoadClassRecord) {
                LoadClassRecord loadClassRecord = (LoadClassRecord) record;
                classNames.put(loadClassRecord.id, loadClassRecord.classNameStringId);
                if (primitiveWrapperClassNames.contains(loadClassRecord.classNameStringId)) {
                    primitiveWrapperTypes.add(loadClassRecord.id);
                }
            } else if (record instanceof GcRootRecord) {
                GcRootRecord gcRootRecord = (GcRootRecord) record;
                GcRoot gcRoot = gcRootRecord.gcRoot;
                if (gcRoot.id != ValueHolder.NULL_REFERENCE && indexedGcRootTypes.contains(gcRoot.getClass())) {
                    gcRoots.add(gcRoot);
                }
            }
            return null;
        };

        hprof.reader.readHprofRecords(recordTypes, onHprofRecordListener);

        Builder indexBuilderListener = new Builder(hprof.reader.identifierByteSize == 8, hprof.fileLength, classNames.size(),
                hprof.instanceCount, hprof.objectArrayCount, hprof.primitiveArrayCount, indexedGcRootTypes);

        hprof.reader.readHprofRecords(recordTypes.andThen(indexBuilderListener), indexBuilderListener);

        return indexBuilderListener.buildIndex(proguardMapping);
    }

    private static int byteSizeForUnsigned(long maxValue) {
        int byteCount = 0;
        long value = maxValue;
        while (value != 0) {
            value = value >> 8;
            byteCount++;
        }
        return byteCount;
    }

    private static final Set<String> PRIMITIVE_WRAPPER_TYPES = Set.of(
            Boolean.class.getName(), Character.class.getName(), Float.class.getName(),
            Double.class.getName(), Byte.class.getName(), Short.class.getName(),
            Integer.class.getName(), Long.class.getName()
    );
}