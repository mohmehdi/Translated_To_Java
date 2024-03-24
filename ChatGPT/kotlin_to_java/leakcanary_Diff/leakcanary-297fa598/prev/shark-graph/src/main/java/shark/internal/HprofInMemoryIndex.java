

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
import kotlin.reflect.KClass;

import java.util.List;
import java.util.Set;

public class HprofInMemoryIndex {
    private final int positionSize;
    private final LongObjectScatterMap<String> hprofStringCache;
    private final LongLongScatterMap classNames;
    private final SortedBytesMap classIndex;
    private final SortedBytesMap instanceIndex;
    private final SortedBytesMap objectArrayIndex;
    private final SortedBytesMap primitiveArrayIndex;
    private final List<GcRoot> gcRoots;
    private final ProguardMapping proguardMapping;
    private final Set<Long> primitiveWrapperTypes;

    public HprofInMemoryIndex(int positionSize, LongObjectScatterMap<String> hprofStringCache, LongLongScatterMap classNames, SortedBytesMap classIndex, SortedBytesMap instanceIndex, SortedBytesMap objectArrayIndex, SortedBytesMap primitiveArrayIndex, List<GcRoot> gcRoots, ProguardMapping proguardMapping, Set<Long> primitiveWrapperTypes) {
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
        return proguardMapping != null ? proguardMapping.deobfuscateFieldName(hprofStringById(classNames.get(classId)), fieldNameString) : fieldNameString;
    }

    public String className(long classId) {
        String classNameString = hprofStringById(classNames.get(classId));
        return proguardMapping != null ? proguardMapping.deobfuscateClassName(classNameString) : classNameString;
    }

    public Long classId(String className) {
        Long hprofStringId = hprofStringCache.entrySequence()
                .filter(entry -> entry.getValue().equals(className))
                .mapToLong(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        return hprofStringId != null ? classNames.entrySequence()
                .filter(entry -> entry.getValue() == hprofStringId)
                .mapToLong(Map.Entry::getKey)
                .findFirst()
                .orElse(null) : null;
    }

    public Sequence<Pair<Long, IndexedClass>> indexedClassSequence() {
        return classIndex.entrySequence()
                .map(entry -> {
                    long id = entry.getKey();
                    ByteSubArray array = entry.getValue();
                    return new Pair<>(id, new IndexedClass(array.readTruncatedLong(positionSize), array.readId(), array.readInt()));
                });
    }

    public Sequence<Pair<Long, IndexedInstance>> indexedInstanceSequence() {
        return instanceIndex.entrySequence()
                .map(entry -> {
                    long id = entry.getKey();
                    ByteSubArray array = entry.getValue();
                    IndexedInstance instance = new IndexedInstance(array.readTruncatedLong(positionSize), array.readId());
                    return new Pair<>(id, instance);
                });
    }

    public Sequence<Pair<Long, IndexedObjectArray>> indexedObjectArraySequence() {
        return objectArrayIndex.entrySequence()
                .map(entry -> {
                    long id = entry.getKey();
                    ByteSubArray array = entry.getValue();
                    IndexedObjectArray objectArray = new IndexedObjectArray(array.readTruncatedLong(positionSize), array.readId());
                    return new Pair<>(id, objectArray);
                });
    }

    public Sequence<Pair<Long, IndexedPrimitiveArray>> indexedPrimitiveArraySequence() {
        return primitiveArrayIndex.entrySequence()
                .map(entry -> {
                    long id = entry.getKey();
                    ByteSubArray array = entry.getValue();
                    IndexedPrimitiveArray primitiveArray = new IndexedPrimitiveArray(array.readTruncatedLong(positionSize), PrimitiveType.values()[array.readByte()]);
                    return new Pair<>(id, primitiveArray);
                });
    }

    public Sequence<Pair<Long, IndexedObject>> indexedObjectSequence() {
        return indexedClassSequence().plus(indexedInstanceSequence()).plus(indexedObjectArraySequence()).plus(indexedPrimitiveArraySequence());
    }

    public List<GcRoot> gcRoots() {
        return gcRoots;
    }

    public IndexedObject indexedObjectOrNull(long objectId) {
        ByteSubArray array = classIndex.get(objectId);
        if (array != null) {
            return new IndexedClass(array.readTruncatedLong(positionSize), array.readId(), array.readInt());
        }
        array = instanceIndex.get(objectId);
        if (array != null) {
            return new IndexedInstance(array.readTruncatedLong(positionSize), array.readId());
        }
        array = objectArrayIndex.get(objectId);
        if (array != null) {
            return new IndexedObjectArray(array.readTruncatedLong(positionSize), array.readId());
        }
        array = primitiveArrayIndex.get(objectId);
        if (array != null) {
            return new IndexedPrimitiveArray(array.readTruncatedLong(positionSize), PrimitiveType.values()[array.readByte()]);
        }
        return null;
    }

    public boolean objectIdIsIndexed(long objectId) {
        return classIndex.get(objectId) != null || instanceIndex.get(objectId) != null || objectArrayIndex.get(objectId) != null || primitiveArrayIndex.get(objectId) != null;
    }

    private String hprofStringById(long id) {
        return hprofStringCache.get(id);
    }

    private static class Builder implements OnHprofRecordListener {
        private final int identifierSize;
        private final int positionSize;
        private final LongObjectScatterMap<String> hprofStringCache;
        private final LongLongScatterMap classNames;
        private final UnsortedByteEntries classIndex;
        private final UnsortedByteEntries instanceIndex;
        private final UnsortedByteEntries objectArrayIndex;
        private final UnsortedByteEntries primitiveArrayIndex;
        private final Set<Class<? extends GcRoot>> indexedGcRootsTypes;

        public Builder(boolean longIdentifiers, long fileLength, int classCount, int instanceCount, int objectArrayCount, int primitiveArrayCount, Set<Class<? extends GcRoot>> indexedGcRootsTypes) {
            this.identifierSize = longIdentifiers ? 8 : 4;
            this.positionSize = byteSizeForUnsigned(fileLength);
            this.hprofStringCache = new LongObjectScatterMap<>();
            this.classNames = new LongLongScatterMap(classCount);
            this.classIndex = new UnsortedByteEntries(positionSize + identifierSize + 4, longIdentifiers, classCount);
            this.instanceIndex = new UnsortedByteEntries(positionSize + identifierSize, longIdentifiers, instanceCount);
            this.objectArrayIndex = new UnsortedByteEntries(positionSize + identifierSize, longIdentifiers, objectArrayCount);
            this.primitiveArrayIndex = new UnsortedByteEntries(positionSize + 1, longIdentifiers, primitiveArrayCount);
            this.indexedGcRootsTypes = indexedGcRootsTypes;
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
                classIndex.append(classSkipContentRecord.id)
                        .writeTruncatedLong(position, positionSize)
                        .writeId(classSkipContentRecord.superclassId)
                        .writeInt(classSkipContentRecord.instanceSize);
            } else if (record instanceof InstanceSkipContentRecord) {
                InstanceSkipContentRecord instanceSkipContentRecord = (InstanceSkipContentRecord) record;
                instanceIndex.append(instanceSkipContentRecord.id)
                        .writeTruncatedLong(position, positionSize)
                        .writeId(instanceSkipContentRecord.classId);
            } else if (record instanceof ObjectArraySkipContentRecord) {
                ObjectArraySkipContentRecord objectArraySkipContentRecord = (ObjectArraySkipContentRecord) record;
                objectArrayIndex.append(objectArraySkipContentRecord.id)
                        .writeTruncatedLong(position, positionSize)
                        .writeId(objectArraySkipContentRecord.arrayClassId);
            } else if (record instanceof PrimitiveArraySkipContentRecord) {
                PrimitiveArraySkipContentRecord primitiveArraySkipContentRecord = (PrimitiveArraySkipContentRecord) record;
                primitiveArrayIndex.append(primitiveArraySkipContentRecord.id)
                        .writeTruncatedLong(position, positionSize)
                        .writeByte((byte) primitiveArraySkipContentRecord.type.ordinal());
            }
        }

        public HprofInMemoryIndex buildIndex(ProguardMapping proguardMapping) {
            SortedBytesMap sortedInstanceIndex = instanceIndex.moveToSortedMap();
            SortedBytesMap sortedObjectArrayIndex = objectArrayIndex.moveToSortedMap();
            SortedBytesMap sortedPrimitiveArrayIndex = primitiveArrayIndex.moveToSortedMap();
            SortedBytesMap sortedClassIndex = classIndex.moveToSortedMap();

            return new HprofInMemoryIndex(positionSize, hprofStringCache, classNames, sortedClassIndex, sortedInstanceIndex, sortedObjectArrayIndex, sortedPrimitiveArrayIndex, gcRoots, proguardMapping, primitiveWrapperTypes);
        }
    }

    private static final Set<String> PRIMITIVE_WRAPPER_TYPES = Set.of(
            Boolean.class.getName(), Char.class.getName(), Float.class.getName(),
            Double.class.getName(), Byte.class.getName(), Short.class.getName(),
            Integer.class.getName(), Long.class.getName()
    );

    private static int byteSizeForUnsigned(long maxValue) {
        long value = maxValue;
        int byteCount = 0;
        while (value != 0L) {
            value = value >> 8;
            byteCount++;
        }
        return byteCount;
    }

    public static HprofInMemoryIndex createReadingHprof(Hprof hprof, ProguardMapping proguardMapping, Set<KClass<? extends GcRoot>> indexedGcRootTypes) {
        Set<Class<? extends HprofRecord>> recordTypes = Set.of(
                StringRecord.class,
                LoadClassRecord.class,
                ClassSkipContentRecord.class,
                InstanceSkipContentRecord.class,
                ObjectArraySkipContentRecord.class,
                PrimitiveArraySkipContentRecord.class,
                GcRootRecord.class
        );
        Hprof.Reader reader = hprof.reader;

        int classCount = 0;
        int instanceCount = 0;
        int objectArrayCount = 0;
        int primitiveArrayCount = 0;
        reader.readHprofRecords(Set.of(
                LoadClassRecord.class,
                InstanceSkipContentRecord.class,
                ObjectArraySkipContentRecord.class,
                PrimitiveArraySkipContentRecord.class
        ), (position, record) -> {
            if (record instanceof LoadClassRecord) {
                classCount++;
            } else if (record instanceof InstanceSkipContentRecord) {
                instanceCount++;
            } else if (record instanceof ObjectArraySkipContentRecord) {
                objectArrayCount++;
            } else if (record instanceof PrimitiveArraySkipContentRecord) {
                primitiveArrayCount++;
            }
        });

        hprof.moveReaderTo(reader.startPosition);
        Builder indexBuilderListener = new Builder(reader.identifierByteSize == 8, hprof.fileLength, classCount, instanceCount, objectArrayCount, primitiveArrayCount, indexedGcRootTypes.stream().map(KClass::getJavaClass).collect(Collectors.toSet()));

        reader.readHprofRecords(recordTypes, indexBuilderListener);

        return indexBuilderListener.buildIndex(proguardMapping);
    }
}