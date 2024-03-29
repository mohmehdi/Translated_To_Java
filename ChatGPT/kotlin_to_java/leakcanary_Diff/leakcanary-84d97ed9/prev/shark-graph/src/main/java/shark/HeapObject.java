
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

  public abstract HeapGraph graph();

  public abstract long objectId();

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

    private final HeapGraph graph;
    private final IndexedClass indexedObject;
    private final long objectId;

    public HeapClass(HeapGraph graph, IndexedClass indexedObject, long objectId) {
      this.graph = graph;
      this.indexedObject = indexedObject;
      this.objectId = objectId;
    }

    @Override
    public ClassDumpRecord readRecord() {
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
      return readRecord().fields.stream()
        .mapToInt(it -> it.type == PrimitiveType.REFERENCE_HPROF_TYPE ? graph.objectIdByteSize : PrimitiveType.byteSizeByHprofType.get(it.type))
        .sum();
    }

    public HeapClass superclass() {
      if (indexedObject.superclassId == ValueHolder.NULL_REFERENCE) return null;
      return (HeapClass) graph.findObjectById(indexedObject.superclassId);
    }

    public Sequence < HeapClass > classHierarchy() {
      return Sequence.generate(this, it -> it.superclass());
    }

    public Sequence < HeapClass > subclasses() {
      return graph.classes.stream().filter(it -> it.subclassOf(this));
    }

    public boolean superclassOf(HeapClass subclass) {
      return subclass.classHierarchy().anyMatch(it -> it.objectId == objectId);
    }

    public boolean subclassOf(HeapClass superclass) {
      return classHierarchy().anyMatch(it -> it.objectId == superclass.objectId);
    }

    public Sequence < HeapInstance > instances() {
      return graph.instances.stream().filter(it -> it.instanceOf(this));
    }

    public Sequence < HeapInstance > directInstances() {
      return graph.instances.stream().filter(it -> it.indexedObject.classId == objectId);
    }

    public Sequence < HeapClassField > readStaticFields() {
      return readRecord().staticFields.stream()
        .map(fieldRecord -> new HeapClassField(this, graph.staticFieldName(fieldRecord), new HeapValue(graph, fieldRecord.value)))
        .collect(Collectors.toCollection(Sequence::new));
    }

    public HeapClassField readStaticField(String fieldName) {
      return readRecord().staticFields.stream()
        .filter(fieldRecord -> graph.staticFieldName(fieldRecord).equals(fieldName))
        .findFirst()
        .map(fieldRecord -> new HeapClassField(this, graph.staticFieldName(fieldRecord), new HeapValue(graph, fieldRecord.value)))
        .orElse(null);
    }

    public HeapClassField get(String fieldName) {
      return readStaticField(fieldName);
    }

    @Override
    public String toString() {
      return "record of class " + name();
    }
  }

  public class HeapInstance extends HeapObject {

    public final HeapGraph graph;
    public final IndexedInstance indexedObject;
    public final long objectId;
    public final boolean isPrimitiveWrapper;

    public HeapInstance(HeapGraph graph, IndexedInstance indexedObject, long objectId, boolean isPrimitiveWrapper) {
      this.graph = graph;
      this.indexedObject = indexedObject;
      this.objectId = objectId;
      this.isPrimitiveWrapper = isPrimitiveWrapper;
    }

    public int size() {
      return instanceClass().instanceSize();
    }

    @Override
    public InstanceDumpRecord readRecord() {
      return graph.readInstanceDumpRecord(objectId, indexedObject);
    }

    public boolean instanceOf(String className) {
      return instanceClass().classHierarchy().anyMatch(it -> it.name().equals(className));
    }

    public boolean instanceOf(Class < ? > expectedClass) {
      return instanceOf(expectedClass.getName());
    }

    public boolean instanceOf(HeapClass expectedClass) {
      return instanceClass().classHierarchy().anyMatch(it -> it.objectId == expectedClass.objectId);
    }

    public HeapClassField get(Class < ? > declaringClass, String fieldName) {
      return readField(declaringClass.getName(), fieldName);
    }

    public HeapClassField get(String declaringClassName, String fieldName) {
      return readField(declaringClassName, fieldName);
    }
    
    public HeapClassField readField(KClass<? extends Object> declaringClass, String fieldName) {
        return readField(declaringClass.getJavaClass().getName(), fieldName);
    }

    public HeapClassField readField(String declaringClassName, String fieldName) {
      return readFields().filter(field -> field.declaringClass().name().equals(declaringClassName) && field.name().equals(fieldName))
        .findFirst().orElse(null);
    }

    public String instanceClassName() {
      return graph.className(indexedObject.classId);
    }

    public String instanceClassSimpleName() {
      return classSimpleName(instanceClassName());
    }

    public HeapClass instanceClass() {
      return (HeapClass) graph.findObjectById(indexedObject.classId);
    }

    public Stream < HeapClassField > readFields() {
      FieldValuesReader fieldReader = graph.createFieldValuesReader(readRecord());
      return instanceClass().classHierarchy().flatMap(heapClass ->
        heapClass.readRecord().fields.stream()
        .map(fieldRecord -> {
          String fieldName = graph.fieldName(fieldRecord);
          ValueHolder fieldValue = fieldReader.readValue(fieldRecord);
          return new HeapClassField(heapClass, fieldName, new HeapValue(graph, fieldValue));
        })
      );
    }

    public String readAsJavaString() {
      if (!instanceClassName().equals("java.lang.String")) {
        return null;
      }

      int count = get("java.lang.String", "count").value().asInt();
      if (count == 0) {
        return "";
      }

      ValueHolder valueField = get("java.lang.String", "value").value().asObject();
      if (valueField == null) {
        throw new UnsupportedOperationException("'value' field is expected to be an object");
      }

      InstanceDumpRecord valueRecord = valueField.readRecord();
      if (valueRecord instanceof CharArrayDump) {
        CharArrayDump charArrayRecord = (CharArrayDump) valueRecord;
        int offset = get("java.lang.String", "offset").value().asInt();

        int toIndex = Math.min(offset + count, charArrayRecord.array.length);
        char[] chars = new char[toIndex - offset];
        System.arraycopy(charArrayRecord.array, offset, chars, 0, chars.length);
        return new String(chars);
      } else if (valueRecord instanceof ByteArrayDump) {
        ByteArrayDump byteArrayRecord = (ByteArrayDump) valueRecord;
        return new String(byteArrayRecord.array, Charset.forName("UTF-8"));
      } else {
        throw new UnsupportedOperationException("'value' field is expected to be a char or byte array");
      }
    }

    @Override
    public String toString() {
      return "instance @" + objectId + " of " + instanceClassName();
    }
  }

  public class HeapObjectArray extends HeapObject {

    public final HeapGraph graph;
    public final IndexedObjectArray indexedObject;
    public final long objectId;
    public final boolean isPrimitiveWrapperArray;

    public HeapObjectArray(HeapGraph graph, IndexedObjectArray indexedObject, long objectId, boolean isPrimitiveWrapperArray) {
      this.graph = graph;
      this.indexedObject = indexedObject;
      this.objectId = objectId;
      this.isPrimitiveWrapperArray = isPrimitiveWrapperArray;
    }

    public String arrayClassName() {
      return graph.className(indexedObject.arrayClassId);
    }

    public String arrayClassSimpleName() {
      return classSimpleName(arrayClassName());
    }

    public HeapClass arrayClass() {
      return (HeapClass) graph.findObjectById(indexedObject.arrayClassId);
    }

    public int readSize() {
      return readRecord().elementIds.size() * graph.objectIdByteSize();
    }

    @Override
    public ObjectArrayDumpRecord readRecord() {
      return graph.readObjectArrayDumpRecord(objectId, indexedObject);
    }

    public Stream < HeapValue > readElements() {
      return readRecord().elementIds.stream()
        .map(elementId -> new HeapValue(graph, new ReferenceHolder(elementId)));
    }

    @Override
    public String toString() {
      return "object array @" + objectId + " of " + arrayClassName();
    }
  }

  public class HeapPrimitiveArray extends HeapObject {

    public final HeapGraph graph;
    public final IndexedPrimitiveArray indexedObject;
    public final long objectId;

    public HeapPrimitiveArray(HeapGraph graph, IndexedPrimitiveArray indexedObject, long objectId) {
      this.graph = graph;
      this.indexedObject = indexedObject;
      this.objectId = objectId;
    }

    public int readSize() {
      PrimitiveArrayDumpRecord record = readRecord();
      if (record instanceof BooleanArrayDump) {
        return ((BooleanArrayDump) record).array.length * PrimitiveType.BOOLEAN.byteSize;
      } else if (record instanceof CharArrayDump) {
        return ((CharArrayDump) record).array.length * PrimitiveType.CHAR.byteSize;
      } else if (record instanceof FloatArrayDump) {
        return ((FloatArrayDump) record).array.length * PrimitiveType.FLOAT.byteSize;
      } else if (record instanceof DoubleArrayDump) {
        return ((DoubleArrayDump) record).array.length * PrimitiveType.DOUBLE.byteSize;
      } else if (record instanceof ByteArrayDump) {
        return ((ByteArrayDump) record).array.length * PrimitiveType.BYTE.byteSize;
      } else if (record instanceof ShortArrayDump) {
        return ((ShortArrayDump) record).array.length * PrimitiveType.SHORT.byteSize;
      } else if (record instanceof IntArrayDump) {
        return ((IntArrayDump) record).array.length * PrimitiveType.INT.byteSize;
      } else if (record instanceof LongArrayDump) {
        return ((LongArrayDump) record).array.length * PrimitiveType.LONG.byteSize;
      } else {
        throw new UnsupportedOperationException("Unsupported primitive array type");
      }
    }

    public PrimitiveType primitiveType() {
      return indexedObject.primitiveType;
    }

    public String arrayClassName() {
      PrimitiveType type = primitiveType();
      switch (type) {
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
      default:
        throw new UnsupportedOperationException("Unsupported primitive type: " + type);
      }
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

  public static String classSimpleName(String className) {
    int separator = className.lastIndexOf('.');
    return separator == -1 ? className : className.substring(separator + 1);
  }
}