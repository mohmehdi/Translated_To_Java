
package shark;

import okio.Buffer;
import shark.HeapObject.HeapClass;
import shark.HeapObject.HeapInstance;
import shark.HeapObject.HeapObjectArray;
import shark.HeapObject.HeapPrimitiveArray;
import shark.HprofHeapGraph.Companion.indexHprof;
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

public class HprofHeapGraph implements HeapGraph {

    private final Hprof hprof;
    private final HprofInMemoryIndex index;
    private final LruCache<Long, ObjectRecord> objectCache = new LruCache<>(3000);

    public HprofHeapGraph(Hprof hprof, HprofInMemoryIndex index) {
        this.hprof = hprof;
        this.index = index;
    }

    @Override
    public int identifierByteSize() {
        return hprof.reader.identifierByteSize();
    }

    @Override
    public GraphContext context() {
        return new GraphContext();
    }

    @Override
    public List<GcRoot> gcRoots() {
        return index.gcRoots();
    }

    @Override
    public Sequence<HeapObject> objects() {
        return index.indexedObjectSequence()
                .map(it -> wrapIndexedObject(it.second, it.first));
    }

    @Override
    public Sequence<HeapClass> classes() {
        return index.indexedClassSequence()
                .map(it -> new HeapClass(this, it.second, it.first));
    }

    @Override
    public Sequence<HeapInstance> instances() {
        return index.indexedInstanceSequence()
                .map(it -> {
                    long objectId = it.first;
                    IndexedObject indexedObject = it.second;
                    boolean isPrimitiveWrapper = index.primitiveWrapperTypes.contains(indexedObject.classId);
                    return new HeapInstance(this, indexedObject, objectId, isPrimitiveWrapper);
                });
    }

    @Override
    public HeapObject findObjectById(long objectId) {
        return wrapIndexedObject(index.indexedObject(objectId), objectId);
    }

    @Override
    public HeapClass findClassByName(String className) {
        Long classId = index.classId(className);
        if (classId == null) {
            return null;
        } else {
            return (HeapClass) findObjectById(classId);
        }
    }

    @Override
    public boolean objectExists(long objectId) {
        return index.objectIdIsIndexed(objectId);
    }

    public String fieldName(FieldRecord fieldRecord) {
        return index.hprofStringById(fieldRecord.nameStringId);
    }

    public String staticFieldName(StaticFieldRecord fieldRecord) {
        return index.hprofStringById(fieldRecord.nameStringId);
    }

    public FieldValuesReader createFieldValuesReader(InstanceDumpRecord record) {
        Buffer buffer = new Buffer();
        buffer.write(record.fieldValues);

        HprofReader reader = new HprofReader(buffer, identifierByteSize());

        return new FieldValuesReader() {
            @Override
            public ValueHolder readValue(FieldRecord field) {
                return reader.readValue(field.type);
            }
        };
    }

    public String className(long classId) {
        return index.className(classId);
    }

    public ObjectArrayDumpRecord readObjectArrayDumpRecord(long objectId, IndexedObjectArray indexedObject) {
        return readObjectRecord(objectId, indexedObject, () -> hprof.reader.readObjectArrayDumpRecord());
    }

    public PrimitiveArrayDumpRecord readPrimitiveArrayDumpRecord(long objectId, IndexedPrimitiveArray indexedObject) {
        return readObjectRecord(objectId, indexedObject, () -> hprof.reader.readPrimitiveArrayDumpRecord());
    }

    public ClassDumpRecord readClassDumpRecord(long objectId, IndexedClass indexedObject) {
        return readObjectRecord(objectId, indexedObject, () -> hprof.reader.readClassDumpRecord());
    }

    public InstanceDumpRecord readInstanceDumpRecord(long objectId, IndexedInstance indexedObject) {
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
            IndexedInstance indexedInstance = (IndexedInstance) indexedObject;
            boolean isPrimitiveWrapper = index.primitiveWrapperTypes.contains(indexedInstance.classId);
            return new HeapInstance(this, indexedInstance, objectId, isPrimitiveWrapper);
        } else if (indexedObject instanceof IndexedObjectArray) {
            IndexedObjectArray indexedObjectArray = (IndexedObjectArray) indexedObject;
            boolean isPrimitiveWrapperArray = index.primitiveWrapperTypes.contains(indexedObjectArray.arrayClassId);
            return new HeapObjectArray(this, indexedObjectArray, objectId, isPrimitiveWrapperArray);
        } else if (indexedObject instanceof IndexedPrimitiveArray) {
            return new HeapPrimitiveArray(this, (IndexedPrimitiveArray) indexedObject, objectId);
        }
        return null;
    }

    public static HeapGraph indexHprof(Hprof hprof) {
        HprofInMemoryIndex index = HprofInMemoryIndex.createReadingHprof(hprof.reader);
        return new HprofHeapGraph(hprof, index);
    }
}