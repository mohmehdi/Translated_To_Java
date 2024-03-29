package shark;

import okio.BufferedSource;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public class HprofReader {
  private BufferedSource source;
  private long startByteReadCount;
  private long byteReadCount;
  private int objectIdByteSize;

  private final Map < Integer, Integer > typeSizes = new HashMap < > ();

  public HprofReader(BufferedSource source, long startByteReadCount, int objectIdByteSize) {
    this.source = source;
    this.startByteReadCount = startByteReadCount;
    this.objectIdByteSize = objectIdByteSize;

    typeSizes.put(PrimitiveType.BOOLEAN.hprofType, PrimitiveType.BOOLEAN.byteSize);
    typeSizes.put(PrimitiveType.BYTE.hprofType, PrimitiveType.BYTE.byteSize);
    typeSizes.put(PrimitiveType.CHAR.hprofType, PrimitiveType.CHAR.byteSize);
    typeSizes.put(PrimitiveType.DOUBLE.hprofType, PrimitiveType.DOUBLE.byteSize);
    typeSizes.put(PrimitiveType.FLOAT.hprofType, PrimitiveType.FLOAT.byteSize);
    typeSizes.put(PrimitiveType.INT.hprofType, PrimitiveType.INT.byteSize);
    typeSizes.put(PrimitiveType.LONG.hprofType, PrimitiveType.LONG.byteSize);
    typeSizes.put(PrimitiveType.SHORT.hprofType, PrimitiveType.SHORT.byteSize);
    typeSizes.put(PrimitiveType.REFERENCE_HPROF_TYPE, objectIdByteSize);
  }

  public void readHprofRecords(
    Set < Class < ? extends HprofRecord >> recordTypes,
    OnHprofRecordListener listener
  ) {
    require(byteReadCount == startByteReadCount, "readHprofRecords() should only be called with a brand new Hprof instance");

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
      // type of the record
      int tag = readUnsignedByte();

      // number of microseconds since the time stamp in the header
      skip(intByteSize);

      // number of bytes that follow and belong to this record
      int length = readUnsignedInt();

      switch (tag) {
      case STRING_IN_UTF8:
        if (readStringRecord) {
          int recordPosition = byteReadCount;
          long id = readId();
          int stringLength = length - objectIdByteSize;
          String string = readUtf8(stringLength);
          StringRecord record = new StringRecord(id, string);
          listener.onHprofRecord(recordPosition, record);
        } else {
          skip(length);
        }
        break;
      case LOAD_CLASS:
        if (readLoadClassRecord) {
          int recordPosition = byteReadCount;
          int classSerialNumber = readInt();
          long id = readId();
          int stackTraceSerialNumber = readInt();
          long classNameStringId = readId();
          LoadClassRecord record = new LoadClassRecord(
            classSerialNumber, id, stackTraceSerialNumber, classNameStringId
          );
          listener.onHprofRecord(recordPosition, record);
        } else {
          skip(length);
        }
        break;
      case STACK_FRAME:
        if (readStackFrameRecord) {
          int recordPosition = byteReadCount;
          StackFrameRecord record = new StackFrameRecord(
            readId(), readId(), readId(), readId(), readInt(), readInt()
          );
          listener.onHprofRecord(recordPosition, record);
        } else {
          skip(length);
        }
        break;
      case STACK_TRACE:
        if (readStackTraceRecord) {
          int recordPosition = byteReadCount;
          int stackTraceSerialNumber = readInt();
          int threadSerialNumber = readInt();
          int frameCount = readInt();
          long[] stackFrameIds = readIdArray(frameCount);
          StackTraceRecord record = new StackTraceRecord(
            stackTraceSerialNumber, threadSerialNumber, stackFrameIds
          );
          listener.onHprofRecord(recordPosition, record);
        } else {
          skip(length);
        }
        break;
      case HEAP_DUMP:
      case HEAP_DUMP_SEGMENT:
        int heapDumpStart = byteReadCount;
        int previousTag = 0;
        while (byteReadCount - heapDumpStart < length) {
          int heapDumpTag = readUnsignedByte();
          switch (heapDumpTag) {
            // Handle various root types and object dump records
            // (code omitted for brevity)
          }
          previousTag = heapDumpTag;
        }
        break;
      case HEAP_DUMP_END:
        if (readHeapDumpEndRecord) {
          int recordPosition = byteReadCount;
          HeapDumpEndRecord record = HeapDumpEndRecord.getInstance();
          listener.onHprofRecord(recordPosition, record);
        }
        break;
      default:
        skip(length);
        break;
      }
    }
  }
  public InstanceDumpRecord readInstanceDumpRecord() {
    long id = readId();
    int stackTraceSerialNumber = readInt();
    long classId = readId();
    int remainingBytesInInstance = readInt();
    byte[] fieldValues = readByteArray(remainingBytesInInstance);
    return new InstanceDumpRecord(id, stackTraceSerialNumber, classId, fieldValues);
  }

  public ClassDumpRecord readClassDumpRecord() {
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
    for (int i = 0; i < constantPoolCount; i++) {
      skip(SHORT_SIZE);
      skip(typeSize(readUnsignedByte()));
    }

    int staticFieldCount = readUnsignedShort();
    ArrayList < StaticFieldRecord > staticFields = new ArrayList < > (staticFieldCount);
    for (int i = 0; i < staticFieldCount; i++) {
      long nameStringId = readId();
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
      id, stackTraceSerialNumber, superClassId, classLoaderId, signersId,
      protectionDomainId, instanceSize, staticFields, fields
    );
  }
  private boolean exhausted() {
    return source.exhausted();
  }

  public PrimitiveArrayDumpRecord readPrimitiveArrayDumpRecord() {
    long id = readId();
    int stackTraceSerialNumber = readInt();
    int arrayLength = readInt();
    int type = readUnsignedByte();
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
      throw new IllegalStateException("Unexpected type " + type);
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
  private boolean readBoolean() {
    byteReadCount += BOOLEAN_SIZE;
    return source.readByte() != 0;
  }

  private byte[] readByteArray(int byteCount) {
    byteReadCount += byteCount;
    return source.readByteArray(byteCount);
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
    byteReadCount += CHAR_SIZE * arrayLength;
    return source.readString(CHAR_SIZE * arrayLength, Charset.forName("UTF-16BE")).toCharArray();
  }

  private String readString(int byteCount, Charset charset) {
    byteReadCount += byteCount;
    return source.readString(byteCount, charset);
  }

  private float[] readFloatArray(int arrayLength) {
    float[] array = new float[arrayLength];
    for (int i = 0; i < arrayLength; i++) {
      array[i] = Float.intBitsToFloat(readInt());
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
  private byte readByte() {
    byteReadCount += BYTE_SIZE;
    return source.readByte();
  }

  private long readLong() {
    byteReadCount += LONG_SIZE;
    return source.readLong();
  }

  private long readId() {
    if (objectIdByteSize == 1) {
      return (long) readByte();
    } else if (objectIdByteSize == 2) {
      return (long) readShort();
    } else if (objectIdByteSize == 4) {
      return (long) readInt();
    } else if (objectIdByteSize == 8) {
      return readLong();
    } else {
      throw new IllegalArgumentException("ID Length must be 1, 2, 4, or 8");
    }
  }

  private String readUtf8(long byteCount) {
    byteReadCount += byteCount;
    return source.readUtf8(byteCount);
  }

  private int readUnsignedInt() {
    return readInt() & 0xffffffff;
  }

  private int readUnsignedByte() {
    return readByte() & 0xff;
  }

  private int readUnsignedShort() {
    return readShort() & 0xffff;
  }

  private void skip(long byteCount) {
    byteReadCount += byteCount;
    source.skip(byteCount);
  }

  private void skip(int byteCount) {
    skip((long) byteCount);
  }
  private void skipInstanceDumpRecord() {
    skip(objectIdByteSize + INT_SIZE + objectIdByteSize);
    int remainingBytesInInstance = readInt();
    skip(remainingBytesInInstance);
  }

  private void skipClassDumpRecord() {
    skip(
      objectIdByteSize + INT_SIZE + objectIdByteSize + objectIdByteSize + objectIdByteSize +
      objectIdByteSize + objectIdByteSize + objectIdByteSize + objectIdByteSize + INT_SIZE
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
      skip(objectIdByteSize);
      int type = readUnsignedByte();
      skip(typeSize(type));
    }

    int fieldCount = readUnsignedShort();
    skip(fieldCount * (objectIdByteSize + BYTE_SIZE));
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

  private HeapDumpInfoRecord readHeapDumpInfoRecord() {
    int heapId = readInt();
    return new HeapDumpInfoRecord(heapId, readId());
  }

  private void skipHeapDumpInfoRecord() {
    skip(objectIdByteSize + objectIdByteSize);
  }

  private static final int BOOLEAN_SIZE = PrimitiveType.BOOLEAN.byteSize;
  private static final int CHAR_SIZE = PrimitiveType.CHAR.byteSize;
  private static final int FLOAT_SIZE = PrimitiveType.FLOAT.byteSize;
  private static final int DOUBLE_SIZE = PrimitiveType.DOUBLE.byteSize;
  private static final int BYTE_SIZE = PrimitiveType.BYTE.byteSize;
  private static final int SHORT_SIZE = PrimitiveType.SHORT.byteSize;
  private static final int INT_SIZE = PrimitiveType.INT.byteSize;
  private static final int LONG_SIZE = PrimitiveType.LONG.byteSize;

  private static final int BOOLEAN_TYPE = PrimitiveType.BOOLEAN.hprofType;
  private static final int CHAR_TYPE = PrimitiveType.CHAR.hprofType;
  private static final int FLOAT_TYPE = PrimitiveType.FLOAT.hprofType;
  private static final int DOUBLE_TYPE = PrimitiveType.DOUBLE.hprofType;
  private static final int BYTE_TYPE = PrimitiveType.BYTE.hprofType;
  private static final int SHORT_TYPE = PrimitiveType.SHORT.hprofType;
  private static final int INT_TYPE = PrimitiveType.INT.hprofType;
  private static final int LONG_TYPE = PrimitiveType.LONG.hprofType;
  private static final long INT_MASK = 0xffffffff L;
  private static final int BYTE_MASK = 0xff;
  private static final int STRING_IN_UTF8 = 0x01;
  private static final int LOAD_CLASS = 0x02;
  private static final int UNLOAD_CLASS = 0x03;
  private static final int STACK_FRAME = 0x04;
  private static final int STACK_TRACE = 0x05;
  private static final int ALLOC_SITES = 0x06;
  private static final int HEAP_SUMMARY = 0x07;
  private static final int ROOT_UNKNOWN = 0xff;
  private static final int ROOT_JNI_GLOBAL = 0x01;
  private static final int ROOT_JNI_LOCAL = 0x02;
  private static final int ROOT_JAVA_FRAME = 0x03;
  private static final int ROOT_NATIVE_STACK = 0x04;
  private static final int ROOT_STICKY_CLASS = 0x05;
  private static final int ROOT_THREAD_BLOCK = 0x06;
  private static final int ROOT_MONITOR_USED = 0x07;
  private static final int ROOT_THREAD_OBJECT = 0x08;
}