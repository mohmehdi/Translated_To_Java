

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
import shark.PrimitiveType;
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

public abstract class HeapObject {

    public abstract HeapGraph graph;

    public abstract long objectId;

    public abstract ObjectRecord readRecord();

    public HeapClass asClass() {
        if (this instanceof HeapClass) {
            return (HeapClass) this;
        }
        return null;
    }

    public HeapInstance asInstance() {
        if (this instanceof HeapInstance) {
            return (HeapInstance) this;
        }
        return null;
    }

    public HeapObjectArray asObjectArray() {
        if (this instanceof HeapObjectArray) {
            return (HeapObjectArray) this;
        }
        return null;
    }

    public HeapPrimitiveArray asPrimitiveArray() {
        if (this instanceof HeapPrimitiveArray) {
            return (HeapPrimitiveArray) this;
        }
        return null;
    }

    public static class HeapClass extends HeapObject {

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
            return classSimpleName(name());
        }

        public int instanceByteSize() {
            return indexedObject.instanceSize;
        }

        public int readFieldsByteSize() {
            return readRecord()
                    .fields.stream()
                    .mapToInt(field -> {
                        if (field.type == PrimitiveType.REFERENCE_HPROF_TYPE) {
                            return hprofGraph.identifierByteSize;
                        }
                        return PrimitiveType.byteSizeByHprofType.get(field.type);
                    })
                    .sum();
        }

        public HeapClass superclass() {
            if (indexedObject.superclassId == ValueHolder.NULL_REFERENCE) {
                return null;
            }
            return hprofGraph.findObjectById(indexedObject.superclassId).asClass();
        }

        public Sequence<HeapClass> classHierarchy() {
            return GeneratedSequence.sequence(this, this::superclass);
        }

        public Sequence<HeapClass> subclasses() {
            return hprofGraph.classes.stream()
                    .filter(heapClass -> heapClass.subclassOf(this));
        }

        public boolean subclassOf(HeapClass superclass) {
            return classHierarchy().anyMatch(heapClass -> heapClass.objectId == superclass.objectId);
        }

        public boolean superclassOf(HeapClass subclass) {
            return subclass.classHierarchy().anyMatch(heapClass -> heapClass.objectId == objectId);
        }

        public Sequence<HeapInstance> instances() {
            return hprofGraph.instances.stream()
                    .filter(heapInstance -> heapInstance.instanceClass.objectId == objectId);
        }

        public Sequence<HeapInstance> directInstances() {
            return hprofGraph.instances.stream()
                    .filter(heapInstance -> heapInstance.indexedObject.classId == objectId);
        }

        @Override
        public ClassDumpRecord readRecord() {
            return hprofGraph.readClassDumpRecord(objectId, indexedObject);
        }

        public Sequence<HeapClassField> readStaticFields() {
            return readRecord()
                    .staticFields.stream()
                    .map(fieldRecord -> {
                        String fieldName = hprofGraph.staticFieldName(fieldRecord);
                        HeapValue fieldValue = new HeapValue(hprofGraph, fieldRecord.value);
                        return new HeapClassField(
                                this, hprofGraph.staticFieldName(fieldRecord),
                                fieldValue);
                    })
                    .sequential();
        }

        public HeapClassField readStaticField(String fieldName) {
            for (ObjectRecord fieldRecord : readRecord().fields) {
                if (hprofGraph.staticFieldName(fieldRecord).equals(fieldName)) {
                    return new HeapClassField(
                            this, hprofGraph.staticFieldName(fieldRecord),
                            new HeapValue(hprofGraph, fieldRecord.value));
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

    public static class HeapInstance extends HeapObject {

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

        public int byteSize() {
            return instanceClass.instanceByteSize;
        }

        public String instanceClassName() {
            return hprofGraph.className(indexedObject.classId);
        }

        public String instanceClassSimpleName() {
            return classSimpleName(instanceClassName());
        }

        public HeapClass instanceClass() {
            return hprofGraph.findObjectById(indexedObject.classId).asClass();
        }

        @Override
        public InstanceDumpRecord readRecord() {
            return hprofGraph.readInstanceDumpRecord(objectId, indexedObject);
        }

        public boolean instanceOf(String className) {
            return instanceClass().classHierarchy().anyMatch(heapClass -> heapClass.name().equals(className));
        }

        public <T extends Object> boolean instanceOf(KClass<T> expectedClass) {
            return instanceOf(expectedClass.java);
        }

        public boolean instanceOf(HeapClass expectedClass) {
            return instanceClass.classHierarchy.stream()
                    .anyMatch(it -> it.objectId == expectedClass.objectId);
        }



        public HeapClassField readField(String declaringClassName, String fieldName) {
            return hprofGraph.createFieldValuesReader(readRecord())
                    .readField(declaringClassName, fieldName);
        }

        public <T extends Object> HeapClassField readField(KClass<T> declaringClass, String fieldName) {
            return readField(declaringClass.java.getName(), fieldName);
        }

        
        public HeapClassField get(KClass<out Any> declaringClass, String fieldName) {
            return readField(declaringClass, fieldName);
        }

        public HeapClassField get(String declaringClassName, String fieldName) {
            return readField(declaringClassName, fieldName);
        }

        public Sequence<HeapClassField> readFields() {
            return hprofGraph.createFieldValuesReader(readRecord())
                    .fields(this)
                    .stream()
                    .map(field -> {
                        String fieldName = hprofGraph.fieldName(field);
                        HeapValue fieldValue = hprofGraph.readFieldValue(field);
                        return new HeapClassField(
                                this, fieldName,
                                new HeapValue(hprofGraph, fieldValue));
                    })
                    .sequential();
        }

        public String readAsJavaString() {
            ObjectNode thisObj = (ObjectNode) this;
            if (!"java.lang.String".equals(instanceClassName)) {
                return null;
            }

            IntNode count = (IntNode) thisObj.get("java.lang.String").get("count");
            if (count.value() == 0) {
                return "";
            }

            JsonNode valueNode = thisObj.get("java.lang.String").get("value");
            ObjectNode valueRecord;
            if (valueNode.isObject()) {
                valueRecord = (ObjectNode) valueNode;
            } else {
                throw new UnsupportedOperationException(
                "'value' field " + valueNode + " was expected to be an object in string instance with id " + objectId);
            }

            if (valueRecord.has("charArrayDump")) {
                IntNode offset = (IntNode) thisObj.get("java.lang.String").get("offset");
                ArrayNode charArrayDump = (ArrayNode) valueRecord.get("charArrayDump");
                int[] chars;
                if (count != null && offset != null) {
                int toIndex = Math.min(offset.value() + count.value(), charArrayDump.size());
                chars = new int[toIndex - offset.value()];
                for (int i = offset.value(), j = 0; i < toIndex; i++, j++) {
                    chars[j] = charArrayDump.get(i).intValue();
                }
                } else {
                chars = new int[charArrayDump.size()];
                for (int i = 0; i < charArrayDump.size(); i++) {
                    chars[i] = charArrayDump.get(i).intValue();
                }
                }
                return new String(chars, 0, chars.length);
            } else if (valueRecord.has("byteArrayDump")) {
                ArrayNode byteArrayDump = (ArrayNode) valueRecord.get("byteArrayDump");
                byte[] bytes = new byte[byteArrayDump.size()];
                for (int i = 0; i < byteArrayDump.size(); i++) {
                bytes[i] = byteArrayDump.get(i).intValue();
                }
                return new String(bytes, StandardCharsets.UTF_8);
            } else {
                throw new UnsupportedOperationException(
                "'value' field " + valueNode + " was expected to be either a char or byte array in string instance with id " + objectId);
            }
        }

        @Override
        public String toString() {
            return "instance @" + objectId + " of " + instanceClassName();
        }
    }

    public static class HeapObjectArray extends HeapObject {

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

        public int readByteSize() {
            return readRecord().elementIds.size() * hprofGraph.identifierByteSize;
        }

        @Override
        public ObjectArrayDumpRecord readRecord() {
            return hprofGraph.readObjectArrayDumpRecord(objectId, indexedObject);
        }

        public Sequence<HeapValue> readElements() {
            return readRecord().elementIds.stream()
                    .map(id -> new HeapValue(hprofGraph, new ReferenceHolder(id)))
                    .sequential();
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

        public int readByteSize() {
            return when (readRecord()) {
                BooleanArrayDump booleanArrayDump -> booleanArrayDump.array.length * PrimitiveType.BOOLEAN.byteSize;
                CharArrayDump charArrayDump -> charArrayDump.array.length * PrimitiveType.CHAR.byteSize;
                FloatArrayDump floatArrayDump -> floatArrayDump.array.length * PrimitiveType.FLOAT.byteSize;
                DoubleArrayDump doubleArrayDump -> doubleArrayDump.array.length * PrimitiveType.DOUBLE.byteSize;
                ByteArrayDump byteArrayDump -> byteArrayDump.array.length * PrimitiveType.BYTE.byteSize;
                ShortArrayDump shortArrayDump -> shortArrayDump.array.length * PrimitiveType.SHORT.byteSize;
                IntArrayDump intArrayDump -> intArrayDump.array.length * PrimitiveType.INT.byteSize;
                LongArrayDump longArrayDump -> longArrayDump.array.length * PrimitiveType.LONG.byteSize;
                default -> throw new UnsupportedOperationException("Unexpected array type");
            };
        }

        @Override
        public PrimitiveArrayDumpRecord readRecord() {
            return hprofGraph.readPrimitiveArrayDumpRecord(objectId, indexedObject);
        }

        public PrimitiveType primitiveType() {
            return indexedObject.primitiveType;
        }

        public String arrayClassName() {
            return switch (primitiveType()) {
                case BOOLEAN -> "boolean[]";
                case CHAR -> "char[]";
                case FLOAT -> "float[]";
                case DOUBLE -> "double[]";
                case BYTE -> "byte[]";
                case SHORT -> "short[]";
                case INT -> "int[]";
                case LONG -> "long[]";
                default -> throw new UnsupportedOperationException("Unexpected primitive type");
            };
        }
    }

            private static String classSimpleName(String className) {
            int separator = className.lastIndexOf('.');
            return Objects.requireNonNullElse(
                    (separator == -1) ? className : className.substring(separator + 1),
                    className
            );
        }
}