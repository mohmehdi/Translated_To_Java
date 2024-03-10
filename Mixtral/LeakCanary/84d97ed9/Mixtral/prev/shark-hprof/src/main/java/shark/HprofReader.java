package shark;

import okio.BufferedSource;
import shark.GcRoot.Debugger;
import shark.GcRoot.Finalizing;
import shark.GcRoot.InternedString;
import shark.GcRoot.JavaFrame;
import shark.GcRoot.JniGlobal;
import shark.GcRoot.JniLocal;
import shark.GcRoot.JniMonitor;
import shark.GcRoot.MonitorUsed;
import shark.GcRoot.NativeStack;
import shark.GcRoot.ReferenceCleanup;
import shark.GcRoot.StickyClass;
import shark.GcRoot.ThreadBlock;
import shark.GcRoot.ThreadObject;
import shark.GcRoot.Unknown;
import shark.GcRoot.Unreachable;
import shark.GcRoot.VmInternal;
import shark.HprofRecord.HeapDumpEndRecord;
import shark.HprofRecord.HeapDumpRecord;
import shark.HprofRecord.HeapDumpRecord.GcRootRecord;
import shark.HprofRecord.HeapDumpRecord.HeapDumpInfoRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassDumpRecord.FieldRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassDumpRecord.StaticFieldRecord;
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
import shark.HprofRecord.LoadClassRecord;
import shark.HprofRecord.StackFrameRecord;
import shark.HprofRecord.StackTraceRecord;
import shark.HprofRecord.StringRecord;
import shark.PrimitiveType.BOOLEAN;
import shark.PrimitiveType.BYTE;
import shark.PrimitiveType.CHAR;
import shark.PrimitiveType.DOUBLE;
import shark.PrimitiveType.FLOAT;
import shark.PrimitiveType.INT;
import shark.PrimitiveType.LONG;
import shark.PrimitiveType.SHORT;
import shark.ValueHolder.BooleanHolder;
import shark.ValueHolder.ByteHolder;
import shark.ValueHolder.CharHolder;
import shark.ValueHolder.DoubleHolder;
import shark.ValueHolder.FloatHolder;
import shark.ValueHolder.IntHolder;
import shark.ValueHolder.LongHolder;
import shark.ValueHolder.ReferenceHolder;
import shark.ValueHolder.ShortHolder;
import java.nio.charset.Charset;

public class HprofReader {
  private BufferedSource source;
  private long byteReadCount;
  private final int[] typeSizes;

  public HprofReader(BufferedSource source, long startByteReadCount, int objectIdByteSize) {
    this.source = source;
    this.byteReadCount = startByteReadCount;
    this.typeSizes = new int[PrimitiveType.values().length];
    this.typeSizes[PrimitiveType.BOOLEAN.hprofType] = BOOLEAN.byteSize;
    this.typeSizes[PrimitiveType.BYTE.hprofType] = BYTE.byteSize;
    this.typeSizes[PrimitiveType.CHAR.hprofType] = CHAR.byteSize;
    this.typeSizes[PrimitiveType.DOUBLE.hprofType] = DOUBLE.byteSize;
    this.typeSizes[PrimitiveType.FLOAT.hprofType] = FLOAT.byteSize;
    this.typeSizes[PrimitiveType.INT.hprofType] = INT.byteSize;
    this.typeSizes[PrimitiveType.LONG.hprofType] = LONG.byteSize;
    this.typeSizes[PrimitiveType.REFERENCE_HPROF_TYPE] = objectIdByteSize;
  }

  private void readHprofRecords(
    Set < Class < ? extends HprofRecord >> recordTypes,
    OnHprofRecordListener listener) {
    require(byteReadCount == startByteReadCount);
    boolean readAllRecords = HprofRecord.class.isAssignableFrom(recordTypes.iterator().next());
    boolean readStringRecord = readAllRecords || StringRecord.class.isAssignableFrom(recordTypes.iterator().next());
    boolean readLoadClassRecord = readAllRecords || LoadClassRecord.class.isAssignableFrom(recordTypes.iterator().next());
    boolean readHeapDumpEndRecord = readAllRecords || HeapDumpEndRecord.class.isAssignableFrom(recordTypes.iterator().next());
    boolean readStackFrameRecord = readAllRecords || StackFrameRecord.class.isAssignableFrom(recordTypes.iterator().next());
    boolean readStackTraceRecord = readAllRecords || StackTraceRecord.class.isAssignableFrom(recordTypes.iterator().next());
    boolean readAllHeapDumpRecords = readAllRecords || HeapDumpRecord.class.isAssignableFrom(recordTypes.iterator().next());
    boolean readGcRootRecord = readAllHeapDumpRecords || GcRootRecord.class.isAssignableFrom(recordTypes.iterator().next());
    boolean readHeapDumpInfoRecord = readAllRecords || HeapDumpInfoRecord.class.isAssignableFrom(recordTypes.iterator().next());
    boolean readAllObjectRecords = readAllHeapDumpRecords || ObjectRecord.class.isAssignableFrom(recordTypes.iterator().next());
    boolean readClassDumpRecord = readAllObjectRecords || ClassDumpRecord.class.isAssignableFrom(recordTypes.iterator().next());
    boolean readInstanceDumpRecord = readAllObjectRecords || InstanceDumpRecord.class.isAssignableFrom(recordTypes.iterator().next());
    boolean readObjectArrayDumpRecord = readAllObjectRecords || ObjectArrayDumpRecord.class.isAssignableFrom(recordTypes.iterator().next());
    boolean readPrimitiveArrayDumpRecord = readAllObjectRecords || PrimitiveArrayDumpRecord.class.isAssignableFrom(recordTypes.iterator().next());
    int intByteSize = INT.byteSize;

    while (!exhausted()) {
      int tag = readUnsignedByte();
      skip(intByteSize);
      long length = readUnsignedInt();

      switch (tag) {
      case STRING_IN_UTF8:
        if (readStringRecord) {
          long recordPosition = byteReadCount;
          long id = readId();
          int stringLength = (int)(length - objectIdByteSize);
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
          LoadClassRecord record = new LoadClassRecord(classSerialNumber, id, stackTraceSerialNumber, classNameStringId);
          listener.onHprofRecord(recordPosition, record);
        } else {
          skip(length);
        }
        break;
      case STACK_FRAME:
        if (readStackFrameRecord) {
          long recordPosition = byteReadCount;
          long record = new StackFrameRecord(
            readId(),
            readId(),
            readId(),
            readId(),
            readInt(),
            readInt()
          );
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
            stackTraceSerialNumber,
            threadSerialNumber,
            stackFrameIds
          );
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
              skip(objectIdByteSize);
            }
            break;
          case ROOT_JNI_GLOBAL:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord record = new GcRootRecord(new JniGlobal(readId(), readId()));
              listener.onHprofRecord(recordPosition, record);
            } else {
              skip(objectIdByteSize + objectIdByteSize);
            }
            break;
          case ROOT_JNI_LOCAL:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord record = new GcRootRecord(new JniLocal(
                readId(),
                readInt(),
                readInt()
              ));
              listener.onHprofRecord(recordPosition, record);
            } else {
              skip(objectIdByteSize + intByteSize + intByteSize);
            }
            break;
          case ROOT_JAVA_FRAME:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord record = new GcRootRecord(new JavaFrame(
                readId(),
                readInt(),
                readInt()
              ));
              listener.onHprofRecord(recordPosition, record);
            } else {
              skip(objectIdByteSize + intByteSize + intByteSize);
            }
            break;
          case ROOT_NATIVE_STACK:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord record = new GcRootRecord(new NativeStack(readId(), readInt()));
              listener.onHprofRecord(recordPosition, record);
            } else {
              skip(objectIdByteSize + intByteSize);
            }
            break;
          case ROOT_STICKY_CLASS:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord record = new GcRootRecord(new StickyClass(readId()));
              listener.onHprofRecord(recordPosition, record);
            } else {
              skip(objectIdByteSize);
            }
            break;
          case ROOT_THREAD_BLOCK:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord record = new GcRootRecord(new ThreadBlock(readId(), readInt()));
              listener.onHprofRecord(recordPosition, record);
            } else {
              skip(objectIdByteSize + intByteSize);
            }
            break;
          case ROOT_MONITOR_USED:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord record = new GcRootRecord(new MonitorUsed(readId()));
              listener.onHprofRecord(recordPosition, record);
            } else {
              skip(objectIdByteSize);
            }
            break;
          case ROOT_THREAD_OBJECT:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord record = new GcRootRecord(new ThreadObject(
                readId(),
                readInt(),
                readInt()
              ));
              listener.onHprofRecord(recordPosition, record);
            } else {
              skip(objectIdByteSize + intByteSize + intByteSize);
            }
            break;
          case ROOT_INTERNED_STRING:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord record = new GcRootRecord(new InternedString(readId()));
              listener.onHprofRecord(recordPosition, record);
            } else {
              skip(objectIdByteSize);
            }
            break;
          case ROOT_FINALIZING:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord record = new GcRootRecord(new Finalizing(readId()));
              listener.onHprofRecord(recordPosition, record);
            } else {
              skip(objectIdByteSize);
            }
            break;
          case ROOT_DEBUGGER:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord record = new GcRootRecord(new Debugger(readId()));
              listener.onHprofRecord(recordPosition, record);
            } else {
              skip(objectIdByteSize);
            }
            break;
          case ROOT_REFERENCE_CLEANUP:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord record = new GcRootRecord(new ReferenceCleanup(readId()));
              listener.onHprofRecord(recordPosition, record);
            } else {
              skip(objectIdByteSize);
            }
            break;
          case ROOT_VM_INTERNAL:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord record = new GcRootRecord(new VmInternal(readId()));
              listener.onHprofRecord(recordPosition, record);
            } else {
              skip(objectIdByteSize);
            }
            break;
          case ROOT_JNI_MONITOR:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord record = new GcRootRecord(new JniMonitor(
                readId(),
                readInt(),
                readInt()
              ));
              listener.onHprofRecord(recordPosition, record);
            } else {
              skip(objectIdByteSize + intByteSize + intByteSize);
            }
            break;
          case ROOT_UNREACHABLE:
            if (readGcRootRecord) {
              long recordPosition = byteReadCount;
              GcRootRecord record = new GcRootRecord(new Unreachable(readId()));
              listener.onHprofRecord(recordPosition, record);
            } else {
              skip(objectIdByteSize);
            }
            break;
          case CLASS_DUMP:
            if (readClassDumpRecord) {
              long recordPosition = byteReadCount;
              ClassDumpRecord record = readClassDumpRecord();
              listener.onHprofRecord(recordPosition, record);
            } else {
              skipClassDumpRecord();
            }
            break;
          case INSTANCE_DUMP:
            if (readInstanceDumpRecord) {
              long recordPosition = byteReadCount;
              InstanceDumpRecord record = readInstanceDumpRecord();
              listener.onHprofRecord(recordPosition, record);
            } else {
              skipInstanceDumpRecord();
            }
            break;
          case OBJECT_ARRAY_DUMP:
            if (readObjectArrayDumpRecord) {
              long recordPosition = byteReadCount;
              ObjectArrayDumpRecord record = readObjectArrayDumpRecord();
              listener.onHprofRecord(recordPosition, record);
            } else {
              skipObjectArrayDumpRecord();
            }
            break;
          case PRIMITIVE_ARRAY_DUMP:
            if (readPrimitiveArrayDumpRecord) {
              long recordPosition = byteReadCount;
              PrimitiveArrayDumpRecord record = readPrimitiveArrayDumpRecord();
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
              HeapDumpInfoRecord record = readHeapDumpInfoRecord();
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
      }
    }

    private ClassDumpRecord readClassDumpRecord() {
      long id = readId();
      int stackTraceSerialNumber = readInt();
      long superClassId = readId();
      long classLoaderId = readId();
      long signersId = readId();
      long protectionDomainId = readId();
      readId();
      readId();
      int instanceSize = readInt();
      int constantPoolCount = readUnsignedShort();
      ArrayList < StaticFieldRecord > staticFields = new ArrayList < > (constantPoolCount);
      for (int i = 0; i < constantPoolCount; i++) {
        int nameStringId = readId();
        int type = readUnsignedByte();
        ValueHolder value = readValue(type);
        staticFields.add(new StaticFieldRecord(nameStringId, type, value));
      }
      int fieldCount = readUnsignedShort();
      ArrayList < FieldRecord > fields = new ArrayList < > (fieldCount);
      for (int i = 0; i < fieldCount; i++) {
        fields.add(new FieldRecord(readId(), readUnsignedByte()));
      }
      return new ClassDumpRecord(
        id,
        stackTraceSerialNumber,
        superClassId,
        classLoaderId,
        signersId,
        protectionDomainId,
        instanceSize,
        staticFields,
        fields
      );
    }

    private InstanceDumpRecord readInstanceDumpRecord() {
      long id = readId();
      int stackTraceSerialNumber = readInt();
      long classId = readId();
      int remainingBytesInInstance = readInt();
      byte[] fieldValues = readByteArray(remainingBytesInInstance);
      return new InstanceDumpRecord(
        id,
        stackTraceSerialNumber,
        classId,
        fieldValues
      );
    }

    private PrimitiveArrayDumpRecord readPrimitiveArrayDumpRecord() {
      long id = readId();
      int stackTraceSerialNumber = readInt();
      int arrayLength = readInt();
      int type = readUnsignedByte();
      switch (type) {
      case BOOLEAN_TYPE:
        return new PrimitiveArrayDumpRecord.BooleanArrayDump(id, stackTraceSerialNumber, readBooleanArray(arrayLength));
      case BYTE_TYPE:
        return new PrimitiveArrayDumpRecord.ByteArrayDump(id, stackTraceSerialNumber, readByteArray(arrayLength));
      case CHAR_TYPE:
        return new PrimitiveArrayDumpRecord.CharArrayDump(id, stackTraceSerialNumber, readCharArray(arrayLength));
      case DOUBLE_TYPE:
        return new PrimitiveArrayDumpRecord.DoubleArrayDump(id, stackTraceSerialNumber, readDoubleArray(arrayLength));
      case FLOAT_TYPE:
        return new PrimitiveArrayDumpRecord.FloatArrayDump(id, stackTraceSerialNumber, readFloatArray(arrayLength));
      case INT_TYPE:
        return new PrimitiveArrayDumpRecord.IntArrayDump(id, stackTraceSerialNumber, readIntArray(arrayLength));
      case LONG_TYPE:
        return new PrimitiveArrayDumpRecord.LongArrayDump(id, stackTraceSerialNumber, readLongArray(arrayLength));
      case SHORT_TYPE:
        return new PrimitiveArrayDumpRecord.ShortArrayDump(id, stackTraceSerialNumber, readShortArray(arrayLength));
      default:
        throw new IllegalStateException("Unexpected type " + type);
      }
    }

    private ObjectArrayDumpRecord readObjectArrayDumpRecord() {
      long id = readId();
      int stackTraceSerialNumber = readInt();
      int arrayLength = readInt();
      long arrayClassId = readId();
      long[] elementIds = readIdArray(arrayLength);
      return new ObjectArrayDumpRecord(
        id,
        stackTraceSerialNumber,
        arrayClassId,
        elementIds
      );
    }

    private ValueHolder readValue(int type) {
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
      return typeSizes[type];
    }

    private boolean exhausted() {
      return source.exhausted();
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
      return source.readByteArray(byteCount);
    }
    private String readString(int byteCount, Charset charset) {
        byteReadCount += byteCount;
        return StandardCharsets.UTF_8.decode(source.read(byteCount)).toString();
    }
    private char readChar() {
      return readString(CHAR_SIZE, Charsets.UTF_16BE).charAt(0);
    }

    private float readFloat() {
      return Float.intBitsToFloat(readInt());
    }
    private long readLong() {
        incrementByteReadCount(LONG_SIZE);
        return source.readLong();
    }
    private double readDouble() {
      return Double.longBitsToDouble(readLong());
    }

    private long readId() {
      switch (objectIdByteSize) {
      case 1:
        return readByte() & 0xFF L;
      case 2:
        return readShort() & 0xFFFF L;
      case 4:
        return readInt() & 0xFFFFFFFF L;
      case 8:
        return readLong();
      default:
        throw new IllegalArgumentException("ID Length must be 1, 2, 4, or 8");
      }
    }

   private String readUtf8(long byteCount) {
    byteReadCount += byteCount;
    return StandardCharsets.UTF_8.decode(source.read(byteCount)).toString();
}

    private long readUnsignedInt() {
      return readInt() & 0xFFFFFFFF L;
    }

    private int readUnsignedByte() {
      return readByte() & 0xFF;
    }

    private int readUnsignedShort() {
      return readShort() & 0xFFFF;
    }

    private void skip(int byteCount) {
      byteReadCount += byteCount;
      source.skip(byteCount);
    }

    private void skipInstanceDumpRecord() {
      skip(objectIdByteSize + INT_SIZE + objectIdByteSize);
      int remainingBytesInInstance = readInt();
      skip(remainingBytesInInstance);
    }

    private void skipClassDumpRecord() {
      skip(
        objectIdByteSize + INT_SIZE + objectIdByteSize + objectIdByteSize + objectIdByteSize + objectIdByteSize + objectIdByteSize + objectIdByteSize + INT_SIZE
      );
      int constantPoolCount = readUnsignedShort();
      for (int i = 0; i < constantPoolCount; i++) {
        skip(SHORT_SIZE);
        skip(typeSize(readUnsignedByte()));
      }
      int staticFieldCount = readUnsignedShort();
      for (int i = 0; i < staticFieldCount; i++) {
        skip(objectIdByteSize);
        int type = readUnsignedByte();
        skip(typeSize(type));
      }
      int fieldCount = readUnsignedShort();
      for (int i = 0; i < fieldCount; i++) {
        skip(objectIdByteSize + BYTE_SIZE);
      }
    }

    private void skipObjectArrayDumpRecord() {
      skip(objectIdByteSize + INT_SIZE);
      int arrayLength = readInt();
      skip(objectIdByteSize + arrayLength * objectIdByteSize);
    }

    private void skipPrimitiveArrayDumpRecord() {
      skip(objectIdByteSize + INT_SIZE);
      int arrayLength = readInt();
      int type = readUnsignedByte();
      skip(objectIdByteSize + arrayLength * typeSize(type));
    }

    private long[] readIdArray(int arrayLength) {
      long[] array = new long[arrayLength];
      for (int i = 0; i < arrayLength; i++) {
        array[i] = readId();
      }
      return array;
    }

    private boolean[] readBooleanArray(int arrayLength) {
      boolean[] array = new boolean[arrayLength];
      for (int i = 0; i < arrayLength; i++) {
        array[i] = readByte() != 0;
      }
      return array;
    }

    private char[] readCharArray(int arrayLength) {
      char[] array = new char[arrayLength];
      for (int i = 0; i < arrayLength; i++) {
        array[i] = readChar();
      }
      return array;
    }

    private float[] readFloatArray(int arrayLength) {
      float[] array = new float[arrayLength];
      for (int i = 0; i < arrayLength; i++) {
        array[i] = readFloat();
      }
      return array;
    }

    private double[] readDoubleArray(int arrayLength) {
      double[] array = new double[arrayLength];
      for (int i = 0; i < arrayLength; i++) {
        array[i] = readDouble();
      }
      return array;
    }
    private int readInt() {
    incrementByteReadCount(INT_SIZE);
    return source.readInt();
}

    private short readShort() {
        incrementByteReadCount(SHORT_SIZE);
        return source.readShort();
    }
    private short[] readShortArray(int arrayLength) {
      short[] array = new short[arrayLength];
      for (int i = 0; i < arrayLength; i++) {
        array[i] = readShort();
      }
      return array;
    }

    private int[] readIntArray(int arrayLength) {
      int[] array = new int[arrayLength];
      for (int i = 0; i < arrayLength; i++) {
        array[i] = readInt();
      }
      return array;
    }

    private long[] readLongArray(int arrayLength) {
      long[] array = new long[arrayLength];
      for (int i = 0; i < arrayLength; i++) {
        array[i] = readLong();
      }
      return array;
    }

    private HeapDumpInfoRecord readHeapDumpInfoRecord() {
      int heapId = readInt();
      long heapNameStringId = readId();
      return new HeapDumpInfoRecord(heapId, heapNameStringId);
    }

    private void skipHeapDumpInfoRecord() {
      skip(objectIdByteSize + objectIdByteSize);
    }

  }

      private static final int BOOLEAN_SIZE = Boolean.BYTES;
    private static final int CHAR_SIZE = Character.BYTES;
    private static final int FLOAT_SIZE = Float.BYTES;
    private static final int DOUBLE_SIZE = Double.BYTES;
    private static final int BYTE_SIZE = Byte.BYTES;
    private static final int SHORT_SIZE = Short.BYTES;
    private static final int INT_SIZE = Integer.BYTES;
    private static final int LONG_SIZE = Long.BYTES;

    private static final sun.misc.ClassLoader.Type BOOLEAN_TYPE = sun.misc.ClassLoader.Type.BOOLEAN;
    private static final sun.misc.ClassLoader.Type CHAR_TYPE = sun.misc.ClassLoader.Type.CHAR;
    private static final sun.misc.ClassLoader.Type FLOAT_TYPE = sun.misc.ClassLoader.Type.FLOAT;
    private static final sun.misc.ClassLoader.Type DOUBLE_TYPE = sun.misc.ClassLoader.Type.DOUBLE;
    private static final sun.misc.ClassLoader.Type BYTE_TYPE = sun.misc.ClassLoader.Type.BYTE;
    private static final sun.misc.ClassLoader.Type SHORT_TYPE = sun.misc.ClassLoader.Type.SHORT;
    private static final sun.misc.ClassLoader.Type INT_TYPE = sun.misc.ClassLoader.Type.INT;
    private static final sun.misc.ClassLoader.Type LONG_TYPE = sun.misc.ClassLoader.Type.LONG;

    private static final long INT_MASK = 0xffffffffL;
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

}