

package shark;

import shark.HeapObject.HeapClass;
import shark.HeapObject.HeapInstance;
import shark.HeapObject.HeapObjectArray;
import shark.HeapObject.HeapPrimitiveArray;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.FieldRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.StaticFieldRecord;
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
import shark.internal.FieldValuesReader;
import shark.internal.HprofInMemoryIndex;
import shark.internal.IndexedObject;
import shark.internal.IndexedObject.IndexedClass;
import shark.internal.IndexedObject.IndexedInstance;
import shark.internal.IndexedObject.IndexedObjectArray;
import shark.internal.IndexedObject.IndexedPrimitiveArray;
import shark.internal.LruCache;
import kotlin.reflect.KClass;

import java.io.File;

public class HprofHeapGraph implements CloseableHeapGraph {

    private final HprofHeader header;
    private final RandomAccessHprofReader reader;
    private final HprofInMemoryIndex index;

    public HprofHeapGraph(HprofHeader header, RandomAccessHprofReader reader, HprofInMemoryIndex index) {
        this.header = header;
        this.reader = reader;
        this.index = index;
    }

    public int getIdentifierByteSize() {
        return header.getIdentifierByteSize();
    }

    public GraphContext context = new GraphContext();

    public List<GcRoot> getGcRoots() {
        return index.gcRoots();
    }

    public Sequence<HeapObject> getObjects() {
        return index.indexedObjectSequence()
                .map(it -> wrapIndexedObject(it.second, it.first));
    }

    public Sequence<HeapClass> getClasses() {
        return index.indexedClassSequence()
                .map(it -> {
                    long objectId = it.first;
                    IndexedObject indexedObject = it.second;
                    return new HeapClass(this, indexedObject, objectId);
                });
    }

    public Sequence<HeapInstance> getInstances() {
        return index.indexedInstanceSequence()
                .map(it -> {
                    long objectId = it.first;
                    IndexedObject indexedObject = it.second;
                    boolean isPrimitiveWrapper = index.primitiveWrapperTypes.contains(indexedObject.classId);
                    return new HeapInstance(this, indexedObject, objectId, isPrimitiveWrapper);
                });
    }

    public Sequence<HeapObjectArray> getObjectArrays() {
        return index.indexedObjectArraySequence().map(it -> {
            long objectId = it.first;
            IndexedObject indexedObject = it.second;
            boolean isPrimitiveWrapper = index.primitiveWrapperTypes.contains(indexedObject.arrayClassId);
            return new HeapObjectArray(this, indexedObject, objectId, isPrimitiveWrapper);
        });
    }

    public Sequence<HeapPrimitiveArray> getPrimitiveArrays() {
        return index.indexedPrimitiveArraySequence().map(it -> {
            long objectId = it.first;
            IndexedObject indexedObject = it.second;
            return new HeapPrimitiveArray(this, indexedObject, objectId);
        });
    }

    private final LruCache<Long, ObjectRecord> objectCache = new LruCache<>(INTERNAL_LRU_CACHE_SIZE);

    private final HeapClass javaLangObjectClass = findClassByName("java.lang.Object");

    public HeapObject findObjectById(long objectId) {
        return findObjectByIdOrNull(objectId);
    }

    public HeapObject findObjectByIdOrNull(long objectId) {
        if (objectId == javaLangObjectClass.getObjectId()) return javaLangObjectClass;

        IndexedObject indexedObject = index.indexedObjectOrNull(objectId);
        if (indexedObject == null) return null;
        return wrapIndexedObject(indexedObject, objectId);
    }

    public HeapClass findClassByName(String className) {
        Long classId = index.classId(className);
        if (classId == null) {
            return null;
        } else {
            return (HeapClass) findObjectById(classId);
        }
    }

    public boolean objectExists(long objectId) {
        return index.objectIdIsIndexed(objectId);
    }

    public void close() {
        reader.close();
    }

    public String fieldName(long classId, FieldRecord fieldRecord) {
        return index.fieldName(classId, fieldRecord.getNameStringId());
    }

    public String staticFieldName(long classId, StaticFieldRecord fieldRecord) {
        return index.fieldName(classId, fieldRecord.getNameStringId());
    }

    public FieldValuesReader createFieldValuesReader(InstanceDumpRecord record) {
        return new FieldValuesReader(record, getIdentifierByteSize());
    }

    public String className(long classId) {
        return index.className(classId);
    }

    public ObjectArrayDumpRecord readObjectArrayDumpRecord(long objectId, IndexedObjectArray indexedObject) {
        return readObjectRecord(objectId, indexedObject, this::readObjectArrayDumpRecord);
    }

    public int readObjectArrayByteSize(long objectId, IndexedObjectArray indexedObject) {
        ObjectArrayDumpRecord cachedRecord = (ObjectArrayDumpRecord) objectCache.get(objectId);
        if (cachedRecord != null) {
            return cachedRecord.getElementIds().size() * getIdentifierByteSize();
        }
        long position = indexedObject.getPosition() + getIdentifierByteSize() + PrimitiveType.INT.byteSize;
        long size = PrimitiveType.INT.byteSize;
        int thinRecordSize = reader.readRecord(position, size, () -> readInt());
        return thinRecordSize * getIdentifierByteSize();
    }

    public PrimitiveArrayDumpRecord readPrimitiveArrayDumpRecord(long objectId, IndexedPrimitiveArray indexedObject) {
        return readObjectRecord(objectId, indexedObject, this::readPrimitiveArrayDumpRecord);
    }

    public int readPrimitiveArrayByteSize(long objectId, IndexedPrimitiveArray indexedObject) {
        PrimitiveArrayDumpRecord cachedRecord = (PrimitiveArrayDumpRecord) objectCache.get(objectId);
        if (cachedRecord != null) {
            if (cachedRecord instanceof BooleanArrayDump) {
                return ((BooleanArrayDump) cachedRecord).getArray().size() * PrimitiveType.BOOLEAN.byteSize;
            } else if (cachedRecord instanceof CharArrayDump) {
                return ((CharArrayDump) cachedRecord).getArray().size() * PrimitiveType.CHAR.byteSize;
            } else if (cachedRecord instanceof FloatArrayDump) {
                return ((FloatArrayDump) cachedRecord).getArray().size() * PrimitiveType.FLOAT.byteSize;
            } else if (cachedRecord instanceof DoubleArrayDump) {
                return ((DoubleArrayDump) cachedRecord).getArray().size() * PrimitiveType.DOUBLE.byteSize;
            } else if (cachedRecord instanceof ByteArrayDump) {
                return ((ByteArrayDump) cachedRecord).getArray().size() * PrimitiveType.BYTE.byteSize;
            } else if (cachedRecord instanceof ShortArrayDump) {
                return ((ShortArrayDump) cachedRecord).getArray().size() * PrimitiveType.SHORT.byteSize;
            } else if (cachedRecord instanceof IntArrayDump) {
                return ((IntArrayDump) cachedRecord).getArray().size() * PrimitiveType.INT.byteSize;
            } else if (cachedRecord instanceof LongArrayDump) {
                return ((LongArrayDump) cachedRecord).getArray().size() * PrimitiveType.LONG.byteSize;
            }
        }
        long position = indexedObject.getPosition() + getIdentifierByteSize() + PrimitiveType.INT.byteSize;
        long size = reader.readRecord(position, PrimitiveType.INT.byteSize, () -> readInt());
        return (int) (size * indexedObject.getPrimitiveType().byteSize);
    }

    public ClassDumpRecord readClassDumpRecord(long objectId, IndexedClass indexedObject) {
        return readObjectRecord(objectId, indexedObject, this::readClassDumpRecord);
    }

    public InstanceDumpRecord readInstanceDumpRecord(long objectId, IndexedInstance indexedObject) {
        return readObjectRecord(objectId, indexedObject, this::readInstanceDumpRecord);
    }

    private <T extends ObjectRecord> T readObjectRecord(long objectId, IndexedObject indexedObject, HprofRecordReader<T> readBlock) {
        ObjectRecord objectRecordOrNull = objectCache.get(objectId);
        if (objectRecordOrNull != null) {
            return (T) objectRecordOrNull;
        }
        return reader.readRecord(indexedObject.getPosition(), indexedObject.getRecordSize(), readBlock)
                .apply(objectRecord -> objectCache.put(objectId, objectRecord));
    }

    private HeapObject wrapIndexedObject(IndexedObject indexedObject, long objectId) {
        if (indexedObject instanceof IndexedClass) {
            return new HeapClass(this, (IndexedClass) indexedObject, objectId);
        } else if (indexedObject instanceof IndexedInstance) {
            boolean isPrimitiveWrapper = index.primitiveWrapperTypes.contains(((IndexedInstance) indexedObject).getClassId());
            return new HeapInstance(this, (IndexedInstance) indexedObject, objectId, isPrimitiveWrapper);
        } else if (indexedObject instanceof IndexedObjectArray) {
            boolean isPrimitiveWrapperArray = index.primitiveWrapperTypes.contains(((IndexedObjectArray) indexedObject).getArrayClassId());
            return new HeapObjectArray(this, (IndexedObjectArray) indexedObject, objectId, isPrimitiveWrapperArray);
        } else if (indexedObject instanceof IndexedPrimitiveArray) {
            return new HeapPrimitiveArray(this, (IndexedPrimitiveArray) indexedObject, objectId);
        }
        return null;
    }

    public static int INTERNAL_LRU_CACHE_SIZE = 3000;

    public static CloseableHeapGraph openHeapGraph(File file, ProguardMapping proguardMapping, Set<KClass<? extends GcRoot>> indexedGcRootTypes) {
        return new FileSourceProvider(file).openHeapGraph(proguardMapping, indexedGcRootTypes);
    }

    public static CloseableHeapGraph openHeapGraph(DualSourceProvider dualSourceProvider, ProguardMapping proguardMapping, Set<KClass<? extends GcRoot>> indexedGcRootTypes) {
        HprofHeader header = dualSourceProvider.openStreamingSource().use(HprofHeader::parseHeaderOf);
        HprofInMemoryIndex index = HprofIndex.indexRecordsOf(dualSourceProvider, header, proguardMapping, indexedGcRootTypes);
        return index.openHeapGraph();
    }

    @Deprecated(
            value = "Replaced by HprofIndex.indexRecordsOf().openHeapGraph() or File.openHeapGraph()",
            replacement = "HprofIndex.indexRecordsOf(hprof, proguardMapping, indexedGcRootTypes).openHeapGraph()"
    )
    public static HeapGraph indexHprof(Hprof hprof, ProguardMapping proguardMapping, Set<KClass<? extends GcRoot>> indexedGcRootTypes) {
        HprofInMemoryIndex index = HprofIndex.indexRecordsOf(new FileSourceProvider(hprof.getFile()), hprof.getHeader(), proguardMapping, indexedGcRootTypes);
        HeapGraph graph = index.openHeapGraph();
        hprof.attachClosable(graph);
        return graph;
    }
}