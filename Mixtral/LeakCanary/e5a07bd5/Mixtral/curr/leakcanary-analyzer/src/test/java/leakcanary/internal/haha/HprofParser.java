package leakcanary.internal.haha;

import leakcanary.internal.haha.GcRoot.Debugger;
import leakcanary.internal.haha.GcRoot.Finalizing;
import leakcanary.internal.haha.GcRoot.InternedString;
import leakcanary.internal.haha.GcRoot.JavaFrame;
import leakcanary.internal.haha.GcRoot.JniGlobal;
import leakcanary.internal.haha.GcRoot.JniLocal;
import leakcanary.internal.haha.GcRoot.JniMonitor;
import leakcanary.internal.haha.GcRoot.MonitorUsed;
import leakcanary.internal.haha.GcRoot.NativeStack;
import leakcanary.internal.haha.GcRoot.ReferenceCleanup;
import leakcanary.internal.haha.GcRoot.StickyClass;
import leakcanary.internal.haha.GcRoot.ThreadBlock;
import leakcanary.internal.haha.GcRoot.ThreadObject;
import leakcanary.internal.haha.GcRoot.Unknown;
import leakcanary.internal.haha.GcRoot.Unreachable;
import leakcanary.internal.haha.GcRoot.VmInternal;
import leakcanary.internal.haha.HeapValue.IntValue;
import leakcanary.internal.haha.HeapValue.ObjectReference;
import leakcanary.internal.haha.HprofReader;
import leakcanary.internal.haha.HprofReader.Companion;
import leakcanary.internal.haha.Record.HeapDumpRecord.GcRootRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.HeapDumpInfoRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.ClassDumpRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.InstanceDumpRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.ObjectArrayDumpRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.ByteArrayDump;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.CharArrayDump;
import leakcanary.internal.haha.Record.LoadClassRecord;
import leakcanary.internal.haha.Record.StringRecord;
import okio.Buffer;
import okio.BufferedSource;
import okio.Source;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class HprofParser implements Closeable {

  private SeekableHprofReader reader;
  private boolean scanning;
  private boolean indexBuilt;
  private final Map < Long, Pair < Long, Long >> hprofStringPositions = new HashMap < > ();
  private final Map < Long, Long > classNames = new HashMap < > ();
  private final Map < Long, Long > objectPositions = new HashMap < > ();

  public HprofParser(SeekableHprofReader reader) {
    this.reader = reader;
  }

  public static HprofParser open(File heapDump) throws IOException {
    InputStream inputStream = new FileInputStream(heapDump);
    FileChannel channel = inputStream.getChannel();
    BufferedSource source = Okio.buffer(source(channel));
    int endOfVersionString = source.indexOf((byte) 0);
    source.skip(endOfVersionString + 1);
    int idSize = source.readInt();
    long startPosition = endOfVersionString + 1 + 4;

    SeekableHprofReader hprofReader = new SeekableHprofReader(channel, source, startPosition, idSize);
    return new HprofParser(hprofReader);
  }

  public class RecordCallbacks {
    private final Map < Class < ? extends Record > , Object > callbacks = new HashMap < > ();

    public < T extends Record > RecordCallbacks on(
      Class < T > recordClass,
      Callback < T > callback) {
      callbacks.put(recordClass, callback);
      return this;
    }

    @SuppressWarnings("unchecked")
    public < T extends Record > Callback < T > get(Class < T > recordClass) {
      return (Callback < T > ) callbacks.get(recordClass);
    }

    @SuppressWarnings("unchecked")
    public < T extends Record > Callback < T > get() {
      return (Callback < T > ) callbacks.get(getRecordClass());
    }

  }
  @Override
  public void close() throws IOException {
    reader.close();
  }
    public void scan(RecordCallbacks callbacks) {
        reader.scan(callbacks);
    }
  public void scan(RecordCallbacks callbacks) {
    if (!isOpen()) {
      throw new IllegalStateException("Reader closed");
    }

    if (scanning) {
      throw new UnsupportedOperationException("Cannot scan while already scanning.");
    }

    scanning = true;

    reset();

    long heapId = 0;
    while (!exhausted()) {
      int tag = readUnsignedByte();

      skip(Companion.INT_SIZE);

      int length = readUnsignedInt();

      switch (tag) {
      case STRING_IN_UTF8:
        RecordCallbacks.RecordCallback < StringRecord > callback = callbacks.get(StringRecord.class);
        if (callback != null || !indexBuilt) {
          long id = readId();
          if (!indexBuilt) {
            hprofStringPositions.put(id, new Pair < > (position, length - idSize));
          }
          if (callback != null) {
            String string = readUtf8(length - idSize);
            callback.callback(new StringRecord(id, string));
          } else {
            skip(length - idSize);
          }
        } else {
          skip(length);
        }
        break;
      case LOAD_CLASS:
        RecordCallbacks.RecordCallback < LoadClassRecord > loadClassCallback = callbacks.get(LoadClassRecord.class);
        if (loadClassCallback != null || !indexBuilt) {
          int classSerialNumber = readInt();
          long id = readId();
          int stackTraceSerialNumber = readInt();
          long classNameStringId = readId();
          if (!indexBuilt) {
            classNames.put(id, classNameStringId);
          }
          if (loadClassCallback != null) {
            loadClassCallback.callback(new LoadClassRecord(
              classSerialNumber, id, stackTraceSerialNumber, classNameStringId));
          }
        } else {
          skip(length);
        }
        break;
      case HEAP_DUMP:
      case HEAP_DUMP_SEGMENT:
        long heapDumpStart = position;
        int previousTag = 0;
        while (position - heapDumpStart < length) {
          int heapDumpTag = readUnsignedByte();

          switch (heapDumpTag) {
          case ROOT_UNKNOWN:
            RecordCallbacks.RecordCallback < GcRootRecord > gcRootCallback = callbacks.get(GcRootRecord.class);
            if (gcRootCallback != null) {
              gcRootCallback.callback(new GcRootRecord(
                gcRoot = new Unknown(id = readId())));
            } else {
              skip(idSize);
            }
            break;
          case ROOT_JNI_GLOBAL:
            gcRootCallback = callbacks.get(GcRootRecord.class);
            if (gcRootCallback != null) {
              gcRootCallback.callback(new GcRootRecord(
                gcRoot = new JniGlobal(id = readId(), jniGlobalRefId = readId())));
            } else {
              skip(idSize + idSize);
            }
            break;
          case ROOT_JNI_LOCAL:
            gcRootCallback = callbacks.get(GcRootRecord.class);
            if (gcRootCallback != null) {
              gcRootCallback.callback(new GcRootRecord(
                gcRoot = new JniLocal(
                  id = readId(), threadSerialNumber = readInt(), frameNumber = readInt())));
            } else {
              skip(idSize + Companion.INT_SIZE + Companion.INT_SIZE);
            }
            break;
          case ROOT_JAVA_FRAME:
            gcRootCallback = callbacks.get(GcRootRecord.class);
            if (gcRootCallback != null) {
              gcRootCallback.callback(new GcRootRecord(
                gcRoot = new JavaFrame(
                  id = readId(), threadSerialNumber = readInt(), frameNumber = readInt())));
            } else {
              skip(idSize + Companion.INT_SIZE + Companion.INT_SIZE);
            }
            break;
          case ROOT_NATIVE_STACK:
            gcRootCallback = callbacks.get(GcRootRecord.class);
            if (gcRootCallback != null) {
              gcRootCallback.callback(new GcRootRecord(
                gcRoot = new NativeStack(id = readId(), threadSerialNumber = readInt())));
            } else {
              skip(idSize + Companion.INT_SIZE);
            }
            break;
          case ROOT_STICKY_CLASS:
            gcRootCallback = callbacks.get(GcRootRecord.class);
            if (gcRootCallback != null) {
              gcRootCallback.callback(new GcRootRecord(
                gcRoot = new StickyClass(id = readId())));
            } else {
              skip(idSize);
            }
            break;
          case ROOT_THREAD_BLOCK:
            gcRootCallback = callbacks.get(GcRootRecord.class);
            if (gcRootCallback != null) {
              gcRootCallback.callback(new GcRootRecord(
                gcRoot = new ThreadBlock(id = readId(), threadSerialNumber = readInt())));
            } else {
              skip(idSize + Companion.INT_SIZE);
            }
            break;
          case ROOT_MONITOR_USED:
            gcRootCallback = callbacks.get(GcRootRecord.class);
            if (gcRootCallback != null) {
              gcRootCallback.callback(new GcRootRecord(
                gcRoot = new MonitorUsed(id = readId())));
            } else {
              skip(idSize);
            }
            break;
          case ROOT_THREAD_OBJECT:
            gcRootCallback = callbacks.get(GcRootRecord.class);
            if (gcRootCallback != null) {
              gcRootCallback.callback(new GcRootRecord(
                gcRoot = new ThreadObject(
                  id = readId(),
                  threadSerialNumber = readInt(),
                  stackTraceSerialNumber = readInt())));
            } else {
              skip(idSize + Companion.INT_SIZE + Companion.INT_SIZE);
            }
            break;
          case CLASS_DUMP:
            RecordCallbacks.RecordCallback < ClassDumpRecord > classDumpCallback = callbacks.get(ClassDumpRecord.class);
            long id = readId();
            if (!indexBuilt) {
              objectPositions.put(id, tagPositionAfterReadingId);
            }
            if (classDumpCallback != null) {
              classDumpCallback.callback(readClassDumpRecord(id));
            } else {
              skip(
                Companion.INT_SIZE + idSize + idSize + idSize + idSize + idSize + idSize + Companion.INT_SIZE
              );

              int constantPoolCount = readUnsignedShort();
              for (int i = 0; i < constantPoolCount; i++) {
                skip(Companion.SHORT_SIZE);
                skip(typeSize(readUnsignedByte()));
              }

              int staticFieldCount = readUnsignedShort();

              for (int i = 0; i < staticFieldCount; i++) {
                skip(idSize);
                int type = readUnsignedByte();
                skip(typeSize(type));
              }

              int fieldCount = readUnsignedShort();
              skip(fieldCount * (idSize + Companion.BYTE_SIZE));
            }
            break;
          case INSTANCE_DUMP:
            id = readId();
            if (!indexBuilt) {
              objectPositions.put(id, tagPositionAfterReadingId);
            }
            RecordCallbacks.RecordCallback < InstanceDumpRecord > instanceDumpCallback = callbacks.get(InstanceDumpRecord.class);
            if (instanceDumpCallback != null) {
              instanceDumpCallback.callback(readInstanceDumpRecord(id));
            } else {
              skip(Companion.INT_SIZE + idSize);
              int remainingBytesInInstance = readInt();
              skip(remainingBytesInInstance);
            }
            break;
          case OBJECT_ARRAY_DUMP:
            id = readId();
            if (!indexBuilt) {
              objectPositions.put(id, tagPositionAfterReadingId);
            }
            RecordCallbacks.RecordCallback < ObjectArrayDumpRecord > objectArrayDumpCallback = callbacks.get(ObjectArrayDumpRecord.class);
            if (objectArrayDumpCallback != null) {
              objectArrayDumpCallback.callback(readObjectArrayDumpRecord(id));
            } else {
              skip(Companion.INT_SIZE);
              int arrayLength = readInt();
              skip(idSize + arrayLength * idSize);
            }
            break;
          case PRIMITIVE_ARRAY_DUMP:
            id = readId();
            if (!indexBuilt) {
              objectPositions.put(id, tagPositionAfterReadingId);
            }
            RecordCallbacks.RecordCallback < PrimitiveArrayDumpRecord > primitiveArrayDumpCallback = callbacks.get(PrimitiveArrayDumpRecord.class);
            if (primitiveArrayDumpCallback != null) {
              primitiveArrayDumpCallback.callback(readPrimitiveArrayDumpRecord(id));
            } else {
              skip(Companion.INT_SIZE);
              int arrayLength = readInt();
              int type = readUnsignedByte();
              skip(arrayLength * typeSize(type));
            }
            break;
          case HEAP_DUMP_INFO:
            heapId = readInt();
            RecordCallbacks.RecordCallback < HeapDumpInfoRecord > heapDumpInfoCallback = callbacks.get(HeapDumpInfoRecord.class);
            if (heapDumpInfoCallback != null) {
              heapDumpInfoCallback.callback(new HeapDumpInfoRecord(heapId, heapNameStringId = readId()));
            } else {
              skip(idSize);
            }
            break;
          case ROOT_INTERNED_STRING:
            gcRootCallback = callbacks.get(GcRootRecord.class);
            if (gcRootCallback != null) {
              gcRootCallback.callback(new GcRootRecord(
                gcRoot = new InternedString(id = readId())));
            } else {
              skip(idSize);
            }
            break;
          case ROOT_FINALIZING:
            gcRootCallback = callbacks.get(GcRootRecord.class);
            if (gcRootCallback != null) {
              gcRootCallback.callback(new GcRootRecord(
                gcRoot = new Finalizing(id = readId())));
            } else {
              skip(idSize);
            }
            break;
          case ROOT_DEBUGGER:
            gcRootCallback = callbacks.get(GcRootRecord.class);
            if (gcRootCallback != null) {
              gcRootCallback.callback(new GcRootRecord(
                gcRoot = new Debugger(id = readId())));
            } else {
              skip(idSize);
            }
            break;
          case ROOT_REFERENCE_CLEANUP:
            gcRootCallback = callbacks.get(GcRootRecord.class);
            if (gcRootCallback != null) {
              gcRootCallback.callback(new GcRootRecord(
                gcRoot = new ReferenceCleanup(id = readId())));
            } else {
              skip(idSize);
            }
            break;
          case ROOT_VM_INTERNAL:
            gcRootCallback = callbacks.get(GcRootRecord.class);
            if (gcRootCallback != null) {
              gcRootCallback.callback(new GcRootRecord(
                gcRoot = new VmInternal(id = readId())));
            } else {
              skip(idSize);
            }
            break;
          case ROOT_JNI_MONITOR:
            gcRootCallback = callbacks.get(GcRootRecord.class);
            if (gcRootCallback != null) {
              gcRootCallback.callback(new GcRootRecord(
                gcRoot = new JniMonitor(
                  id = readId(), stackTraceSerialNumber = readInt(),
                  stackDepth = readInt())));
            } else {
              skip(idSize + Companion.INT_SIZE + Companion.INT_SIZE);
            }
            break;
          case ROOT_UNREACHABLE:
            gcRootCallback = callbacks.get(GcRootRecord.class);
            if (gcRootCallback != null) {
              gcRootCallback.callback(new GcRootRecord(
                gcRoot = new Unreachable(id = readId())));
            } else {
              skip(idSize);
            }
            break;
          default:
            throw new IllegalStateException("Unknown tag " + heapDumpTag + " after " + previousTag);
          }
          previousTag = heapDumpTag;
        }
        heapId = 0;
        break;
      default:
        skip(length);
        break;
      }
    }

    scanning = false;
    indexBuilt = true;
  }

  public String hprofStringById(long id) {
    Pair < Long, Long > positionAndLength = hprofStringPositions.get(id);
    if (positionAndLength == null) {
      throw new IllegalArgumentException("Unknown string id " + id);
    }
    reader.moveTo(positionAndLength.getFirst());
    return reader.readUtf8(positionAndLength.getSecond());
  }

  String retrieveString(ObjectReference reference) {
    InstanceDumpRecord instanceRecord = (InstanceDumpRecord) retrieveRecord(reference);
    ObjectInstance instance = hydrateInstance(instanceRecord);
    return instanceAsString(instance);
  }

  ObjectRecord retrieveRecord(ObjectReference reference) {
    return retrieveRecord(reference.value);
  }
  public ObjectRecord retrieveRecord(long objectId) {
    int position;
    require(objectPositions.containsKey(objectId), "Unknown object id " + objectId);
    position = objectPositions.get(objectId);
    reader.moveTo(position);
    byte heapDumpTag = reader.readUnsignedByte();

    reader.skip(reader.idSize);
    return switch (heapDumpTag) {
    case CLASS_DUMP -> reader.readClassDumpRecord(objectId);
    case INSTANCE_DUMP -> reader.readInstanceDumpRecord(objectId);
    case OBJECT_ARRAY_DUMP -> reader.readObjectArrayDumpRecord(objectId);
    case PRIMITIVE_ARRAY_DUMP -> reader.readPrimitiveArrayDumpRecord(objectId);
    default -> {
      throw new IllegalStateException(
        "Unexpected tag " + heapDumpTag + " for id " + objectId + " at position " + position);
    }
    };
  }

  public class HydratedInstance {
    private final InstanceDumpRecord record;
    private final List < HydratedClass > classHierarchy;
    private final List < List < HeapValue >> fieldValues;

    public HydratedInstance(InstanceDumpRecord record, List < HydratedClass > classHierarchy, List < List < HeapValue >> fieldValues) {
      this.record = record;
      this.classHierarchy = classHierarchy;
      this.fieldValues = fieldValues;
    }

    public < T extends HeapValue > T fieldValue(String name) {
      T fieldValueOrNull = fieldValueOrNull(name);
      if (fieldValueOrNull == null) {
        throw new IllegalArgumentException("Could not find field " + name + " in instance with id " + record.getId());
      }
      return fieldValueOrNull;
    }

    public < T extends HeapValue > T fieldValueOrNull(String name) {
      for (int classIndex = 0; classIndex < classHierarchy.size(); classIndex++) {
        HydratedClass hydratedClass = classHierarchy.get(classIndex);
        for (int fieldIndex = 0; fieldIndex < hydratedClass.getFieldNames().size(); fieldIndex++) {
          String fieldName = hydratedClass.getFieldNames().get(fieldIndex);
          if (fieldName.equals(name)) {
            @SuppressWarnings("unchecked")
            T fieldValue = (T) fieldValues.get(classIndex).get(fieldIndex);
            return fieldValue;
          }
        }
      }
      return null;
    }
  }
  public static HydratedInstance hydrateInstance(InstanceDumpRecord instanceRecord) {
    long classId = instanceRecord.getClassId();

    List < HydratedClass > classHierarchy = new ArrayList < > ();
    do {
      ClassDumpRecord classRecord = (ClassDumpRecord) retrieveRecord(classId);
      String className = hprofStringById(classNames.get(classRecord.getId()));

      List < String > staticFieldNames = new ArrayList < > ();
      for (FieldDumpRecord staticField: classRecord.getStaticFields()) {
        staticFieldNames.add(hprofStringById(staticField.getNameStringId()));
      }

      List < String > fieldNames = new ArrayList < > ();
      for (FieldDumpRecord field: classRecord.getFields()) {
        fieldNames.add(hprofStringById(field.getNameStringId()));
      }

      classHierarchy.add(new HydratedClass(classRecord, className, staticFieldNames, fieldNames));
      classId = classRecord.getSuperClassId();
    } while (classId != 0 L);

    Buffer buffer = new Buffer();
    buffer.write(instanceRecord.getFieldValues());
    HprofReader valuesReader = new HprofReader(buffer, 0, reader.getIdSize());

    List < List < HeapValue >> allFieldValues = new ArrayList < > ();
    for (HydratedClass hydratedClass: classHierarchy) {
      List < HeapValue > classFieldValues = new ArrayList < > ();
      for (FieldDumpRecord field: hydratedClass.getRecord().getFields()) {
        classFieldValues.add(valuesReader.readValue(field.getType()));
      }
      allFieldValues.add(classFieldValues);
    }

    return new HydratedInstance(instanceRecord, classHierarchy, allFieldValues);
  }

  public String instanceAsString(HydratedInstance instance) {
    int count = instance.fieldValue("count", IntValue.class).value;
    if (count == 0) {
      return "";
    }
    ObjectReference value = instance.fieldValue("value", ObjectReference.class);
    Record valueRecord = retrieveRecord(value);
    if (valueRecord instanceof CharArrayDump) {
      int offset = 0;
      IntValue offsetValue = instance.fieldValueOrNull("offset", IntValue.class);
      if (offsetValue != null) {
        offset = offsetValue.value;
      }
      CharArrayDump charArrayDump = (CharArrayDump) valueRecord;
      char[] chars = Arrays.copyOfRange(charArrayDump.array, offset, offset + count);
      return new String(chars);
    } else if (valueRecord instanceof ByteArrayDump) {
      return new String(((ByteArrayDump) valueRecord).array, StandardCharsets.UTF_8);
    } else {
      throw new UnsupportedOperationException(
        "'value' field was expected to be either a char or byte array in string instance with id " + instance.record.id);
    }
  }

  class HydratedClass {
    private final ClassDumpRecord record;
    private final String className;
    private final List < String > staticFieldNames;
    private final List < String > fieldNames;

    public HydratedClass(ClassDumpRecord record, String className, List < String > staticFieldNames, List < String > fieldNames) {
      this.record = record;
      this.className = className;
      this.staticFieldNames = staticFieldNames;
      this.fieldNames = fieldNames;
    }

  }

  public static final int STRING_IN_UTF8 = 0x01;
  public static final int LOAD_CLASS = 0x02;
  public static final int UNLOAD_CLASS = 0x03;
  public static final int STACK_FRAME = 0x04;
  public static final int STACK_TRACE = 0x05;
  public static final int ALLOC_SITES = 0x06;
  public static final int HEAP_SUMMARY = 0x07;
  public static final int START_THREAD = 0x0a;
  public static final int END_THREAD = 0x0b;
  public static final int HEAP_DUMP = 0x0c;
  public static final int HEAP_DUMP_SEGMENT = 0x1c;
  public static final int HEAP_DUMP_END = 0x2c;
  public static final int CPU_SAMPLES = 0x0d;
  public static final int CONTROL_SETTINGS = 0x0e;
  public static final int ROOT_UNKNOWN = 0xff;
  public static final int ROOT_JNI_GLOBAL = 0x01;
  public static final int ROOT_JNI_LOCAL = 0x02;
  public static final int ROOT_JAVA_FRAME = 0x03;
  public static final int ROOT_NATIVE_STACK = 0x04;
  public static final int ROOT_STICKY_CLASS = 0x05;
  public static final int ROOT_THREAD_BLOCK = 0x06;
  public static final int ROOT_MONITOR_USED = 0x07;
  public static final int ROOT_THREAD_OBJECT = 0x08;
  public static final int CLASS_DUMP = 0x20;
  public static final int INSTANCE_DUMP = 0x21;
  public static final int OBJECT_ARRAY_DUMP = 0x22;
  public static final int PRIMITIVE_ARRAY_DUMP = 0x23;
  public static final int HEAP_DUMP_INFO = 0xfe;
  public static final int ROOT_INTERNED_STRING = 0x89;
  public static final int ROOT_FINALIZING = 0x8a;
  public static final int ROOT_DEBUGGER = 0x8b;
  public static final int ROOT_REFERENCE_CLEANUP = 0x8c;
  public static final int ROOT_VM_INTERNAL = 0x8d;
  public static final int ROOT_JNI_MONITOR = 0x8e;
  public static final int ROOT_UNREACHABLE = 0x90;
  public static final int PRIMITIVE_ARRAY_NODATA = 0xc3;

  public static HprofParser open(File heapDump) throws IOException {
    InputStream inputStream = new FileInputStream(heapDump);
    FileChannel channel = inputStream.getChannel();
    ReadableByteChannel source = Channels.newChannel(inputStream);
    ByteBuffer buffer = ByteBuffer.allocateDirect(1024);

    int endOfVersionString = source.read(buffer);
    buffer.flip();
    int endOfVersionStringPos = buffer.position();
    buffer.clear();
    source.read(buffer);
    int idSize = buffer.getInt();
    long startPosition = endOfVersionStringPos + 4;

    SeekableHprofReader hprofReader = new SeekableHprofReader(channel, source, startPosition, idSize);
    return new HprofParser(hprofReader);
  }
}