
package shark;

import shark.HprofRecord.HeapDumpRecord.ObjectRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassDumpRecord;
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
import shark.PrimitiveType.BOOLEAN;
import shark.PrimitiveType.BYTE;
import shark.PrimitiveType.CHAR;
import shark.PrimitiveType.DOUBLE;
import shark.PrimitiveType.FLOAT;
import shark.PrimitiveType.INT;
import shark.PrimitiveType.LONG;
import shark.PrimitiveType.SHORT;
import shark.ValueHolder.ReferenceHolder;
import shark.internal.IndexedObject.IndexedClass;
import shark.internal.IndexedObject.IndexedInstance;
import shark.internal.IndexedObject.IndexedObjectArray;
import shark.internal.IndexedObject.IndexedPrimitiveArray;
import java.nio.charset.Charset;
import kotlin.reflect.KClass;

public abstract class HeapObject {

    public final HeapGraph graph;
    public final long objectId;

    public HeapObject(HeapGraph graph, long objectId) {
        this.graph = graph;
        this.objectId = objectId;
    }

    public abstract ObjectRecord readRecord();

    public HeapClass asClass() {
        return (this instanceof HeapClass) ? (HeapClass) this : null;
    }

    public HeapInstance asInstance() {
        return (this instanceof HeapInstance) ? (HeapInstance) this : null;
    }

    public HeapObjectArray asObjectArray() {
        return (this instanceof HeapObjectArray) ? (HeapObjectArray) this : null;
    }

    public HeapPrimitiveArray asPrimitiveArray() {
        return (this instanceof HeapPrimitiveArray) ? (HeapPrimitiveArray) this : null;
    }
    public class HeapClass extends HeapObject {
    private final HprofHeapGraph hprofGraph;
    private final IndexedClass indexedObject;
    private final long objectId;

    public HeapClass(HprofHeapGraph hprofGraph, IndexedClass indexedObject, long objectId) {
        this.hprofGraph = hprofGraph;
        this.indexedObject = indexedObject;
        this.objectId = objectId;
    }

    @Override
    public HeapGraph graph() {
        return hprofGraph;
    }

    public String name() {
        return hprofGraph.className(objectId);
    }

    public String simpleName() {
        String name = name();
        int separator = name.lastIndexOf('.');
        return (separator == -1) ? name : name.substring(separator + 1);
    }

    public int instanceByteSize() {
        return indexedObject.instanceSize;
    }

    public int readFieldsByteSize() {
        int totalByteSize = 0;
        for (FieldRecord field : readRecord().fields) {
            if (field.type == PrimitiveType.REFERENCE_HPROF_TYPE) {
                totalByteSize += hprofGraph.identifierByteSize();
            } else {
                totalByteSize += PrimitiveType.byteSizeByHprofType().getOrDefault(field.type, 0);
            }
        }
        return totalByteSize;
    }

    public HeapClass superclass() {
        if (indexedObject.superclassId == ValueHolder.NULL_REFERENCE) {
            return null;
        } else {
            return (HeapClass) hprofGraph.findObjectById(indexedObject.superclassId);
        }
    }

    public Iterator<HeapClass> classHierarchy() {
        return new Iterator<HeapClass>() {
            private HeapClass current = HeapClass.this;

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public HeapClass next() {
                if (current == null) {
                    throw new NoSuchElementException();
                }
                HeapClass next = current;
                current = current.superclass();
                return next;
            }
        };
    }

    public Iterator<HeapClass> subclasses() {
        return hprofGraph.classes().stream()
                .filter(cls -> cls.subclassOf(HeapClass.this))
                .iterator();
    }

    public boolean superclassOf(HeapClass subclass) {
        return subclass.classHierarchy().anyMatch(cls -> cls.objectId() == objectId);
    }

    public boolean subclassOf(HeapClass superclass) {
        return classHierarchy().anyMatch(cls -> cls.objectId() == superclass.objectId());
    }

    public Iterator<HeapInstance> instances() {
        return hprofGraph.instances().stream()
                .filter(inst -> inst.instanceOf(HeapClass.this))
                .iterator();
    }

    public Iterator<HeapInstance> directInstances() {
        return hprofGraph.instances().stream()
                .filter(inst -> inst.indexedObject().classId() == objectId)
                .iterator();
    }

    @Override
    public ClassDumpRecord readRecord() {
        return hprofGraph.readClassDumpRecord(objectId, indexedObject);
    }

    public Iterator<HeapClassField> readStaticFields() {
        return readRecord().staticFields().stream()
                .map(fieldRecord -> new HeapClassField(
                        this, hprofGraph.staticFieldName(fieldRecord),
                        new HeapValue(hprofGraph, fieldRecord.value())
                ))
                .iterator();
    }

    public HeapClassField readStaticField(String fieldName) {
        for (FieldRecord fieldRecord : readRecord().staticFields()) {
            if (hprofGraph.staticFieldName(fieldRecord).equals(fieldName)) {
                return new HeapClassField(
                        this, hprofGraph.staticFieldName(fieldRecord),
                        new HeapValue(hprofGraph, fieldRecord.value())
                );
            }
        }
        return null;
    }

    
    public HeapClassField get(String fieldName) {
        return readStaticField(fieldName);
    }

    @Override
    public String toString() {
        return "class " + name();
    }
}

    public class HeapInstance extends HeapObject {

    private final HprofHeapGraph hprofGraph;
    private final IndexedInstance indexedObject;
    private final long objectId;
    private final boolean isPrimitiveWrapper;

    public HeapInstance(HprofHeapGraph hprofGraph, IndexedInstance indexedObject, long objectId, boolean isPrimitiveWrapper) {
        this.hprofGraph = hprofGraph;
        this.indexedObject = indexedObject;
        this.objectId = objectId;
        this.isPrimitiveWrapper = isPrimitiveWrapper;
    }

    @Override
    public HeapGraph graph() {
        return hprofGraph;
    }

    @Override
    public long objectId() {
        return objectId;
    }

    @Override
    public ObjectRecord readRecord() {
        return hprofGraph.readInstanceDumpRecord(objectId, indexedObject);
    }

    public boolean instanceOf(String className) {
        return instanceClass().classHierarchy().anyMatch(cls -> cls.name().equals(className));
    }

    public boolean instanceOf(Class<?> expectedClass) {
        return instanceOf(expectedClass.getName());
    }

    public boolean instanceOf(HeapClass expectedClass) {
        return instanceClass().classHierarchy().anyMatch(cls -> cls.objectId() == expectedClass.objectId());
    }

    public HeapClassField readField(Class<?> declaringClass, String fieldName) {
        return readField(declaringClass.getName(), fieldName);
    }

    public HeapClassField readField(String declaringClassName, String fieldName) {
        return readFields().filter(field -> field.declaringClass().name().equals(declaringClassName) &&
                field.name().equals(fieldName)).findFirst().orElse(null);
    }

    public HeapClassField get(Class<?> declaringClass, String fieldName) {
        return readField(declaringClass, fieldName);
    }

    public HeapClassField get(String declaringClassName, String fieldName) {
        return readField(declaringClassName, fieldName);
    }

    public Stream<HeapClassField> readFields() {
        FieldValuesReader fieldReader = hprofGraph.createFieldValuesReader(readRecord());
        return instanceClass().classHierarchy().flatMap(heapClass ->
                heapClass.readRecord().fields().stream()
                        .map(fieldRecord -> new HeapClassField(heapClass, hprofGraph.fieldName(fieldRecord),
                                new HeapValue(hprofGraph, fieldReader.readValue(fieldRecord)))));
    }

public String readAsJavaString() {
    if (!instanceClassName().equals("java.lang.String")) {
        return null;
    }

    Integer count = get("java.lang.String", "count").value().getAsInt();
    if (count == 0) {
        return "";
    }

    Object valueRecord = get("java.lang.String", "value").value().asObject().readRecord();
    if (valueRecord instanceof CharArrayDump) {
        Integer offset = get("java.lang.String", "offset").value().getAsInt();
        CharArrayDump charArrayDump = (CharArrayDump) valueRecord;
        char[] chars = count != null && offset != null ?
                Arrays.copyOfRange(charArrayDump.array, offset, Math.min(offset + count, charArrayDump.array.length)) :
                charArrayDump.array;
        return new String(chars);
    } else if (valueRecord instanceof ByteArrayDump) {
        ByteArrayDump byteArrayDump = (ByteArrayDump) valueRecord;
        return new String(byteArrayDump.array, Charset.forName("UTF-8"));
    } else {
        throw new UnsupportedOperationException("'value' field " + get("java.lang.String", "value").value() +
                " was expected to be either a char or byte array in string instance with id " + objectId);
    }
}

    @Override
    public String toString() {
        return "instance @" + objectId + " of " + instanceClassName();
    }
}

    public class HeapObjectArray extends HeapObject {

    private final HprofHeapGraph hprofGraph;
    private final IndexedObjectArray indexedObject;
    private final long objectId;
    private final boolean isPrimitiveWrapperArray;

    public HeapObjectArray(HprofHeapGraph hprofGraph, IndexedObjectArray indexedObject, long objectId, boolean isPrimitiveWrapperArray) {
        this.hprofGraph = hprofGraph;
        this.indexedObject = indexedObject;
        this.objectId = objectId;
        this.isPrimitiveWrapperArray = isPrimitiveWrapperArray;
    }

    @Override
    public HeapGraph graph() {
        return hprofGraph;
    }

    @Override
    public long objectId() {
        return objectId;
    }

    @Override
    public ObjectArrayDumpRecord readRecord() {
        return hprofGraph.readObjectArrayDumpRecord(objectId, indexedObject);
    }

    public int readByteSize() {
        return readRecord().elementIds().size() * hprofGraph.identifierByteSize();
    }

    public Stream<HeapValue> readElements() {
        return readRecord().elementIds().stream()
                .map(it -> new HeapValue(hprofGraph, new ReferenceHolder(it)));
    }

    @Override
    public String toString() {
        return "object array @" + objectId + " of " + arrayClassName();
    }
}

    public static class HeapPrimitiveArray extends HeapObject {

        private final HprofHeapGraph hprofGraph;
        private final IndexedPrimitiveArray indexedObject;
        private final long objectId;

        public HeapPrimitiveArray(HprofHeapGraph hprofGraph, IndexedPrimitiveArray indexedObject, long objectId) {
            this.hprofGraph = hprofGraph;
            this.indexedObject = indexedObject;
            this.objectId = objectId;
        }

        @Override
        public HeapGraph graph() {
            return hprofGraph;
        }

        @Override
        public long objectId() {
            return objectId;
        }

        @Override
        public PrimitiveArrayDumpRecord readRecord() {
            return hprofGraph.readPrimitiveArrayDumpRecord(objectId, indexedObject);
        }

public int readByteSize() {
    PrimitiveArrayDumpRecord record = readRecord();
    if (record instanceof BooleanArrayDump) {
        BooleanArrayDump booleanArrayDump = (BooleanArrayDump) record;
        return booleanArrayDump.array.length * PrimitiveType.BOOLEAN.byteSize;
    } else if (record instanceof CharArrayDump) {
        CharArrayDump charArrayDump = (CharArrayDump) record;
        return charArrayDump.array.length * PrimitiveType.CHAR.byteSize;
    } else if (record instanceof FloatArrayDump) {
        FloatArrayDump floatArrayDump = (FloatArrayDump) record;
        return floatArrayDump.array.length * PrimitiveType.FLOAT.byteSize;
    } else if (record instanceof DoubleArrayDump) {
        DoubleArrayDump doubleArrayDump = (DoubleArrayDump) record;
        return doubleArrayDump.array.length * PrimitiveType.DOUBLE.byteSize;
    } else if (record instanceof ByteArrayDump) {
        ByteArrayDump byteArrayDump = (ByteArrayDump) record;
        return byteArrayDump.array.length * PrimitiveType.BYTE.byteSize;
    } else if (record instanceof ShortArrayDump) {
        ShortArrayDump shortArrayDump = (ShortArrayDump) record;
        return shortArrayDump.array.length * PrimitiveType.SHORT.byteSize;
    } else if (record instanceof IntArrayDump) {
        IntArrayDump intArrayDump = (IntArrayDump) record;
        return intArrayDump.array.length * PrimitiveType.INT.byteSize;
    } else if (record instanceof LongArrayDump) {
        LongArrayDump longArrayDump = (LongArrayDump) record;
        return longArrayDump.array.length * PrimitiveType.LONG.byteSize;
    } else {
        return 0;
    }
}

        public PrimitiveType primitiveType() {
            return indexedObject.primitiveType;
        }

        public String arrayClassName() {
            switch (getPrimitiveType()) {
                case BOOLEAN:
                    return "boolean[]";
                case CHAR:
                    return "char[]";
                case FLOAT:
                    return "float[]";
                case DOUBLE:
                    return "double[]";
                case BYTE:
                    return "byte[]";
                case SHORT:
                    return "short[]";
                case INT:
                    return "int[]";
                case LONG:
                    return "long[]";
            }
            return "";
        }

        @Override
        public String toString() {
            return "primitive array @" + objectId + " of " + getArrayClassName();
        }
    }

    private static String classSimpleName(String className) {
        int separator = className.lastIndexOf('.');
        return separator == -1 ? className : className.substring(separator + 1);
    }
}