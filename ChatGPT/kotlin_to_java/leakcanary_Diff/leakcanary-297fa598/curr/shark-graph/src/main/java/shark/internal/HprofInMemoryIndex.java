
package shark.internal;

import shark.GcRoot;
import shark.HprofHeader;
import shark.HprofRecord;
import shark.HprofRecord.HeapDumpRecord.GcRootRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassSkipContentRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.InstanceSkipContentRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ObjectArraySkipContentRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArraySkipContentRecord;
import shark.HprofRecord.LoadClassRecord;
import shark.HprofRecord.StringRecord;
import shark.HprofVersion;
import shark.StreamingHprofReader;
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

import java.util.List;
import java.util.Set;

import static java.lang.Math.max;

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
    private final int bytesForClassSize;
    private final int bytesForInstanceSize;
    private final int bytesForObjectArraySize;
    private final int bytesForPrimitiveArraySize;
    private final boolean useForwardSlashClassPackageSeparator;

    public HprofInMemoryIndex(int positionSize, LongObjectScatterMap<String> hprofStringCache, LongLongScatterMap classNames,
                              SortedBytesMap classIndex, SortedBytesMap instanceIndex, SortedBytesMap objectArrayIndex,
                              SortedBytesMap primitiveArrayIndex, List<GcRoot> gcRoots, ProguardMapping proguardMapping,
                              Set<Long> primitiveWrapperTypes, int bytesForClassSize, int bytesForInstanceSize,
                              int bytesForObjectArraySize, int bytesForPrimitiveArraySize, boolean useForwardSlashClassPackageSeparator) {
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
        this.bytesForClassSize = bytesForClassSize;
        this.bytesForInstanceSize = bytesForInstanceSize;
        this.bytesForObjectArraySize = bytesForObjectArraySize;
        this.bytesForPrimitiveArraySize = bytesForPrimitiveArraySize;
        this.useForwardSlashClassPackageSeparator = useForwardSlashClassPackageSeparator;
    }

    public String fieldName(long classId, long id) {
        String fieldNameString = hprofStringById(id);
        return proguardMapping != null ? proguardMapping.deobfuscateFieldName(hprofStringById(classNames.get(classId)), fieldNameString) : fieldNameString;
    }

    public String className(long classId) {
        String classNameString = hprofStringById(classNames.get(classId));
        return (proguardMapping != null ? proguardMapping.deobfuscateClassName(classNameString) : classNameString).replace('/', '.');
    }

    public Long classId(String className) {
        String internalClassName = useForwardSlashClassPackageSeparator ? className.replace('.', '/') : className;
        Long hprofStringId = hprofStringCache.entrySequence().firstOrNull(entry -> entry.second.equals(internalClassName))?.first;
        return hprofStringId != null ? classNames.entrySequence().firstOrNull(entry -> entry.second.equals(hprofStringId))?.first : null;
    }

    public Sequence<Pair<Long, IndexedClass>> indexedClassSequence() {
        return classIndex.entrySequence().map(entry -> {
            long id = entry.first;
            ByteSubArray array = entry.second;
            return new Pair<>(id, new IndexedClass(array.readTruncatedLong(positionSize), array.readId(), array.readInt(), array.readTruncatedLong(bytesForClassSize)));
        });
    }

    public Sequence<Pair<Long, IndexedInstance>> indexedInstanceSequence() {
        return instanceIndex.entrySequence().map(entry -> {
            long id = entry.first;
            ByteSubArray array = entry.second;
            IndexedInstance instance = new IndexedInstance(array.readTruncatedLong(positionSize), array.readId(), array.readTruncatedLong(bytesForInstanceSize));
            return new Pair<>(id, instance);
        });
    }

    public Sequence<Pair<Long, IndexedObjectArray>> indexedObjectArraySequence() {
        return objectArrayIndex.entrySequence().map(entry -> {
            long id = entry.first;
            ByteSubArray array = entry.second;
            IndexedObjectArray objectArray = new IndexedObjectArray(array.readTruncatedLong(positionSize), array.readId(), array.readTruncatedLong(bytesForObjectArraySize));
            return new Pair<>(id, objectArray);
        });
    }

    public Sequence<Pair<Long, IndexedPrimitiveArray>> indexedPrimitiveArraySequence() {
        return primitiveArrayIndex.entrySequence().map(entry -> {
            long id = entry.first;
            ByteSubArray array = entry.second;
            IndexedPrimitiveArray primitiveArray = new IndexedPrimitiveArray(array.readTruncatedLong(positionSize), PrimitiveType.values()[array.readByte()], array.readTruncatedLong(bytesForPrimitiveArraySize));
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
            return new IndexedClass(array.readTruncatedLong(positionSize), array.readId(), array.readInt(), array.readTruncatedLong(bytesForClassSize));
        }
        array = instanceIndex.get(objectId);
        if (array != null) {
            return new IndexedInstance(array.readTruncatedLong(positionSize), array.readId(), array.readTruncatedLong(bytesForInstanceSize));
        }
        array = objectArrayIndex.get(objectId);
        if (array != null) {
            return new IndexedObjectArray(array.readTruncatedLong(positionSize), array.readId(), array.readTruncatedLong(bytesForObjectArraySize));
        }
        array = primitiveArrayIndex.get(objectId);
        if (array != null) {
            return new IndexedPrimitiveArray(array.readTruncatedLong(positionSize), PrimitiveType.values()[array.readByte()], array.readTruncatedLong(bytesForPrimitiveArraySize));
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

        private final int positionSize;
        private final LongObjectScatterMap<String> hprofStringCache;
        private final LongLongScatterMap classNames;
        private final UnsortedByteEntries classIndex;
        private final UnsortedByteEntries instanceIndex;
        private final UnsortedByteEntries objectArrayIndex;
        private final UnsortedByteEntries primitiveArrayIndex;
        private final Set<Long> primitiveWrapperTypes;
        private final Set<Long> primitiveWrapperClassNames;
        private final List<GcRoot> gcRoots;

        public Builder(boolean longIdentifiers, long maxPosition, int classCount, int instanceCount, int objectArrayCount, int primitiveArrayCount,
                       Set<Class<? extends GcRoot>> indexedGcRootsTypes, int bytesForClassSize, int bytesForInstanceSize,
                       int bytesForObjectArraySize, int bytesForPrimitiveArraySize) {
            this.positionSize = byteSizeForUnsigned(maxPosition);
            this.hprofStringCache = new LongObjectScatterMap<>();
            this.classNames = new LongLongScatterMap(classCount);
            this.classIndex = new UnsortedByteEntries(positionSize + (longIdentifiers ? 8 : 4) + 4 + bytesForClassSize, longIdentifiers, classCount);
            this.instanceIndex = new UnsortedByteEntries(positionSize + (longIdentifiers ? 8 : 4) + bytesForInstanceSize, longIdentifiers, instanceCount);
            this.objectArrayIndex = new UnsortedByteEntries(positionSize + (longIdentifiers ? 8 : 4) + bytesForObjectArraySize, longIdentifiers, objectArrayCount);
            this.primitiveArrayIndex = new UnsortedByteEntries(positionSize + 1 + bytesForPrimitiveArraySize, longIdentifiers, primitiveArrayCount);
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
                hprofStringCache.put(stringRecord.id, stringRecord.string);
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
                        .writeInt(classSkipContentRecord.instanceSize)
                        .writeTruncatedLong(classSkipContentRecord.recordSize, bytesForClassSize);
            } else if (record instanceof InstanceSkipContentRecord) {
                InstanceSkipContentRecord instanceSkipContentRecord = (InstanceSkipContentRecord) record;
                instanceIndex.append(instanceSkipContentRecord.id)
                        .writeTruncatedLong(position, positionSize)
                        .writeId(instanceSkipContentRecord.classId)
                        .writeTruncatedLong(instanceSkipContentRecord.recordSize, bytesForInstanceSize);
            } else if (record instanceof ObjectArraySkipContentRecord) {
                ObjectArraySkipContentRecord objectArraySkipContentRecord = (ObjectArraySkipContentRecord) record;
                objectArrayIndex.append(objectArraySkipContentRecord.id)
                        .writeTruncatedLong(position, positionSize)
                        .writeId(objectArraySkipContentRecord.arrayClassId)
                        .writeTruncatedLong(objectArraySkipContentRecord.recordSize, bytesForObjectArraySize);
            } else if (record instanceof PrimitiveArraySkipContentRecord) {
                PrimitiveArraySkipContentRecord primitiveArraySkipContentRecord = (PrimitiveArraySkipContentRecord) record;
                primitiveArrayIndex.append(primitiveArraySkipContentRecord.id)
                        .writeTruncatedLong(position, positionSize)
                        .writeByte((byte) primitiveArraySkipContentRecord.type.ordinal())
                        .writeTruncatedLong(primitiveArraySkipContentRecord.recordSize, bytesForPrimitiveArraySize);
            }
        }

        public HprofInMemoryIndex buildIndex(ProguardMapping proguardMapping, HprofHeader hprofHeader) {
            SortedBytesMap sortedInstanceIndex = instanceIndex.moveToSortedMap();
            SortedBytesMap sortedObjectArrayIndex = objectArrayIndex.moveToSortedMap();
            SortedBytesMap sortedPrimitiveArrayIndex = primitiveArrayIndex.moveToSortedMap();
            SortedBytesMap sortedClassIndex = classIndex.moveToSortedMap();

            return new HprofInMemoryIndex(positionSize, hprofStringCache, classNames, sortedClassIndex, sortedInstanceIndex,
                    sortedObjectArrayIndex, sortedPrimitiveArrayIndex, gcRoots, proguardMapping, primitiveWrapperTypes,
                    bytesForClassSize, bytesForInstanceSize, bytesForObjectArraySize, bytesForPrimitiveArraySize,
                    hprofHeader.version != HprofVersion.ANDROID);
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

    public static HprofInMemoryIndex indexHprof(StreamingHprofReader reader, HprofHeader hprofHeader, ProguardMapping proguardMapping,
                                                Set<Class<? extends GcRoot>> indexedGcRootTypes) {
        Set<Class<? extends HprofRecord>> recordTypes = Set.of(
                StringRecord.class, LoadClassRecord.class, ClassSkipContentRecord.class,
                InstanceSkipContentRecord.class, ObjectArraySkipContentRecord.class,
                PrimitiveArraySkipContentRecord.class, GcRootRecord.class
        );

        long maxClassSize = 0L;
        long maxInstanceSize = 0L;
        long maxObjectArraySize = 0L;
        long maxPrimitiveArraySize = 0L;
        int classCount = 0;
        int instanceCount = 0;
        int objectArrayCount = 0;
        int primitiveArrayCount = 0;

        long bytesRead = reader.readRecords(Set.of(ClassSkipContentRecord.class, InstanceSkipContentRecord.class,
                ObjectArraySkipContentRecord.class, PrimitiveArraySkipContentRecord.class), (position, record) -> {
            if (record instanceof ClassSkipContentRecord) {
                ClassSkipContentRecord classSkipContentRecord = (ClassSkipContentRecord) record;
                classCount++;
                maxClassSize = max(maxClassSize, classSkipContentRecord.recordSize);
            } else if (record instanceof InstanceSkipContentRecord) {
                InstanceSkipContentRecord instanceSkipContentRecord = (InstanceSkipContentRecord) record;
                instanceCount++;
                maxInstanceSize = max(maxInstanceSize, instanceSkipContentRecord.recordSize);
            } else if (record instanceof ObjectArraySkipContentRecord) {
                ObjectArraySkipContentRecord objectArraySkipContentRecord = (ObjectArraySkipContentRecord) record;
                objectArrayCount++;
                maxObjectArraySize = max(maxObjectArraySize, objectArraySkipContentRecord.recordSize);
            } else if (record instanceof PrimitiveArraySkipContentRecord) {
                PrimitiveArraySkipContentRecord primitiveArraySkipContentRecord = (PrimitiveArraySkipContentRecord) record;
                primitiveArrayCount++;
                maxPrimitiveArraySize = max(maxPrimitiveArraySize, primitiveArraySkipContentRecord.recordSize);
            }
        });

        int bytesForClassSize = byteSizeForUnsigned(maxClassSize);
        int bytesForInstanceSize = byteSizeForUnsigned(maxInstanceSize);
        int bytesForObjectArraySize = byteSizeForUnsigned(maxObjectArraySize);
        int bytesForPrimitiveArraySize = byteSizeForUnsigned(maxPrimitiveArraySize);

        Builder indexBuilderListener = new Builder(hprofHeader.identifierByteSize == 8, bytesRead, classCount, instanceCount,
                objectArrayCount, primitiveArrayCount, indexedGcRootTypes, bytesForClassSize, bytesForInstanceSize,
                bytesForObjectArraySize, bytesForPrimitiveArraySize);

        reader.readRecords(recordTypes, indexBuilderListener);
        return indexBuilderListener.buildIndex(proguardMapping, hprofHeader);
    }
}