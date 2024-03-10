

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

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class HprofHeapGraph extends HeapGraph {

    private final Hprof hprof;
    private final HprofInMemoryIndex index;
    private final int identifierByteSize;
    private final GraphContext context;
    private final LruCache<Long, ObjectRecord> objectCache;

    HprofHeapGraph(Hprof hprof, HprofInMemoryIndex index) {
        this.hprof = hprof;
        this.index = index;
        this.identifierByteSize = hprof.getReader().getIdentifierByteSize();
        this.context = new GraphContext();
        this.objectCache = new LruCache<>(3000);
    }




    @Override
    public Stream<HeapInstance> instances() {
        return index.indexedInstanceSequence().stream()
                .map(new Function<IndexedObject, HeapInstance>() {

                });
    }

    @Override
    public HeapObject findObjectById(long objectId) {
        return wrapIndexedObject(index.indexedObject(objectId), objectId);
    }

    @Override
    public HeapClass findClassByName(String className) {
        long classId = index.classId(className);
        if (classId == -1) {
            return null;
        } else {
            return (HeapClass) findObjectById(classId);
        }
    }

    @Override
    public boolean objectExists(long objectId) {
        return index.objectIdIsIndexed(objectId);
    }

    private String fieldName(FieldRecord fieldRecord) {
        return index.hprofStringById(fieldRecord.nameStringId);
    }

    private String staticFieldName(StaticFieldRecord fieldRecord) {
        return index.hprofStringById(fieldRecord.nameStringId);
    }

    private FieldValuesReader createFieldValuesReader(InstanceDumpRecord record) {
        Buffer buffer = new Buffer();
        buffer.write(record.fieldValues);

        HprofReader reader = new HprofReader(buffer, identifierByteSize);

        return new FieldValuesReader() {
            @Override
            public ValueHolder readValue(FieldRecord field) {
                return reader.readValue(field.type);
            }
        };
    }

    private String className(long classId) {
        return index.className(classId);
    }

        internal ObjectArrayDumpRecord readObjectArrayDumpRecord(
            long objectId, IndexedObjectArray indexedObject) {
        return readObjectRecord(objectId, indexedObject,
                () -> hprof.reader.readObjectArrayDumpRecord());
    }

    internal PrimitiveArrayDumpRecord readPrimitiveArrayDumpRecord(
            long objectId, IndexedPrimitiveArray indexedObject) {
        return readObjectRecord(objectId, indexedObject,
                () -> hprof.reader.readPrimitiveArrayDumpRecord());
    }

    internal ClassDumpRecord readClassDumpRecord(
            long objectId, IndexedClass indexedClass) {
        return readObjectRecord(objectId, indexedClass,
                () -> hprof.reader.readClassDumpRecord());
    }

    internal InstanceDumpRecord readInstanceDumpRecord(
            long objectId, IndexedInstance indexedInstance) {
        return readObjectRecord(objectId, indexedInstance,
                () -> hprof.reader.readInstanceDumpRecord());
    }

    private <T extends ObjectRecord> T readObjectRecord(
            long objectId, IndexedObject indexedObject, Function<Buffer, T> readBlock) {
        ObjectRecord objectRecordOrNull = objectCache.get(objectId);
        if (objectRecordOrNull != null) {
            return (T) objectRecordOrNull;
        }
        hprof.moveReaderTo(indexedObject.getPosition());
        return readBlock.apply(hprof.getReader());
    }

    private HeapObject wrapIndexedObject(IndexedObject indexedObject, long objectId) {
        if (indexedObject instanceof IndexedClass) {
            return new HeapClass(this, (IndexedClass) indexedObject, objectId);
        } else if (indexedObject instanceof IndexedInstance) {
            boolean isPrimitiveWrapper = index.primitiveWrapperTypes.contains(indexedObject.getClassId());
            return new HeapInstance(this, (IndexedInstance) indexedObject, objectId, isPrimitiveWrapper);
        } else if (indexedObject instanceof IndexedObjectArray) {
            boolean isPrimitiveWrapperArray = index.primitiveWrapperTypes.contains(indexedObject.getArrayClassId());
            return new HeapObjectArray(this, (IndexedObjectArray) indexedObject, objectId, isPrimitiveWrapperArray);
        } else if (indexedObject instanceof IndexedPrimitiveArray) {
            return new HeapPrimitiveArray(this, (IndexedPrimitiveArray) indexedObject, objectId);
        }
        return null;
    }

    public static HprofHeapGraph indexHprof(Hprof hprof) {
        HprofInMemoryIndex index = HprofInMemoryIndex.createReadingHprof(hprof.getReader());
        return new HprofHeapGraph(hprof, index);
    }
}