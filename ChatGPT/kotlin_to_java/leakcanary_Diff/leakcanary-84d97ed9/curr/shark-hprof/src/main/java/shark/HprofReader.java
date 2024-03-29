package shark;

import okio.BufferedSource;
import kotlin.reflect.KClass;

import java.util.Set;

public class HprofReader {

  public HprofReader(BufferedSource source, int identifierByteSize, long startByteReadCount) {
    this.source = source;
    this.identifierByteSize = identifierByteSize;
    this.startByteReadCount = startByteReadCount;
    this.byteReadCount = startByteReadCount;
    this.typeSizes = new int[] {
      PrimitiveType.BOOLEAN.byteSize,
        PrimitiveType.BYTE.byteSize,
        PrimitiveType.CHAR.byteSize,
        PrimitiveType.DOUBLE.byteSize,
        PrimitiveType.FLOAT.byteSize,
        PrimitiveType.INT.byteSize,
        PrimitiveType.LONG.byteSize,
        PrimitiveType.SHORT.byteSize,
        identifierByteSize
    };
  }
  public void readHprofRecords(Set < Class < ? extends HprofRecord >> recordTypes, OnHprofRecordListener listener) {
    if (byteReadCount != startByteReadCount) {
      throw new IllegalStateException("readHprofRecords() should only be called on an unused HprofReader instance");
    }
    boolean readAllRecords = recordTypes.contains(HprofRecord.class);
    boolean readStringRecord = readAllRecords || recordTypes.contains(StringRecord.class);
    boolean readLoadClassRecord = readAllRecords || recordTypes.contains(LoadClassRecord.class);
    boolean readHeapDumpEndRecord = readAllRecords || recordTypes.contains(HeapDumpEndRecord.class);
    boolean readStackFrameRecord = readAllRecords || recordTypes.contains(StackFrameRecord.class);
    boolean readStackTraceRecord = readAllRecords || recordTypes.contains(StackTraceRecord.class);

    boolean readAllHeapDumpRecords = readAllRecords || recordTypes.contains(HeapDumpRecord.class);

    boolean readGcRootRecord = readAllHeapDumpRecords || recordTypes.contains(GcRootRecord.class);
    boolean readHeapDumpInfoRecord = readAllRecords || recordTypes.contains(HeapDumpInfoRecord.class);

    boolean readAllObjectRecords = readAllHeapDumpRecords || recordTypes.contains(ObjectRecord.class);

    boolean readClassDumpRecord = readAllObjectRecords || recordTypes.contains(ClassDumpRecord.class);
    boolean readInstanceDumpRecord = readAllObjectRecords || recordTypes.contains(InstanceDumpRecord.class);
    boolean readObjectArrayDumpRecord = readAllObjectRecords || recordTypes.contains(ObjectArrayDumpRecord.class);
    boolean readPrimitiveArrayDumpRecord = readAllObjectRecords || recordTypes.contains(PrimitiveArrayDumpRecord.class);

    int intByteSize = INT.byteSize;

    while (!exhausted()) {
      int tag = readUnsignedByte();

      skip(intByteSize);

      int length = readUnsignedInt();

      switch (tag) {
      case STRING_IN_UTF8:
        if (readStringRecord) {
          long recordPosition = byteReadCount;
          long id = readId();
          int stringLength = length - identifierByteSize;
          String string = readUtf8(stringLength);
          StringRecord record = new StringRecord(id, string);
          listener.onHprofRecord(recordPosition, record);
        } else {
          skip(length);
        }
        break;
      case LOAD_CLASS:
        if (readLoadClassRecord) {
          long recordPosition = byteReadCount;
          int classSerialNumber = readInt();
          long id = readId();
          int stackTraceSerialNumber = readInt();
          long classNameStringId = readId();
          LoadClassRecord record = new LoadClassRecord(
            classSerialNumber, id, stackTraceSerialNumber, classNameStringId);
          listener.onHprofRecord(recordPosition, record);
        } else {
          skip(length);
        }
        break;
      case STACK_FRAME:
        if (readStackFrameRecord) {
          long recordPosition = byteReadCount;
          StackFrameRecord record = new StackFrameRecord(
            readId(), readId(), readId(), readId(), readInt(), readInt());
          listener.onHprofRecord(recordPosition, record);
        } else {
          skip(length);
        }
        break;
      case STACK_TRACE:
        if (readStackTraceRecord) {
          long recordPosition = byteReadCount;
          int stackTraceSerialNumber = readInt();
          int threadSerialNumber = readInt();
          int frameCount = readInt();
          long[] stackFrameIds = readIdArray(frameCount);
          StackTraceRecord record = new StackTraceRecord(
            stackTraceSerialNumber, threadSerialNumber, stackFrameIds);
          listener.onHprofRecord(recordPosition, record);
        } else {
          skip(length);
        }
        break;
      case HEAP_DUMP:
      case HEAP_DUMP_SEGMENT:
        long heapDumpStart = byteReadCount;
        int previousTag = 0;
        while (byteReadCount - heapDumpStart < length) {
          int heapDumpTag = readUnsignedByte();

          switch (heapDumpTag) {
          case ROOT_UNKNOWN:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord record = new GcRootRecord(new Unknown(readId()));
              listener.onHprofRecord(recordPosition, record);
            } else {
              skip(identifierByteSize);
            }
            break;
          case ROOT_JNI_GLOBAL:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord gcRootRecord = new GcRootRecord(
                new JniGlobal(readId(), readId()));
              listener.onHprofRecord(recordPosition, gcRootRecord);
            } else {
              skip(identifierByteSize + identifierByteSize);
            }
            break;
          case ROOT_JNI_LOCAL:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord gcRootRecord = new GcRootRecord(
                new JniLocal(readId(), readInt(), readInt()));
              listener.onHprofRecord(recordPosition, gcRootRecord);
            } else {
              skip(identifierByteSize + intByteSize + intByteSize);
            }
            break;
          case ROOT_JAVA_FRAME:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord gcRootRecord = new GcRootRecord(
                new JavaFrame(readId(), readInt(), readInt()));
              listener.onHprofRecord(recordPosition, gcRootRecord);
            } else {
              skip(identifierByteSize + intByteSize + intByteSize);
            }
            break;
          case ROOT_NATIVE_STACK:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord gcRootRecord = new GcRootRecord(
                new NativeStack(readId(), readInt()));
              listener.onHprofRecord(recordPosition, gcRootRecord);
            } else {
              skip(identifierByteSize + intByteSize);
            }
            break;
          case ROOT_STICKY_CLASS:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord gcRootRecord = new GcRootRecord(new StickyClass(readId()));
              listener.onHprofRecord(recordPosition, gcRootRecord);
            } else {
              skip(identifierByteSize);
            }
            break;
          case ROOT_THREAD_BLOCK:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord gcRootRecord = new GcRootRecord(
                new ThreadBlock(readId(), readInt()));
              listener.onHprofRecord(recordPosition, gcRootRecord);
            } else {
              skip(identifierByteSize + intByteSize);
            }
            break;
          case ROOT_MONITOR_USED:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord gcRootRecord = new GcRootRecord(new MonitorUsed(readId()));
              listener.onHprofRecord(recordPosition, gcRootRecord);
            } else {
              skip(identifierByteSize);
            }
            break;
          case ROOT_THREAD_OBJECT:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord gcRootRecord = new GcRootRecord(
                new ThreadObject(readId(), readInt(), readInt()));
              listener.onHprofRecord(recordPosition, gcRootRecord);
            } else {
              skip(identifierByteSize + intByteSize + intByteSize);
            }
            break;
          case ROOT_INTERNED_STRING:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord gcRootRecord = new GcRootRecord(new InternedString(readId()));
              listener.onHprofRecord(recordPosition, gcRootRecord);
            } else {
              skip(identifierByteSize);
            }
            break;
          case ROOT_FINALIZING:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord gcRootRecord = new GcRootRecord(new Finalizing(readId()));
              listener.onHprofRecord(recordPosition, gcRootRecord);
            } else {
              skip(identifierByteSize);
            }
            break;
          case ROOT_DEBUGGER:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord gcRootRecord = new GcRootRecord(new Debugger(readId()));
              listener.onHprofRecord(recordPosition, gcRootRecord);
            } else {
              skip(identifierByteSize);
            }
            break;
          case ROOT_REFERENCE_CLEANUP:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord gcRootRecord = new GcRootRecord(new ReferenceCleanup(readId()));
              listener.onHprofRecord(recordPosition, gcRootRecord);
            } else {
              skip(identifierByteSize);
            }
            break;
          case ROOT_VM_INTERNAL:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord gcRootRecord = new GcRootRecord(new VmInternal(readId()));
              listener.onHprofRecord(recordPosition, gcRootRecord);
            } else {
              skip(identifierByteSize);
            }
            break;
          case ROOT_JNI_MONITOR:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord gcRootRecord = new GcRootRecord(
                new JniMonitor(readId(), readInt(), readInt()));
              listener.onHprofRecord(recordPosition, gcRootRecord);
            } else {
              skip(identifierByteSize + intByteSize + intByteSize);
            }
            break;
          case ROOT_UNREACHABLE:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord gcRootRecord = new GcRootRecord(new Unreachable(readId()));
              listener.onHprofRecord(recordPosition, gcRootRecord);
            } else {
              skip(identifierByteSize);
            }
            break;
          case CLASS_DUMP:
            if (readClassDumpRecord) {
              long recordPosition = byteReadCount;
              HprofRecord record = readClassDumpRecord();
              listener.onHprofRecord(recordPosition, record);
            } else {
              skipClassDumpRecord();
            }
            break;
          case INSTANCE_DUMP:
            if (readInstanceDumpRecord) {
              long recordPosition = byteReadCount;
              HprofRecord instanceDumpRecord = readInstanceDumpRecord();
              listener.onHprofRecord(recordPosition, instanceDumpRecord);
            } else {
              skipInstanceDumpRecord();
            }
            break;
          case OBJECT_ARRAY_DUMP:
            if (readObjectArrayDumpRecord) {
              long recordPosition = byteReadCount;
              HprofRecord arrayRecord = readObjectArrayDumpRecord();
              listener.onHprofRecord(recordPosition, arrayRecord);
            } else {
              skipObjectArrayDumpRecord();
            }
            break;
          case PRIMITIVE_ARRAY_DUMP:
            if (readPrimitiveArrayDumpRecord) {
              long recordPosition = byteReadCount;
              HprofRecord record = readPrimitiveArrayDumpRecord();
              listener.onHprofRecord(recordPosition, record);
            } else {
              skipPrimitiveArrayDumpRecord();
            }
            break;
          case PRIMITIVE_ARRAY_NODATA:
            throw new UnsupportedOperationException("PRIMITIVE_ARRAY_NODATA cannot be parsed");
          case HEAP_DUMP_INFO:
            if (readHeapDumpInfoRecord) {
              long recordPosition = byteReadCount;
              HprofRecord record = readHeapDumpInfoRecord();
              listener.onHprofRecord(recordPosition, record);
            } else {
              skipHeapDumpInfoRecord();
            }
            break;
          default:
            throw new IllegalStateException("Unknown tag " + heapDumpTag + " after " + previousTag);
          }
          previousTag = heapDumpTag;
        }
        break;
      case HEAP_DUMP_END:
        if (readHeapDumpEndRecord) {
          long recordPosition = byteReadCount;
          HeapDumpEndRecord record = new HeapDumpEndRecord();
          listener.onHprofRecord(recordPosition, record);
        }
        break;
      default:
        skip(length);
        break;
      }
    }
  }
  private static final int BOOLEAN_SIZE = 1;
  private static final int CHAR_SIZE = 2;
  private static final int FLOAT_SIZE = 4;
  private static final int DOUBLE_SIZE = 8;
  private static final int BYTE_SIZE = 1;
  private static final int SHORT_SIZE = 2;
  private static final int INT_SIZE = 4;
  private static final int LONG_SIZE = 8;

  private static final byte BOOLEAN_TYPE = 4;
  private static final byte CHAR_TYPE = 5;
  private static final byte FLOAT_TYPE = 6;
  private static final byte DOUBLE_TYPE = 7;
  private static final byte BYTE_TYPE = 8;
  private static final byte SHORT_TYPE = 9;
  private static final byte INT_TYPE = 10;
  private static final byte LONG_TYPE = 11;

  private static final long INT_MASK = 0xffffffff L;
  private static final int BYTE_MASK = 0xff;

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
  private static final int HEAP_DUMP_INFO = 0xfe;
  private static final int ROOT_INTERNED_STRING = 0x89;
  private static final int ROOT_FINALIZING = 0x8a;
  private static final int ROOT_DEBUGGER = 0x8b;
  private static final int ROOT_REFERENCE_CLEANUP = 0x8c;
  private static final int ROOT_VM_INTERNAL = 0x8d;
  private static final int ROOT_JNI_MONITOR = 0x8e;
  private static final int ROOT_UNREACHABLE = 0x90;
  private static final int PRIMITIVE_ARRAY_NODATA = 0xc3;

  private int byteReadCount;
  private final HprofSource source;

  public HprofReader(HprofSource source) {
    this.source = source;
    this.byteReadCount = 0;
  }

  public InstanceDumpRecord readInstanceDumpRecord() {
    long id = readId();
    int stackTraceSerialNumber = readInt();
    long classId = readId();
    int remainingBytesInInstance = readInt();
    byte[] fieldValues = readByteArray(remainingBytesInInstance);
    return new InstanceDumpRecord(
      id, stackTraceSerialNumber, classId, fieldValues
    );
  }

  public ClassDumpRecord readClassDumpRecord() {
    long id = readId();
    int stackTraceSerialNumber = readInt();
    long superclassId = readId();
    long classLoaderId = readId();
    long signersId = readId();
    long protectionDomainId = readId();
    readId(); // reserved
    readId(); // reserved
    int instanceSize = readInt();
    // Skip constant pool
    int constantPoolCount = readUnsignedShort();
    for (int i = 0; i < constantPoolCount; i++) {
      skip(SHORT_SIZE);
      skip(typeSize(readUnsignedByte()));
    }
    // Read static fields
    int staticFieldCount = readUnsignedShort();
    ArrayList < StaticFieldRecord > staticFields = new ArrayList < > (staticFieldCount);
    for (int i = 0; i < staticFieldCount; i++) {
      long nameStringId = readId();
      int type = readUnsignedByte();
      ValueHolder value = readValue(type);
      staticFields.add(new StaticFieldRecord(nameStringId, type, value));
    }
    // Read fields
    int fieldCount = readUnsignedShort();
    ArrayList < FieldRecord > fields = new ArrayList < > (fieldCount);
    for (int i = 0; i < fieldCount; i++) {
      long nameStringId = readId();
      int type = readUnsignedByte();
      fields.add(new FieldRecord(nameStringId, type));
    }
    return new ClassDumpRecord(
      id, stackTraceSerialNumber, superclassId, classLoaderId, signersId,
      protectionDomainId, instanceSize, staticFields, fields
    );
  }

  public PrimitiveArrayDumpRecord readPrimitiveArrayDumpRecord() {
    long id = readId();
    int stackTraceSerialNumber = readInt();
    int arrayLength = readInt();
    byte type = readUnsignedByte();
    switch (type) {
    case BOOLEAN_TYPE:
      return new BooleanArrayDump(id, stackTraceSerialNumber, readBooleanArray(arrayLength));
    case CHAR_TYPE:
      return new CharArrayDump(id, stackTraceSerialNumber, readCharArray(arrayLength));
    case FLOAT_TYPE:
      return new FloatArrayDump(id, stackTraceSerialNumber, readFloatArray(arrayLength));
    case DOUBLE_TYPE:
      return new DoubleArrayDump(id, stackTraceSerialNumber, readDoubleArray(arrayLength));
    case BYTE_TYPE:
      return new ByteArrayDump(id, stackTraceSerialNumber, readByteArray(arrayLength));
    case SHORT_TYPE:
      return new ShortArrayDump(id, stackTraceSerialNumber, readShortArray(arrayLength));
    case INT_TYPE:
      return new IntArrayDump(id, stackTraceSerialNumber, readIntArray(arrayLength));
    case LONG_TYPE:
      return new LongArrayDump(id, stackTraceSerialNumber, readLongArray(arrayLength));
    default:
      throw new IllegalStateException("Unexpected type "
        type);
    }
  }

  public ObjectArrayDumpRecord readObjectArrayDumpRecord() {
    long id = readId();
    int stackTraceSerialNumber = readInt();
    int arrayLength = readInt();
    long arrayClassId = readId();
    long[] elementIds = readIdArray(arrayLength);
    return new ObjectArrayDumpRecord(id, stackTraceSerialNumber, arrayClassId, elementIds);
  }

  public ValueHolder readValue(int type) {
    switch (type) {
    case PrimitiveType.REFERENCE_HPROF_TYPE:
      return new ReferenceHolder(readId());
    case BOOLEAN_TYPE:
      return new BooleanHolder(readBoolean());
    case CHAR_TYPE:
      return new CharHolder(readChar());
    case FLOAT_TYPE:
      return new FloatHolder(readFloat());
    case DOUBLE_TYPE:
      return new DoubleHolder(readDouble());
    case BYTE_TYPE:
      return new ByteHolder(readByte());
    case SHORT_TYPE:
      return new ShortHolder(readShort());
    case INT_TYPE:
      return new IntHolder(readInt());
    case LONG_TYPE:
      return new LongHolder(readLong());
    default:
      throw new IllegalStateException("Unknown type " + type);
    }
  }

  private int typeSize(int type) {
    return typeSizes.get(type);
  }

  private short readShort() {
    byteReadCount += SHORT_SIZE;
    return source.readShort();
  }

  private int readInt() {
    byteReadCount += INT_SIZE;
    return source.readInt();
  }

  private long[] readIdArray(int arrayLength) {
    return LongStream.range(0, arrayLength).mapToLong(i -> readId()).toArray();
  }

  private boolean[] readBooleanArray(int arrayLength) {
    return IntStream.range(0, arrayLength).mapToBoolean(i -> readByte() != 0).toArray();
  }

  private char[] readCharArray(int arrayLength) {
    return readString(CHAR_SIZE * arrayLength, StandardCharsets.UTF_16BE).toCharArray();
  }

  private String readString(int byteCount, Charset charset) {
    byteReadCount += byteCount;
    return source.readString((long) byteCount, charset);
  }

  private float[] readFloatArray(int arrayLength) {
    return IntStream.range(0, arrayLength).mapToFloat(i -> readFloat()).toArray();
  }

  private double[] readDoubleArray(int arrayLength) {
    return IntStream.range(0, arrayLength).mapToDouble(i -> readDouble()).toArray();
  }

  private short[] readShortArray(int arrayLength) {
    return IntStream.range(0, arrayLength).mapToShort(i -> readShort()).toArray();
  }

  private int[] readIntArray(int arrayLength) {
    return IntStream.range(0, arrayLength).map(i -> readInt()).toArray();
  }

  private long[] readLongArray(int arrayLength) {
    return LongStream.range(0, arrayLength).mapToLong(i -> readLong()).toArray();
  }

  private long readLong() {
    byteReadCount += LONG_SIZE;
    return source.readLong();
  }

  private void skip(long byteCount) {
    byteReadCount += byteCount;
    source.skip(byteCount);
  }

  private byte readByte() {
    byteReadCount += BYTE_SIZE;
    return source.readByte();
  }

  private boolean readBoolean() {
    byteReadCount += BOOLEAN_SIZE;
    return source.readByte() != 0;
  }

  private byte[] readByteArray(int byteCount) {
    byteReadCount += byteCount;
    return source.readByteArray((long) byteCount);
  }

  private char readChar() {
    return readString(CHAR_SIZE, StandardCharsets.UTF_16BE).charAt(0);
  }

  private float readFloat() {
    return Float.intBitsToFloat(readInt());
  }

  private double readDouble() {
    return Double.longBitsToDouble(readLong());
  }

  private long readId() {
    switch (identifierByteSize) {
    case 1:
      return readByte();
    case 2:
      return readShort();
    case 4:
      return readInt();
    case 8:
      return readLong();
    default:
      throw new IllegalArgumentException("ID Length must be 1, 2, 4, or 8");
    }
  }

  private String readUtf8(long byteCount) {
    byteReadCount += byteCount;
    return source.readUtf8(byteCount);
  }

  private long readUnsignedInt() {
    return readInt() & INT_MASK;
  }

  private int readUnsignedByte() {
    return readByte() & BYTE_MASK;
  }

  private int readUnsignedShort() {
    return readShort() & 0xFFFF;
  }

  private void skip(int byteCount) {
    byteReadCount += byteCount;
    source.skip((long) byteCount);
  }

  private void skipInstanceDumpRecord() {
    skip(identifierByteSize + INT_SIZE + identifierByteSize);
    int remainingBytesInInstance = readInt();
    skip(remainingBytesInInstance);
  }
  private boolean exhausted() {
    return source.exhausted();
  }

  private void skipClassDumpRecord() {
    skip(
      identifierByteSize + INT_SIZE + identifierByteSize + identifierByteSize + identifierByteSize + identifierByteSize + identifierByteSize + identifierByteSize + INT_SIZE
    );

    // Skip over the constant pool
    int constantPoolCount = readUnsignedShort();
    for (int i = 0; i < constantPoolCount; i++) {
      // constant pool index
      skip(SHORT_SIZE);
      skip(typeSize(readUnsignedByte()));
    }

    int staticFieldCount = readUnsignedShort();
    for (int i = 0; i < staticFieldCount; i++) {
      skip(identifierByteSize);
      int type = readUnsignedByte();
      skip(typeSize(type));
    }

    int fieldCount = readUnsignedShort();
    skip(fieldCount * (identifierByteSize + BYTE_SIZE));
  }

  private void skipObjectArrayDumpRecord() {
    skip(identifierByteSize + INT_SIZE);
    int arrayLength = readInt();
    skip(identifierByteSize + arrayLength * identifierByteSize);
  }

  private void skipPrimitiveArrayDumpRecord() {
    skip(identifierByteSize + INT_SIZE);
    int arrayLength = readInt();
    int type = readUnsignedByte();
    skip(identifierByteSize + arrayLength * typeSize(type));
  }

  private HeapDumpInfoRecord readHeapDumpInfoRecord() {
    int heapId = readInt();
    return new HeapDumpInfoRecord(heapId, readId());
  }

  private void skipHeapDumpInfoRecord() {
    skip(identifierByteSize + identifierByteSize);
  }
}