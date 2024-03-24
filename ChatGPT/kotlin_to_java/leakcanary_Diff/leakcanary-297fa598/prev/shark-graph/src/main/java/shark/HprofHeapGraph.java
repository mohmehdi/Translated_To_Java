

package shark;

import shark.GcRoot.JavaFrame;
import shark.GcRoot.JniGlobal;
import shark.GcRoot.JniLocal;
import shark.GcRoot.JniMonitor;
import shark.GcRoot.MonitorUsed;
import shark.GcRoot.NativeStack;
import shark.GcRoot.StickyClass;
import shark.GcRoot.ThreadBlock;
import shark.GcRoot.ThreadObject;
import shark.HeapObject.HeapClass;
import shark.HeapObject.HeapInstance;
import shark.HeapObject.HeapObjectArray;
import shark.HeapObject.HeapPrimitiveArray;
import shark.HprofHeapGraph.Companion;
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

public class HprofHeapGraph implements HeapGraph {

    private final Hprof hprof;
    private final HprofInMemoryIndex index;
    private final LruCache<Long, ObjectRecord> objectCache = new LruCache<>(3000);
    private final HeapClass javaLangObjectClass;

    public HprofHeapGraph(Hprof hprof, HprofInMemoryIndex index) {
        this.hprof = hprof;
        this.index = index;
        this.javaLangObjectClass = findClassByName("java.lang.Object");
    }

    public int getIdentifierByteSize() {
        return hprof.reader.getIdentifierByteSize();
    }

    public GraphContext getContext() {
        return new GraphContext();
    }

    public List<GcRoot> getGcRoots() {
        return index.gcRoots();
    }

    public Sequence<HeapObject> getObjects() {
        return index.indexedObjectSequence()
                .map(it -> wrapIndexedObject(it.second, it.first));
    }

    public Sequence<HeapClass> getClasses() {
        return index.indexedClassSequence()
                .map(it -> new HeapClass(this, it.second, it.first));
    }

    public Sequence<HeapInstance> getInstances() {
        return index.indexedInstanceSequence()
                .map(it -> new HeapInstance(this, it.second, it.first, index.primitiveWrapperTypes.contains(it.second.classId)));
    }

    public Sequence<HeapObjectArray> getObjectArrays() {
        return index.indexedObjectArraySequence()
                .map(it -> new HeapObjectArray(this, it.second, it.first, index.primitiveWrapperTypes.contains(it.second.arrayClassId)));
    }

    public Sequence<HeapPrimitiveArray> getPrimitiveArrays() {
        return index.indexedPrimitiveArraySequence()
                .map(it -> new HeapPrimitiveArray(this, it.second, it.first));
    }

    public HeapObject findObjectById(long objectId) {
        return findObjectByIdOrNull(objectId);
    }

    public HeapObject findObjectByIdOrNull(long objectId) {
        if (objectId == javaLangObjectClass.getObjectId()) {
            return javaLangObjectClass;
        }

        IndexedObject indexedObject = index.indexedObjectOrNull(objectId);
        if (indexedObject == null) {
            return null;
        }
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

    public String fieldName(long classId, FieldRecord fieldRecord) {
        return index.fieldName(classId, fieldRecord.nameStringId);
    }

    public String staticFieldName(long classId, StaticFieldRecord fieldRecord) {
        return index.fieldName(classId, fieldRecord.nameStringId);
    }

    public FieldValuesReader createFieldValuesReader(InstanceDumpRecord record) {
        return new FieldValuesReader(record, getIdentifierByteSize());
    }

    public String className(long classId) {
        return index.className(classId);
    }

    public ObjectArrayDumpRecord readObjectArrayDumpRecord(long objectId, IndexedObjectArray indexedObject) {
        return readObjectRecord(objectId, indexedObject, hprof.reader::readObjectArrayDumpRecord);
    }

    public int readObjectArrayByteSize(long objectId, IndexedObjectArray indexedObject) {
        ObjectArrayDumpRecord cachedRecord = (ObjectArrayDumpRecord) objectCache.get(objectId);
        if (cachedRecord != null) {
            return cachedRecord.elementIds.size() * getIdentifierByteSize();
        }
        hprof.moveReaderTo(indexedObject.position);
        ObjectArrayDumpRecord thinRecord = hprof.reader.readObjectArraySkipContentRecord();
        return thinRecord.size * getIdentifierByteSize();
    }

    public PrimitiveArrayDumpRecord readPrimitiveArrayDumpRecord(long objectId, IndexedPrimitiveArray indexedObject) {
        return readObjectRecord(objectId, indexedObject, hprof.reader::readPrimitiveArrayDumpRecord);
    }

    public int readPrimitiveArrayByteSize(long objectId, IndexedPrimitiveArray indexedObject) {
        PrimitiveArrayDumpRecord cachedRecord = (PrimitiveArrayDumpRecord) objectCache.get(objectId);
        if (cachedRecord != null) {
            if (cachedRecord instanceof BooleanArrayDump) {
                return ((BooleanArrayDump) cachedRecord).array.size() * PrimitiveType.BOOLEAN.byteSize;
            } else if (cachedRecord instanceof CharArrayDump) {
                return ((CharArrayDump) cachedRecord).array.size() * PrimitiveType.CHAR.byteSize;
            } else if (cachedRecord instanceof FloatArrayDump) {
                return ((FloatArrayDump) cachedRecord).array.size() * PrimitiveType.FLOAT.byteSize;
            } else if (cachedRecord instanceof DoubleArrayDump) {
                return ((DoubleArrayDump) cachedRecord).array.size() * PrimitiveType.DOUBLE.byteSize;
            } else if (cachedRecord instanceof ByteArrayDump) {
                return ((ByteArrayDump) cachedRecord).array.size() * PrimitiveType.BYTE.byteSize;
            } else if (cachedRecord instanceof ShortArrayDump) {
                return ((ShortArrayDump) cachedRecord).array.size() * PrimitiveType.SHORT.byteSize;
            } else if (cachedRecord instanceof IntArrayDump) {
                return ((IntArrayDump) cachedRecord).array.size() * PrimitiveType.INT.byteSize;
            } else if (cachedRecord instanceof LongArrayDump) {
                return ((LongArrayDump) cachedRecord).array.size() * PrimitiveType.LONG.byteSize;
            }
        }
        hprof.moveReaderTo(indexedObject.position);
        PrimitiveArrayDumpRecord thinRecord = hprof.reader.readPrimitiveArraySkipContentRecord();
        return thinRecord.size * thinRecord.type.byteSize;
    }

    public ClassDumpRecord readClassDumpRecord(long objectId, IndexedClass indexedObject) {
        return readObjectRecord(objectId, indexedObject, hprof.reader::readClassDumpRecord);
    }

    public InstanceDumpRecord readInstanceDumpRecord(long objectId, IndexedInstance indexedObject) {
        return readObjectRecord(objectId, indexedObject, hprof.reader::readInstanceDumpRecord);
    }

    private <T extends ObjectRecord> T readObjectRecord(long objectId, IndexedObject indexedObject, Supplier<T> readBlock) {
        ObjectRecord objectRecordOrNull = objectCache.get(objectId);
        if (objectRecordOrNull != null) {
            return (T) objectRecordOrNull;
        }
        hprof.moveReaderTo(indexedObject.position);
        T result = readBlock.get();
        objectCache.put(objectId, result);
        return result;
    }

    private HeapObject wrapIndexedObject(IndexedObject indexedObject, long objectId) {
        if (indexedObject instanceof IndexedClass) {
            return new HeapClass(this, (IndexedClass) indexedObject, objectId);
        } else if (indexedObject instanceof IndexedInstance) {
            boolean isPrimitiveWrapper = index.primitiveWrapperTypes.contains(((IndexedInstance) indexedObject).classId);
            return new HeapInstance(this, (IndexedInstance) indexedObject, objectId, isPrimitiveWrapper);
        } else if (indexedObject instanceof IndexedObjectArray) {
            boolean isPrimitiveWrapperArray = index.primitiveWrapperTypes.contains(((IndexedObjectArray) indexedObject).arrayClassId);
            return new HeapObjectArray(this, (IndexedObjectArray) indexedObject, objectId, isPrimitiveWrapperArray);
        } else if (indexedObject instanceof IndexedPrimitiveArray) {
            return new HeapPrimitiveArray(this, (IndexedPrimitiveArray) indexedObject, objectId);
        }
        return null;
    }

    public static HeapGraph indexHprof(Hprof hprof, ProguardMapping proguardMapping, Set<KClass<? extends GcRoot>> indexedGcRootTypes) {
        HprofInMemoryIndex index = HprofInMemoryIndex.createReadingHprof(hprof, proguardMapping, indexedGcRootTypes);
        return new HprofHeapGraph(hprof, index);
    }
}