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
import leakcanary.internal.haha.HprofReader;
import leakcanary.internal.haha.HprofReader.Companion;
import leakcanary.internal.haha.Record.HeapDumpRecord.ClassDumpRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.GcRootRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.HeapDumpInfoRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.InstanceDumpRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectArrayDumpRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.PrimitiveArrayDumpRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.PrimitiveArrayDumpRecord.BooleanArrayDump;
import leakcanary.internal.haha.Record.HeapDumpRecord.PrimitiveArrayDumpRecord.ByteArrayDump;
import leakcanary.internal.haha.Record.HeapDumpRecord.PrimitiveArrayDumpRecord.CharArrayDump;
import leakcanary.internal.haha.Record.HeapDumpRecord.PrimitiveArrayDumpRecord.DoubleArrayDump;
import leakcanary.internal.haha.Record.HeapDumpRecord.PrimitiveArrayDumpRecord.FloatArrayDump;
import leakcanary.internal.haha.Record.HeapDumpRecord.PrimitiveArrayDumpRecord.IntArrayDump;
import leakcanary.internal.haha.Record.HeapDumpRecord.PrimitiveArrayDumpRecord.LongArrayDump;
import leakcanary.internal.haha.Record.HeapDumpRecord.PrimitiveArrayDumpRecord.ShortArrayDump;
import leakcanary.internal.haha.Record.LoadClassRecord;
import leakcanary.internal.haha.Record.StringRecord;
import okio.Buffer;
import okio.ByteString;
import okio.Source;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

public class HprofParser implements Closeable {

  private static final int STRING_IN_UTF8 = 0x01;
  private static final int LOAD_CLASS = 0x02;
  private static final int UNLOAD_CLASS = 0x03;
  private static final int STACK_FRAME = 0x04;
  private static final int STACK_TRACE = 0x05;
  private static final int ALLOC_SITES = 0x06;
  private static final int HEAP_SUMMARY = 0x07;
  private static final int START_THREAD = 0x0a;
  private static final int END_THREAD = 0x0b;
  private static final int HEAP_DUMP = 0x0c;
  private static final int HEAP_DUMP_SEGMENT = 0x1c;
  private static final int HEAP_DUMP_END = 0x2c;
  private static final int CPU_SAMPLES = 0x0d;
  private static final int CONTROL_SETTINGS = 0x0e;
  private static final int ROOT_UNKNOWN = 0xff;
  private static final int ROOT_JNI_GLOBAL = 0x01;
  private static final int ROOT_JNI_LOCAL = 0x02;
  private static final int ROOT_JAVA_FRAME = 0x03;
  private static final int ROOT_NATIVE_STACK = 0x04;
  private static final int ROOT_STICKY_CLASS = 0x05;
  private static final int ROOT_THREAD_BLOCK = 0x06;
  private static final int ROOT_MONITOR_USED = 0x07;
  private static final int ROOT_THREAD_OBJECT = 0x08;
  private static final int CLASS_DUMP = 0x20;
  private static final int INSTANCE_DUMP = 0x21;
  private static final int OBJECT_ARRAY_DUMP = 0x22;
  private static final int PRIMITIVE_ARRAY_DUMP = 0x23;
  private static final int PRIMITIVE_ARRAY_NODATA = 0xc3;
  private static final int HEAP_DUMP_INFO = 0xfe;
  private static final int ROOT_INTERNED_STRING = 0x89;
  private static final int ROOT_FINALIZING = 0x8a;
  private static final int ROOT_DEBUGGER = 0x8b;
  private static final int ROOT_REFERENCE_CLEANUP = 0x8c;
  private static final int ROOT_VM_INTERNAL = 0x8d;
  private static final int ROOT_JNI_MONITOR = 0x8e;
  private static final int ROOT_UNREACHABLE = 0x90;

  private SeekableHprofReader reader;
  private boolean scanning;
  private boolean indexBuilt;
  private Map < Long, Pair < Long, Long >> hprofStringPositions;
  private Map < Long, Long > classNames;
  private Map < Long, Long > classPositions;
  private Map < Long, Long > instancePositions;

  public HprofParser(SeekableHprofReader reader) {
    this.reader = reader;
    this.hprofStringPositions = new HashMap < > ();
    this.classNames = new HashMap < > ();
    this.classPositions = new HashMap < > ();
    this.instancePositions = new HashMap < > ();
  }

  public class RecordCallbacks {
    private final Map < Class < ? extends Record > , Object > callbacks = new HashMap < > ();

    public < T extends Record > RecordCallbacks on(Class < T > recordClass, Callback < T > callback) {
      callbacks.put(recordClass, callback);
      return this;
    }

    @SuppressWarnings("unchecked")
    public < T extends Record > Callback < T > get(Class < T > recordClass) {
      return (Callback < T > ) callbacks.get(recordClass);
    }

    @SuppressWarnings("unchecked")
    public < T extends Record > Callback < T > get() {
      return (Callback < T > ) callbacks.get(getCallBackClass(recordClass));
    }
  }

  public void close() throws IOException {
    reader.close();
  }

  public void scan(RecordCallbacks callbacks) {
    reader.scan(callbacks);
  }

  public void scan(RecordCallbacks callbacks) throws IOException {
    if (!isOpen()) {
      throw new IllegalStateException("Reader closed");
    }

    if (scanning) {
      throw new UnsupportedOperationException("Cannot scan while already scanning.");
    }

    scanning = true;

    reset();

    skipLong();

    int heapId = 0;
    while (!exhausted()) {

      int tag = readUnsignedByte();

      skipInt();

      int length = readUnsignedInt();

      switch (tag) {
      case STRING_IN_UTF8:
        RecordCallback < StringRecord > stringCallback = callbacks.get(StringRecord.class);
        if (stringCallback != null || !indexBuilt) {
          int id = readId();
          if (!indexBuilt) {
            hprofStringPositions.put(id, new Pair < > (position, length - idSize));
          }
          if (stringCallback != null) {
            String string = readUtf8(length - idSize);
            stringCallback.callback(new StringRecord(id, string));
          } else {
            skip(length - idSize);
          }
        } else {
          skip(length);
        }
        break;
      case LOAD_CLASS:
        RecordCallback < LoadClassRecord > loadClassCallback = callbacks.get(LoadClassRecord.class);
        if (loadClassCallback != null || !indexBuilt) {
          int classSerialNumber = readInt();
          int id = readId();
          int stackTraceSerialNumber = readInt();
          int classNameStringId = readId();
          if (!indexBuilt) {
            classNames.put(id, classNameStringId);
          }
          if (loadClassCallback != null) {
            loadClassCallback.callback(new LoadClassRecord(
              classSerialNumber,
              id,
              stackTraceSerialNumber,
              classNameStringId
            ));
          }
        } else {
          skip(length);
        }
        break;
      case HEAP_DUMP:
      case HEAP_DUMP_SEGMENT:
        int heapDumpStart = position;
        int previousTag = 0;
        while (position - heapDumpStart < length) {
          int heapDumpTag = readUnsignedByte();

          switch (heapDumpTag) {
          case ROOT_UNKNOWN:
            RecordCallback < GcRootRecord > unknownCallback = callbacks.get(GcRootRecord.class);
            if (unknownCallback != null) {
              unknownCallback.callback(new GcRootRecord(
                gcRoot = new Unknown(id = readId())
              ));
            } else {
              skip(idSize);
            }
            break;
            // ... other cases omitted for brevity
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

  private String hprofStringById(long id) {
    int[] positions = hprofStringPositions.get(id);
    Objects.requireNonNull(positions, "Unknown string id " + id);

    this.position = positions[0];
    byte[] bytes = new byte[positions[1]];
    buffer.get(bytes);

    return new String(bytes, StandardCharsets.UTF_8);
  }

  public ClassDumpRecord classDumpRecordById(long id) {
    Long position = classPositions.get(id);
    Objects.requireNonNull(position, "Unknown class id " + id);

    this.position = position;
    return readClassDumpRecord(id);
  }

  public InstanceDumpRecord instanceDumpRecordById(long id) {
    Long position = instancePositions.get(id);
    Objects.requireNonNull(position, "Unknown instance id " + id);

    this.position = position;
    return readInstanceDumpRecord(id);
  }

  class HydradedInstance {
    private final InstanceDumpRecord record;
    private final List < HydradedClass > classHierarchy;
    private final List < List < HeapValue >> fieldValues;

    public HydradedInstance(InstanceDumpRecord record, List < HydradedClass > classHierarchy, List < List < HeapValue >> fieldValues) {
      this.record = record;
      this.classHierarchy = classHierarchy;
      this.fieldValues = fieldValues;
    }
  }

  public class HydratedClass {
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
  public static HydratedInstance hydratedInstanceById(Long id) {
    try {
      return Hydrate.hydrate(InstanceDumpRecord.getInstanceDumpRecordById(id));
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
  public static HydradedInstance hydrate(InstanceDumpRecord instanceRecord) {
    long classId = instanceRecord.getClassId();
    List < HydradedClass > classHierarchy = new ArrayList < > ();

    do {
      ClassDumpRecord classRecord = classDumpRecordById(classId);
      String className = hprofStringById(classNames.get(classRecord.getId()));

      List < String > staticFieldNames = new ArrayList < > ();
      for (FieldDumpRecord staticField: classRecord.getStaticFieldsList()) {
        staticFieldNames.add(hprofStringById(staticField.getNameStringId()));
      }

      List < String > fieldNames = new ArrayList < > ();
      for (FieldDumpRecord field: classRecord.getFieldsList()) {
        fieldNames.add(hprofStringById(field.getNameStringId()));
      }

      classHierarchy.add(new HydradedClass(classRecord, className, staticFieldNames, fieldNames));
      classId = classRecord.getSuperClassId();
    } while (classId != 0);

    ByteString valuesByteString = instanceRecord.getFieldValues();
    CodedInputStream buffer = CodedInputStream.newInstance(valuesByteString.toByteArray());

    List < List < Object >> allFieldValues = new ArrayList < > ();
    for (HydradedClass hydratedClass: classHierarchy) {
      List < Object > fieldValues = new ArrayList < > ();
      for (FieldDumpRecord field: hydratedClass.getRecord().getFieldsList()) {
        fieldValues.add(HprofReader.readValue(buffer, field.getType()));
      }
      allFieldValues.add(fieldValues);
    }

    return new HydradedInstance(instanceRecord, classHierarchy, allFieldValues);
  }

  public static HprofParser open(File heapDump) throws IOException {
    InputStream inputStream = new FileInputStream(heapDump);
    FileChannel channel = inputStream.getChannel();
    Source source = okio.Okio.source(inputStream);
    Buffer buffer = source.buffer();

    int endOfVersionString = buffer.indexOf((byte) 0);
    buffer.skip(endOfVersionString + 1);
    int idSize = buffer.readInt();
    long startPosition = endOfVersionString + 1 + 4;

    SeekableHprofReader hprofReader = new SeekableHprofReader(channel, buffer, startPosition, idSize);
    return new HprofParser(hprofReader);
  }
}