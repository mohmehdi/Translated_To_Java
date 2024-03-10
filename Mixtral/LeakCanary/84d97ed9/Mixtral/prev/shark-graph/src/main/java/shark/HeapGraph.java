

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

public class HeapGraph {

    private final Hprof hprof;
    private final HprofInMemoryIndex index;
    private final int objectIdByteSize;
    private final GraphContext context = new GraphContext();

    public HeapGraph(Hprof hprof, HprofInMemoryIndex index) {
        this.hprof = hprof;
        this.index = index;
        this.objectIdByteSize = hprof.getReader().getObjectIdByteSize();
    }

    private final LruCache<Long, ObjectRecord> objectCache = new LruCache<>(3000);

    public HeapObject findObjectById(long objectId) {
        return wrapIndexedObject(index.indexedObject(objectId), objectId);
    }

    public HeapClass findClassByName(String className) {
        long classId = index.classId(className);
        if (classId == -1) {
            return null;
        }
        return (HeapClass) findObjectById(classId);
    }

    public boolean objectExists(long objectId) {
        return index.objectIdIsIndexed(objectId);
    }

    private String fieldName(FieldRecord fieldRecord) {
        return index.hprofStringById(fieldRecord.getNameStringId());
    }

    private String staticFieldName(StaticFieldRecord fieldRecord) {
        return index.hprofStringById(fieldRecord.getNameStringId());
    }

    private FieldValuesReader createFieldValuesReader(InstanceDumpRecord record) {
        Buffer buffer = new Buffer();
        buffer.write(record.getFieldValues());

        HprofReader reader = new HprofReader(buffer, 0, objectIdByteSize);

        return new FieldValuesReader() {
            @Override
            public ValueHolder readValue(FieldRecord field) {
                return reader.readValue(field.getType());
            }
        };
    }

    private String className(long classId) {
        return index.className(classId);
    }

    private ObjectArrayDumpRecord readObjectArrayDumpRecord(
            long objectId, IndexedObjectArray indexedObject) {
        return readObjectRecord(objectId, indexedObject,
                () -> hprof.getReader().readObjectArrayDumpRecord());
    }

    private PrimitiveArrayDumpRecord readPrimitiveArrayDumpRecord(
            long objectId, IndexedPrimitiveArray indexedObject) {
        return readObjectRecord(objectId, indexedObject,
                () -> hprof.getReader().readPrimitiveArrayDumpRecord());
    }

    private ClassDumpRecord readClassDumpRecord(
            long objectId, IndexedClass indexedObject) {
        return readObjectRecord(objectId, indexedObject,
                () -> hprof.getReader().readClassDumpRecord());
    }

    private InstanceDumpRecord readInstanceDumpRecord(
            long objectId, IndexedInstance indexedObject) {
        return readObjectRecord(objectId, indexedObject,
                () -> hprof.getReader().readInstanceDumpRecord());
    }

    private <T extends ObjectRecord> T readObjectRecord(
            long objectId, IndexedObject indexedObject,
            Function<HprofReader, T> readBlock) {
        ObjectRecord objectRecordOrNull = objectCache.get(objectId);
        if (objectRecordOrNull != null) {
            return (T) objectRecordOrNull;
        }
        hprof.getReader().moveTo(indexedObject.getPosition());
        T objectRecord = readBlock.apply(new HprofReader(hprof.getReader(), 0, objectIdByteSize));
        objectCache.put(objectId, objectRecord);
        return objectRecord;
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

    public static HeapGraph indexHprof(Hprof hprof) {
        HprofInMemoryIndex index = HprofInMemoryIndex.createReadingHprof(hprof.getReader());
        return new HeapGraph(hprof, index);
    }
}