

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

  protected final HeapGraph graph;
  protected final long objectId;

  public HeapObject(HeapGraph graph, long objectId) {
    this.graph = graph;
    this.objectId = objectId;
  }

  public abstract ObjectRecord readRecord();

  public HeapClass asClass() {
    return this instanceof HeapClass ? (HeapClass) this : null;
  }

  public HeapInstance asInstance() {
    return this instanceof HeapInstance ? (HeapInstance) this : null;
  }

  public HeapObjectArray asObjectArray() {
    return this instanceof HeapObjectArray ? (HeapObjectArray) this : null;
  }

  public HeapPrimitiveArray asPrimitiveArray() {
    return this instanceof HeapPrimitiveArray ? (HeapPrimitiveArray) this : null;
  }

  public static class HeapClass extends HeapObject {

    private final IndexedClass indexedObject;

    public HeapClass(HeapGraph graph, IndexedClass indexedObject, long objectId) {
      super(graph, objectId);
      this.indexedObject = indexedObject;
    }

    @Override
    public ObjectRecord readRecord() {
      return graph.readClassDumpRecord(objectId, indexedObject);
    }

    public String name() {
      return graph.className(objectId);
    }

    public String simpleName() {
      return classSimpleName(name());
    }

    public int instanceSize() {
      return indexedObject.instanceSize;
    }

    public int readFieldsSize() {
      return readRecord()
          .fields.stream()
          .mapToInt(field -> {
            if (field.type == PrimitiveType.REFERENCE_HPROF_TYPE) {
              return graph.objectIdByteSize;
            } else {
              return PrimitiveType.byteSizeByHprofType.get(field.type);
            }
          })
          .sum();
    }

    public HeapClass superclass() {
      if (indexedObject.superclassId == ValueHolder.NULL_REFERENCE) {
        return null;
      }
      return graph.findObjectById(indexedObject.superclassId).asClass();
    }

    public Sequence<HeapClass> classHierarchy() {
      return generateSequence(this, HeapClass::superclass);
    }

    public Sequence<HeapClass> subclasses() {
      return graph.classes.stream()
          .filter(this::subclassOf)
          .map(HeapClass::new);
    }

    public boolean superclassOf(HeapClass subclass) {
      return subclass.classHierarchy().anyMatch(clazz -> clazz.objectId == objectId);
    }

    public boolean subclassOf(HeapClass superclass) {
      return classHierarchy().anyMatch(clazz -> clazz.objectId == superclass.objectId);
    }

    public Sequence<HeapInstance> instances() {
      return graph.instances.stream().filter(instance -> instance.indexedObject.classId == objectId);
    }

    public Sequence<HeapInstance> directInstances() {
      return graph.instances.stream().filter(instance -> instance.classId == objectId);
    }

    public Sequence<HeapClassField> readStaticFields() {
      return readRecord().staticFields.stream()
          .map(fieldRecord -> new HeapClassField(
              this,
              graph.staticFieldName(fieldRecord),
              new HeapValue(graph, fieldRecord.value)
          ));
    }

    public HeapClassField readStaticField(String fieldName) {
      for (fieldRecord : readRecord().staticFields) {
        if (graph.staticFieldName(fieldRecord).equals(fieldName)) {
          return new HeapClassField(
              this,
              graph.staticFieldName(fieldRecord),
              new HeapValue(graph, fieldRecord.value)
          );
        }
      }
      return null;
    }
      public static Object get(String fieldName) {
        try {
            Field field = readStaticField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
    @Override
    public String toString() {
      return "record of class " + name();
    }
  }

  public static class HeapInstance extends HeapObject {

    private final IndexedInstance indexedObject;
    private final boolean isPrimitiveWrapper;

    public HeapInstance(HeapGraph graph, IndexedInstance indexedObject, long objectId, boolean isPrimitiveWrapper) {
      super(graph, objectId);
      this.indexedObject = indexedObject;
      this.isPrimitiveWrapper = isPrimitiveWrapper;
    }

    public int size() {
      return instanceClass().instanceSize();
    }

    @Override
    public ObjectRecord readRecord() {
      return graph.readInstanceDumpRecord(objectId, indexedObject);
    }

    public boolean instanceOf(String className) {
      return instanceClass().classHierarchy().anyMatch(clazz -> clazz.name().equals(className));
    }

    public boolean instanceOf(Class<?> clazz) {
      return instanceOf(clazz.getName());
    }

    public boolean instanceOf(HeapClass expectedClass) {
      return instanceClass().classHierarchy().anyMatch(clazz -> clazz.objectId == expectedClass.objectId);
    }

    public HeapClassField get(Class<?> declaringClass, String fieldName) {
      return readField(declaringClass, fieldName);
    }

    public HeapClassField get(String declaringClassName, String fieldName) {
      return readField(declaringClassName, fieldName);
    }

    public HeapClassField readField(Class<?> declaringClass, String fieldName) {
      return readField(declaringClass.getName(), fieldName);
    }

    public HeapClassField readField(String declaringClassName, String fieldName) {
      return readFields().stream()
          .filter(field -> field.declaringClass.name().equals(declaringClassName) && field.name.equals(fieldName))
          .findFirst()
          .orElse(null);
    }

    public String instanceClassName() {
      return graph.className(indexedObject.classId);
    }

    public String instanceClassSimpleName() {
      return classSimpleName(instanceClassName());
    }

    public HeapClass instanceClass() {
      return graph.findObjectById(indexedObject.classId).asClass();
    }

    public Sequence<HeapClassField> readFields() {
      return instanceClass().classHierarchy()
          .map(HeapClass::readRecord)
          .flatMap(heapClass -> {
            AtomicReference<HeapClassField.FieldReader> fieldReader = new AtomicReference<>();
            return heapClass.fields.stream()
                .map(fieldRecord -> {
                  String fieldName = graph.fieldName(fieldRecord);
                  HeapValue fieldValue = fieldReader.get().readValue(fieldRecord);
                  return new HeapClassField(heapClass, fieldName, new HeapValue(graph, fieldValue));
                });
          });
    }

    public String readAsJavaString() {
      if (!instanceClassName().equals("java.lang.String")) {
        return null;
      }

      HeapClassField countField = get("java.lang.String", "count");
      if (countField.value.asInt() == 0) {
        return "";
      }

      HeapClassField valueField = get("java.lang.String", "value");
      PrimitiveArrayDumpRecord valueRecord = (PrimitiveArrayDumpRecord) valueField.value.asObject().readRecord();

      switch (valueRecord.getClass().getSimpleName()) {
        case "CharArrayDump":
          CharArrayDump charArrayDump = (CharArrayDump) valueRecord;
          int offset = get("java.lang.String", "offset").value.asInt();
          int count = countField.value.asInt();
          char[] chars = new char[count];
          for (int i = 0; i < count; i++) {
            chars[i] = charArrayDump.array[offset + i];
          }
          return new String(chars);
        case "ByteArrayDump":
          ByteArrayDump byteArrayDump = (ByteArrayDump) valueRecord;
          return new String(byteArrayDump.array, Charset.forName("UTF-8"));
        default:
          throw new UnsupportedOperationException(
              "'value' field " + valueField + " was expected to be either a char or byte array in string instance with id " + objectId);
      }
    }

    @Override
    public String toString() {
      return "instance @" + objectId + " of " + instanceClassName();
    }
  }

  public static class HeapObjectArray extends HeapObject {

    private final IndexedObjectArray indexedObject;

    public HeapObjectArray(HeapGraph graph, IndexedObjectArray indexedObject, long objectId) {
      super(graph, objectId);
      this.indexedObject = indexedObject;
    }

    public String arrayClassName() {
      return graph.className(indexedObject.arrayClassId);
    }

    public String arrayClassSimpleName() {
      return classSimpleName(arrayClassName());
    }

    public HeapClass arrayClass() {
      return graph.findObjectById(indexedObject.arrayClassId).asClass();
    }

    public int readSize() {
      return readRecord().elementIds.size() * graph.objectIdByteSize;
    }

    @Override
    public ObjectArrayDumpRecord readRecord() {
      return graph.readObjectArrayDumpRecord(objectId, indexedObject);
    }

    public Sequence<HeapValue> readElements() {
      return readRecord().elementIds.stream()
          .map(id -> new HeapValue(graph, new ReferenceHolder(id)));
    }

    @Override
    public String toString() {
      return "object array @" + objectId + " of " + arrayClassName();
    }
  }

  public static class HeapPrimitiveArray extends HeapObject {

    private final IndexedPrimitiveArray indexedObject;

    public HeapPrimitiveArray(HeapGraph graph, IndexedPrimitiveArray indexedObject, long objectId) {
      super(graph, objectId);
      this.indexedObject = indexedObject;
    }

    public int readSize() {
      PrimitiveArrayDumpRecord record = readRecord();
      switch (record.getClass().getSimpleName()) {
        case "BooleanArrayDump":
          return ((BooleanArrayDump) record).array.length * PrimitiveType.BOOLEAN.byteSize;
        case "CharArrayDump":
          return ((CharArrayDump) record).array.length * PrimitiveType.CHAR.byteSize;
        case "FloatArrayDump":
          return ((FloatArrayDump) record).array.length * PrimitiveType.FLOAT.byteSize;
        case "DoubleArrayDump":
          return ((DoubleArrayDump) record).array.length * PrimitiveType.DOUBLE.byteSize;
        case "ByteArrayDump":
          return ((ByteArrayDump) record).array.length * PrimitiveType.BYTE.byteSize;
        case "ShortArrayDump":
          return ((ShortArrayDump) record).array.length * PrimitiveType.SHORT.byteSize;
        case "IntArrayDump":
          return ((IntArrayDump) record).array.length * PrimitiveType.INT.byteSize;
        case "LongArrayDump":
          return ((LongArrayDump) record).array.length * PrimitiveType.LONG.byteSize;
        default:
          throw new UnsupportedOperationException("Unexpected primitive array type: " + record.getClass().getSimpleName());
      }
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
      };
    }

    @Override
    public PrimitiveArrayDumpRecord readRecord() {
      return graph.readPrimitiveArrayDumpRecord(objectId, indexedObject);
    }

    @Override
    public String toString() {
      return "primitive array @" + objectId + " of " + arrayClassName();
    }
  }

  private static String classSimpleName(String className) {
    int separator = className.lastIndexOf('.');
    return separator == -1 ? className : className.substring(separator + 1);
  }
}