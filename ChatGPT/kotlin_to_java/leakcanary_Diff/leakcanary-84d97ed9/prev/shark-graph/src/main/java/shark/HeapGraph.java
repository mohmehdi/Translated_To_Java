package shark;

import okio.Buffer;
import shark.HeapObject.HeapClass;
import shark.HeapObject.HeapInstance;
import shark.HeapObject.HeapObjectArray;
import shark.HeapObject.HeapPrimitiveArray;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassDumpRecord.FieldRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassDumpRecord.StaticFieldRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.InstanceDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ObjectArrayDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord;
import shark.internal.FieldValuesReader;
import shark.internal.HprofInMemoryIndex;
import shark.internal.IndexedObject;
import shark.internal.IndexedObject.IndexedClass;
import shark.internal.IndexedObject.IndexedInstance;
import shark.internal.IndexedObject.IndexedObjectArray;
import shark.internal.IndexedObject.IndexedPrimitiveArray;
import shark.internal.LruCache;

public class HeapGraph {

    private final Hprof hprof;
    private final HprofInMemoryIndex index;

    public HeapGraph(Hprof hprof, HprofInMemoryIndex index) {
        this.hprof = hprof;
        this.index = index;
    }

    public int objectIdByteSize() {
        return hprof.reader.objectIdByteSize;
    }

    public GraphContext context = new GraphContext();

    public List<GcRoot> gcRoots() {
        return index.gcRoots();
    }

    public Sequence<HeapObject> objects() {
        return index.indexedObjectSequence()
                .map(it -> wrapIndexedObject(it.second, it.first));
    }

    public Sequence<HeapClass> classes() {
        return index.indexedClassSequence()
                .map(it -> new HeapClass(this, it.second, it.first));
    }

    public Sequence<HeapInstance> instances() {
        return index.indexedInstanceSequence()
                .map(it -> new HeapInstance(this, it.second, it.first,
                        index.primitiveWrapperTypes.contains(it.second.classId)));
    }

    private final LruCache<Long, ObjectRecord> objectCache = new LruCache<>(3000);

    public HeapObject findObjectById(long objectId) {
        return wrapIndexedObject(index.indexedObject(objectId), objectId);
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

    String fieldName(FieldRecord fieldRecord) {
        return index.hprofStringById(fieldRecord.nameStringId);
    }

    String staticFieldName(StaticFieldRecord fieldRecord) {
        return index.hprofStringById(fieldRecord.nameStringId);
    }

    FieldValuesReader createFieldValuesReader(InstanceDumpRecord record) {
        Buffer buffer = new Buffer();
        buffer.write(record.fieldValues);

        HprofReader reader = new HprofReader(buffer, 0, objectIdByteSize());

        return new FieldValuesReader() {
            @Override
            public ValueHolder readValue(FieldRecord field) {
                return reader.readValue(field.type);
            }
        };
    }

    String className(long classId) {
        return index.className(classId);
    }

    ObjectArrayDumpRecord readObjectArrayDumpRecord(long objectId, IndexedObjectArray indexedObject) {
        return readObjectRecord(objectId, indexedObject, () -> hprof.reader.readObjectArrayDumpRecord());
    }

    PrimitiveArrayDumpRecord readPrimitiveArrayDumpRecord(long objectId, IndexedPrimitiveArray indexedObject) {
        return readObjectRecord(objectId, indexedObject, () -> hprof.reader.readPrimitiveArrayDumpRecord());
    }

    ClassDumpRecord readClassDumpRecord(long objectId, IndexedClass indexedObject) {
        return readObjectRecord(objectId, indexedObject, () -> hprof.reader.readClassDumpRecord());
    }

    InstanceDumpRecord readInstanceDumpRecord(long objectId, IndexedInstance indexedObject) {
        return readObjectRecord(objectId, indexedObject, () -> hprof.reader.readInstanceDumpRecord());
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
        } else {
            throw new IllegalArgumentException("Unknown indexed object type");
        }
    }

    public static HeapGraph indexHprof(Hprof hprof) {
        HprofInMemoryIndex index = HprofInMemoryIndex.createReadingHprof(hprof.reader);
        return new HeapGraph(hprof, index);
    }
}