

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
import shark.ProguardMapping;
import shark.StreamingHprofReader;
import shark.ValueHolder;
import shark.internal.IndexedObject.IndexedClass;
import shark.internal.IndexedObject.IndexedInstance;
import shark.internal.IndexedObject.IndexedObjectArray;
import shark.internal.IndexedObject.IndexedPrimitiveArray;
import shark.internal.hppc.LongLongScatterMap;
import shark.internal.hppc.LongObjectScatterMap;
import shark.PrimitiveType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

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
    private final int bytesForClassSize;
    private final int bytesForInstanceSize;
    private final int bytesForObjectArraySize;
    private final int bytesForPrimitiveArraySize;
    private final boolean useForwardSlashClassPackageSeparator;

    private HprofInMemoryIndex(Builder builder) {
        this.positionSize = builder.positionSize;
        this.hprofStringCache = builder.hprofStringCache;
        this.classNames = builder.classNames;
        this.classIndex = builder.classIndex.build();
        this.instanceIndex = builder.instanceIndex.build();
        this.objectArrayIndex = builder.objectArrayIndex.build();
        this.primitiveArrayIndex = builder.primitiveArrayIndex.build();
        this.gcRoots = builder.gcRoots.stream().collect(Collectors.toList());
        this.proguardMapping = builder.proguardMapping;
        this.primitiveWrapperTypes = builder.primitiveWrapperTypes;
        this.bytesForClassSize = builder.bytesForClassSize;
        this.bytesForInstanceSize = builder.bytesForInstanceSize;
        this.bytesForObjectArraySize = builder.bytesForObjectArraySize;
        this.bytesForPrimitiveArraySize = builder.bytesForPrimitiveArraySize;
        this.useForwardSlashClassPackageSeparator = builder.useForwardSlashClassPackageSeparator;
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
        return (proguardMapping != null ? proguardMapping.deobfuscateClassName(classNameString) : classNameString).replace(useForwardSlashClassPackageSeparator ? '/' : '.', '.');
    }

    public Long classId(String className) {
        String internalClassName = useForwardSlashClassPackageSeparator ? className.replace('.', '/') : className;

        Long hprofStringId = hprofStringCache.entrySet().stream()
                .filter(entry -> entry.getValue().equals(internalClassName))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        return hprofStringId != null ? classNames.get(hprofStringId) : null;
    }

    public Stream<Pair<Long, IndexedClass>> indexedClassSequence() {
        return classIndex.entrySet().stream()
                .map(entry -> Pair.create(
                        entry.getKey(),
                        new IndexedClass(
                                entry.getValue(),
                                positionSize,
                                bytesForClassSize
                        )
                ));
    }

    public Stream<Pair<Long, IndexedInstance>> indexedInstanceSequence() {
        return instanceIndex.entrySet().stream()
                .map(entry -> Pair.create(
                        entry.getKey(),
                        new IndexedInstance(
                                entry.getValue(),
                                positionSize,
                                bytesForInstanceSize
                        )
                ));
    }

    public Stream<Pair<Long, IndexedObjectArray>> indexedObjectArraySequence() {
        return objectArrayIndex.entrySet().stream()
                .map(entry -> Pair.create(
                        entry.getKey(),
                        new IndexedObjectArray(
                                entry.getValue(),
                                positionSize,
                                bytesForObjectArraySize
                        )
                ));
    }

    public Stream<Pair<Long, IndexedPrimitiveArray>> indexedPrimitiveArraySequence() {
        return primitiveArrayIndex.entrySet().stream()
                .map(entry -> Pair.create(
                        entry.getKey(),
                        new IndexedPrimitiveArray(
                                entry.getValue(),
                                positionSize,
                                bytesForPrimitiveArraySize
                        )
                ));
    }

    public Stream<Pair<Long, IndexedObject>> indexedObjectSequence() {
        return Stream.of(
                indexedClassSequence(),
                indexedInstanceSequence(),
                indexedObjectArraySequence(),
                indexedPrimitiveArraySequence()
        ).flatMap(Function.identity());
    }

    public List<GcRoot> gcRoots() {
        return gcRoots;
    }

    public IndexedObject indexedObjectOrNull(long objectId) {
        byte[] array = classIndex.get(objectId);
        if (array != null) {
            return new IndexedClass(array, positionSize, bytesForClassSize);
        }
        array = instanceIndex.get(objectId);
        if (array != null) {
            return new IndexedInstance(array, positionSize, bytesForInstanceSize);
        }
        array = objectArrayIndex.get(objectId);
        if (array != null) {
            return new IndexedObjectArray(array, positionSize, bytesForObjectArraySize);
        }
        array = primitiveArrayIndex.get(objectId);
        if (array != null) {
            return new IndexedPrimitiveArray(array, positionSize, bytesForPrimitiveArraySize);
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
        return hprofStringCache.get(id);
    }

    public static class Builder {

        private final int positionSize;
        private final LongObjectScatterMap<String> hprofStringCache;
        private final LongLongScatterMap classNames;
        private final SortedMapBuilder<Long, byte[]> classIndex;
        private final SortedMapBuilder<Long, byte[]> instanceIndex;
        private final SortedMapBuilder<Long, byte[]> objectArrayIndex;
        private final SortedMapBuilder<Long, byte[]> primitiveArrayIndex;
        private final Set<Long> primitiveWrapperTypes;
        private final boolean useForwardSlashClassPackageSeparator;

        public Builder(int maxPosition, int classCount, int instanceCount, int objectArrayCount, int primitiveArrayCount, Set<Class<? extends GcRoot>> indexedGcRootTypes, int bytesForClassSize, int bytesForInstanceSize, int bytesForObjectArraySize, int bytesForPrimitiveArraySize) {
            this.positionSize = byteSizeForUnsigned(maxPosition);
            this.hprofStringCache = new LongObjectScatterMap<>();
            this.classNames = new LongLongScatterMap(classCount);
            this.classIndex = new SortedMapBuilder<>(Long::compare, bytesPerValue -> new byte[bytesPerValue], classCount);
            this.instanceIndex = new SortedMapBuilder<>(Long::compare, bytesPerValue -> new byte[bytesPerValue], instanceCount);
            this.objectArrayIndex = new SortedMapBuilder<>(Long::compare, bytesPerValue -> new byte[bytesPerValue], objectArrayCount);
            this.primitiveArrayIndex = new SortedMapBuilder<>(Long::compare, bytesPerValue -> new byte[bytesPerValue], primitiveArrayCount);
            this.primitiveWrapperTypes = EnumSet.noneOf(Long.class);
            this.useForwardSlashClassPackageSeparator = bytesForClassSize != 0;

            // Initialize indexedGcRootTypes here
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
        if (gcRoot.id != ValueHolder.NULL_REFERENCE && indexedGcRootsTypes.contains(gcRoot.javaClass)) {
        gcRoots.add(gcRoot);
        }
    } else if (record instanceof ClassSkipContentRecord) {
        ClassSkipContentRecord classSkipContentRecord = (ClassSkipContentRecord) record;
        classIndex
            .append(classSkipContentRecord.id)
            .writeTruncatedLong(position, positionSize)
            .writeId(classSkipContentRecord.superclassId)
            .writeInt(classSkipContentRecord.instanceSize)
            .writeTruncatedLong(classSkipContentRecord.recordSize, bytesForClassSize);
    } else if (record instanceof InstanceSkipContentRecord) {
        InstanceSkipContentRecord instanceSkipContentRecord = (InstanceSkipContentRecord) record;
        instanceIndex
            .append(instanceSkipContentRecord.id)
            .writeTruncatedLong(position, positionSize)
            .writeId(instanceSkipContentRecord.classId)
            .writeTruncatedLong(instanceSkipContentRecord.recordSize, bytesForInstanceSize);
    } else if (record instanceof ObjectArraySkipContentRecord) {
        ObjectArraySkipContentRecord objectArraySkipContentRecord = (ObjectArraySkipContentRecord) record;
        objectArrayIndex
            .append(objectArraySkipContentRecord.id)
            .writeTruncatedLong(position, positionSize)
            .writeId(objectArraySkipContentRecord.arrayClassId)
            .writeTruncatedLong(objectArraySkipContentRecord.recordSize, bytesForObjectArraySize);
    } else if (record instanceof PrimitiveArraySkipContentRecord) {
        PrimitiveArraySkipContentRecord primitiveArraySkipContentRecord = (PrimitiveArraySkipContentRecord) record;
        primitiveArrayIndex
            .append(primitiveArraySkipContentRecord.id)
            .writeTruncatedLong(position, positionSize)
            .writeByte((byte) primitiveArraySkipContentRecord.type.ordinal)
            .writeTruncatedLong(primitiveArraySkipContentRecord.recordSize, bytesForPrimitiveArraySize);
    }
    }

        public HprofInMemoryIndex buildIndex(ProguardMapping proguardMapping, HprofHeader hprofHeader) {
            SortedMap<Long, byte[]> sortedInstanceIndex = instanceIndex.build();
            SortedMap<Long, byte[]> sortedObjectArrayIndex = objectArrayIndex.build();
            SortedMap<Long, byte[]> sortedPrimitiveArrayIndex = primitiveArrayIndex.build();
            SortedMap<Long, byte[]> sortedClassIndex = classIndex.build();

            return new HprofInMemoryIndex(this, sortedClassIndex, sortedInstanceIndex, sortedObjectArrayIndex, sortedPrimitiveArrayIndex, proguardMapping, hprofHeader.version != HprofVersion.ANDROID);
        }


    }

    private HprofInMemoryIndex(Builder builder, SortedMap<Long, byte[]> sortedClassIndex, SortedMap<Long, byte[]> sortedInstanceIndex, SortedMap<Long, byte[]> sortedObjectArrayIndex, SortedMap<Long, byte[]> sortedPrimitiveArrayIndex, ProguardMapping proguardMapping, boolean useForwardSlashClassPackageSeparator) {
        this.positionSize = builder.positionSize;
        this.hprofStringCache = builder.hprofStringCache;
        this.classNames = builder.classNames;
        this.classIndex = sortedClassIndex;
        this.instanceIndex = sortedInstanceIndex;
        this.objectArrayIndex = sortedObjectArrayIndex;
        this.primitiveArrayIndex = sortedPrimitiveArrayIndex;
        this.gcRoots = Collections.emptyList();
        this.proguardMapping = proguardMapping;
        this.primitiveWrapperTypes = builder.primitiveWrapperTypes;
        this.bytesForClassSize = builder.bytesForClassSize;
        this.bytesForInstanceSize = builder.bytesForInstanceSize;
        this.bytesForObjectArraySize = builder.bytesForObjectArraySize;
        this.bytesForPrimitiveArraySize = builder.bytesForPrimitiveArraySize;
        this.useForwardSlashClassPackageSeparator = useForwardSlashClassPackageSeparator;
    }

   private static final Set<String> PRIMITIVE_WRAPPER_TYPES = Set.of(
            Boolean.class.getName(), Character.class.getName(), Float.class.getName(),
            Double.class.getName(), Byte.class.getName(), Short.class.getName(),
            Integer.class.getName(), Long.class.getName()
    );

    private static int byteSizeForUnsigned(long maxValue) {
        int byteCount = 0;
        long value = maxValue;
        while (value != 0L) {
            value = value >> 8;
            byteCount++;
        }
        return byteCount;
    }

    public static HprofInMemoryIndex indexHprof(
            StreamingHprofReader reader,
            HprofHeader hprofHeader,
            ProguardMapping proguardMapping,
            Set<Class<? extends GcRoot>> indexedGcRootTypes
    ) {
        Set<Class<? extends Record>> recordTypes = Set.of(
                StringRecord.class,
                LoadClassRecord.class,
                ClassSkipContentRecord.class,
                InstanceSkipContentRecord.class,
                ObjectArraySkipContentRecord.class,
                PrimitiveArraySkipContentRecord.class,
                GcRootRecord.class
        );

        long maxClassSize = 0;
        long maxInstanceSize = 0;
        long maxObjectArraySize = 0;
        long maxPrimitiveArraySize = 0;
        long classCount = 0;
        long instanceCount = 0;
        long objectArrayCount = 0;
        long primitiveArrayCount = 0;

        long bytesRead = reader.readRecords(Set.of(
                ClassSkipContentRecord.class,
                InstanceSkipContentRecord.class,
                ObjectArraySkipContentRecord.class,
                PrimitiveArraySkipContentRecord.class
        ), record -> {
            switch (record.getClass()) {
                case ClassSkipContentRecord.class:
                    classCount++;
                    maxClassSize = Math.max(maxClassSize, record.getRecordSize());
                    break;
                case InstanceSkipContentRecord.class:
                    instanceCount++;
                    maxInstanceSize = Math.max(maxInstanceSize, record.getRecordSize());
                    break;
                case ObjectArraySkipContentRecord.class:
                    objectArrayCount++;
                    maxObjectArraySize = Math.max(maxObjectArraySize, record.getRecordSize());
                    break;
                case PrimitiveArraySkipContentRecord.class:
                    primitiveArrayCount++;
                    maxPrimitiveArraySize = Math.max(maxPrimitiveArraySize, record.getRecordSize());
                    break;
            }
            return 1;
        });

        int bytesForClassSize = byteSizeForUnsigned(maxClassSize);
        int bytesForInstanceSize = byteSizeForUnsigned(maxInstanceSize);
        int bytesForObjectArraySize = byteSizeForUnsigned(maxObjectArraySize);
        int bytesForPrimitiveArraySize = byteSizeForUnsigned(maxPrimitiveArraySize);

        IndexBuilderListener indexBuilderListener = new IndexBuilderListener(
                hprofHeader.getIdentifierByteSize() == 8,
                bytesRead,
                classCount,
                instanceCount,
                objectArrayCount,
                primitiveArrayCount,
                indexedGcRootTypes,
                bytesForClassSize,
                bytesForInstanceSize,
                bytesForObjectArraySize,
                bytesForPrimitiveArraySize
        );

        reader.readRecords(recordTypes, indexBuilderListener);
        return indexBuilderListener.buildIndex(proguardMapping, hprofHeader);
    }
}