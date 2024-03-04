

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
import shark.HprofRecord.HeapDumpRecord.GcRootRecord;
import shark.HprofRecord.HeapDumpRecord.HeapDumpInfoRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassDumpRecord.FieldRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassDumpRecord.StaticFieldRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassSkipContentRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.InstanceDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.InstanceSkipContentRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ObjectArrayDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ObjectArraySkipContentRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.BooleanArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.ByteArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.CharArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.DoubleArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.FloatArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.IntArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.LongArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.ShortArrayDump;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArraySkipContentRecord;
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
import java.nio.charset.StandardCharsets;

public class HprofRecordReader {

    private long bytesRead;
    private final int[] typeSizes;
    private final HprofHeader header;
    private final BufferedSource source;

    public HprofRecordReader(HprofHeader header, BufferedSource source) {
        this.header = header;
        this.source = source;
        this.bytesRead = 0;

        var typeSizesMap = new HashMap<PrimitiveType, Integer>();
        typeSizesMap.put(PrimitiveType.BOOLEAN, BOOLEAN.byteSize);
        typeSizesMap.put(PrimitiveType.BYTE, BYTE.byteSize);
        typeSizesMap.put(PrimitiveType.CHAR, CHAR.byteSize);
        typeSizesMap.put(PrimitiveType.DOUBLE, DOUBLE.byteSize);
        typeSizesMap.put(PrimitiveType.FLOAT, FLOAT.byteSize);
        typeSizesMap.put(PrimitiveType.INT, INT.byteSize);
        typeSizesMap.put(PrimitiveType.LONG, LONG.byteSize);
        typeSizesMap.put(PrimitiveType.REFERENCE, header.identifierByteSize);
        typeSizesMap.put(PrimitiveType.SHORT, SHORT.byteSize);

        int maxKey = typeSizesMap.keySet().stream().mapToInt(PrimitiveType::hprofType).max().orElse(0);

        this.typeSizes = new int[maxKey + 1];
        for (int key : typeSizesMap.keySet()) {
            this.typeSizes[key.hprofType] = typeSizesMap.get(key);
        }
    }

    public int sizeOf(int type) {
        return typeSizes[type];
    }

    public StringRecord readStringRecord(long length) {
        long id = readId();
        String string = readUtf8(length - header.identifierByteSize);
        return new StringRecord(id, string);
    }

    public void LoadClassRecord$read() {
        classSerialNumber = readInt();
        id = readId();
        stackTraceSerialNumber = readInt();
        classNameStringId = readId();
    }

    public StackFrameRecord readStackFrameRecord() {
        long id = readId();
        long methodNameStringId = readId();
        long methodSignatureStringId = readId();
        long sourceFileNameStringId = readId();
        int classSerialNumber = readInt();
        int lineNumber = readInt();
        return new StackFrameRecord(id, methodNameStringId, methodSignatureStringId, sourceFileNameStringId, classSerialNumber, lineNumber);
    }

    public StackTraceRecord readStackTraceRecord() {
        int stackTraceSerialNumber = readInt();
        int threadSerialNumber = readInt();
        long[] stackFrameIds = readIdArray(readInt());
        return new StackTraceRecord(stackTraceSerialNumber, threadSerialNumber, stackFrameIds);
    }

    public GcRootRecord readUnknownGcRootRecord() {
        long id = readId();
        return new GcRootRecord(new Unknown(id));
    }

    public GcRootRecord readJniGlobalGcRootRecord() {
        long id = readId();
        long jniGlobalRefId = readId();
        return new GcRootRecord(new JniGlobal(id, jniGlobalRefId));
    }

    public GcRootRecord readJniLocalGcRootRecord() {
        long id = readId();
        int threadSerialNumber = readInt();
        int frameNumber = readInt();
        return new GcRootRecord(new JniLocal(id, threadSerialNumber, frameNumber));
    }

    public GcRootRecord readJavaFrameGcRootRecord() {
        long id = readId();
        int threadSerialNumber = readInt();
        int frameNumber = readInt();
        return new GcRootRecord(new JavaFrame(id, threadSerialNumber, frameNumber));
    }

    public GcRootRecord readNativeStackGcRootRecord() {
        long id = readId();
        int threadSerialNumber = readInt();
        return new GcRootRecord(new NativeStack(id, threadSerialNumber));
    }

    public GcRootRecord readStickyClassGcRootRecord() {
        long id = readId();
        return new GcRootRecord(new StickyClass(id));
    }

    public GcRootRecord readThreadBlockGcRootRecord() {
        long id = readId();
        int threadSerialNumber = readInt();
        return new GcRootRecord(new ThreadBlock(id, threadSerialNumber));
    }

    public GcRootRecord readMonitorUsedGcRootRecord() {
        long id = readId();
        return new GcRootRecord(new MonitorUsed(id));
    }

    public GcRootRecord readThreadObjectGcRootRecord() {
        long id = readId();
        int threadSerialNumber = readInt();
        int stackTraceSerialNumber = readInt();
        return new GcRootRecord(new ThreadObject(id, threadSerialNumber, stackTraceSerialNumber));
    }

    public GcRootRecord readInternedStringGcRootRecord() {
        long id = readId();
        return new GcRootRecord(new InternedString(id));
    }

    public GcRootRecord readFinalizingGcRootRecord() {
        long id = readId();
        return new GcRootRecord(new Finalizing(id));
    }

    public GcRootRecord readDebuggerGcRootRecord() {
        long id = readId();
        return new GcRootRecord(new Debugger(id));
    }

    public GcRootRecord readReferenceCleanupGcRootRecord() {
        long id = readId();
        return new GcRootRecord(new ReferenceCleanup(id));
    }

    public GcRootRecord readVmInternalGcRootRecord() {
        long id = readId();
        return new GcRootRecord(new VmInternal(id));
    }

    public GcRootRecord readJniMonitorGcRootRecord() {
        long id = readId();
        int stackTraceSerialNumber = readInt();
        int stackDepth = readInt();
        return new GcRootRecord(new JniMonitor(id, stackTraceSerialNumber, stackDepth));
    }

    public GcRootRecord readUnreachableGcRootRecord() {
        long id = readId();
        return new GcRootRecord(new Unreachable(id));
    }

    public InstanceDumpRecord readInstanceDumpRecord() {
        long id = readId();
        int stackTraceSerialNumber = readInt();
        long classId = readId();
        int remainingBytesInInstance = readInt();
        byte[] fieldValues = readByteArray(remainingBytesInInstance);
        return new InstanceDumpRecord(id, stackTraceSerialNumber, classId, fieldValues);
    }

    public HeapDumpInfoRecord readHeapDumpInfoRecord() {
        int heapId = readInt();
        long heapNameStringId = readId();
        return new HeapDumpInfoRecord(heapId, heapNameStringId);
    }

    public ClassDumpRecord readClassDumpRecord() {
        long id = readId();

        int stackTraceSerialNumber = readInt();
        long superclassId = readId();

        long classLoaderId = readId();

        long signersId = readId();

        long protectionDomainId = readId();

        readId();

        readId();

        int instanceSize = readInt();

        short constantPoolCount = readUnsignedShort();
        for (int i = 0; i < constantPoolCount; i++) {
            skip(SHORT_SIZE);
            skip(typeSizes[readUnsignedByte()]);
        }

        short staticFieldCount = readUnsignedShort();
        ArrayList<StaticFieldRecord> staticFields = new ArrayList<>(staticFieldCount);
        for (int i = 0; i < staticFieldCount; i++) {
            long nameStringId = readId();
            byte type = readUnsignedByte();
            ValueHolder value = readValue(type);

            staticFields.add(new StaticFieldRecord(nameStringId, type, value));
        }

        short fieldCount = readUnsignedShort();
        ArrayList<FieldRecord> fields = new ArrayList<>(fieldCount);
        for (int i = 0; i < fieldCount; i++) {
            fields.add(new FieldRecord(readId(), readUnsignedByte()));
        }

        return new ClassDumpRecord(id, stackTraceSerialNumber, superclassId, classLoaderId, signersId, protectionDomainId, instanceSize, staticFields, fields);
    }

    public PrimitiveArrayDumpRecord readPrimitiveArrayDumpRecord() {
        long id = readId();
        int stackTraceSerialNumber = readInt();

        int arrayLength = readInt();
        byte type = readUnsignedByte();
        switch (type) {
            case BOOLEAN.hprofType:
                return new PrimitiveArrayDumpRecord(id, stackTraceSerialNumber, readBooleanArray(arrayLength));
            case CHAR.hprofType:
                return new PrimitiveArrayDumpRecord(id, stackTraceSerialNumber, readCharArray(arrayLength));
            case FLOAT.hprofType:
                return new PrimitiveArrayDumpRecord(id, stackTraceSerialNumber, readFloatArray(arrayLength));
            case DOUBLE.hprofType:
                return new PrimitiveArrayDumpRecord(id, stackTraceSerialNumber, readDoubleArray(arrayLength));
            case BYTE.hprofType:
                return new PrimitiveArrayDumpRecord(id, stackTraceSerialNumber, readByteArray(arrayLength));
            case SHORT.hprofType:
                return new PrimitiveArrayDumpRecord(id, stackTraceSerialNumber, readShortArray(arrayLength));
            case INT.hprofType:
                return new PrimitiveArrayDumpRecord(id, stackTraceSerialNumber, readIntArray(arrayLength));
            case LONG.hprofType:
                return new PrimitiveArrayDumpRecord(id, stackTraceSerialNumber, readLongArray(arrayLength));
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

    

    public void skipInstanceDumpRecord() {
        skip(identifierByteSize + INT_SIZE + identifierByteSize);
        int remainingBytesInInstance = readInt();
        skip(remainingBytesInInstance);
    }

    public void skipClassDumpRecord() {
        skip(identifierByteSize + INT_SIZE + identifierByteSize + identifierByteSize + identifierByteSize + identifierByteSize + identifierByteSize + identifierByteSize + INT_SIZE);

        short constantPoolCount = readUnsignedShort();
        for (int i = 0; i < constantPoolCount; i++) {
            skip(SHORT_SIZE);
            skip(typeSizes[readUnsignedByte()]);
        }

        short staticFieldCount = readUnsignedShort();
        for (int i = 0; i < staticFieldCount; i++) {
            skip(identifierByteSize);
            byte type = readUnsignedByte();
            skip(typeSizes[type]);
        }

        short fieldCount = readUnsignedShort();
        skip(fieldCount * (identifierByteSize + BYTE_SIZE));
    }

    public void skipObjectArrayDumpRecord() {
        skip(identifierByteSize + INT_SIZE);
        int arrayLength = readInt();
        skip(identifierByteSize + arrayLength * identifierByteSize);
    }

    public void skipPrimitiveArrayDumpRecord() {
        skip(identifierByteSize + INT_SIZE);
        int arrayLength = readInt();
        byte type = readUnsignedByte();
        skip(arrayLength * typeSizes[type]);
    }

    public void skipHeapDumpInfoRecord() {
        skip(identifierByteSize + identifierByteSize);
    }
    public long skip(int byteCount) {
        bytesRead += byteCount;
        return source.skip( (long) byteCount);
    }
    public void skip(long byteCount) {
        bytesRead += byteCount;
        source.skip(byteCount);
    }

    public long readUnsignedInt() {
        return readInt() & INT_MASK;
    }

    public int readUnsignedByte() {
        return readByte() & BYTE_MASK;
    }

    public ValueHolder readValue(int type) {
        switch (type) {
            case PrimitiveType.REFERENCE_HPROF_TYPE:
                return new ReferenceHolder(readId());
            case BOOLEAN.hprofType:
                return new BooleanHolder(readBoolean());
            case CHAR.hprofType:
                return new CharHolder(readChar());
            case FLOAT.hprofType:
                return new FloatHolder(readFloat());
            case DOUBLE.hprofType:
                return new DoubleHolder(readDouble());
            case BYTE.hprofType:
                return new ByteHolder(readByte());
            case SHORT.hprofType:
                return new ShortHolder(readShort());
            case INT.hprofType:
                return new IntHolder(readInt());
            case LONG.hprofType:
                return new LongHolder(readLong());
            default:
                throw new IllegalStateException("Unknown type " + type);
        }
    }

    public short readShort() {
        bytesRead += SHORT_SIZE;
        return source.readShort();
    }

    public int readInt() {
        bytesRead += INT_SIZE;
        return source.readInt();
    }

    public long[] readIdArray(int arrayLength) {
        long[] array = new long[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            array[i] = readId();
        }
        return array;
    }

    public boolean[] readBooleanArray(int arrayLength) {
        boolean[] array = new boolean[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            array[i] = readByte() != 0;
        }
        return array;
    }

    public char[] readCharArray(int arrayLength) {
        char[] array = new char[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            array[i] = readChar();
        }
        return array;
    }

    public String readString(int byteCount, Charset charset) {
        bytesRead += byteCount;
        return source.readString(byteCount, charset);
    }

    public float[] readFloatArray(int arrayLength) {
        float[] array = new float[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            array[i] = Float.intBitsToFloat(readInt());
        }
        return array;
    }

    public double[] readDoubleArray(int arrayLength) {
        double[] array = new double[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            array[i] = Double.longBitsToDouble(readLong());
        }
        return array;
    }

    public short[] readShortArray(int arrayLength) {
        short[] array = new short[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            array[i] = readShort();
        }
        return array;
    }

    public int[] readIntArray(int arrayLength) {
        int[] array = new int[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            array[i] = readInt();
        }
        return array;
    }

    public long[] readLongArray(int arrayLength) {
        long[] array = new long[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            array[i] = readLong();
        }
        return array;
    }

    public long readLong() {
        bytesRead += LONG_SIZE;
        return source.readLong();
    }

    public byte readByte() {
        bytesRead += BYTE_SIZE;
        return (byte) source.readByte();
    }

    public boolean readBoolean() {
        bytesRead += BOOLEAN_SIZE;
        return source.readByte() != 0;
    }

    public byte[] readByteArray(int byteCount) {
        bytesRead += byteCount;
        return source.readByteArray(byteCount);
    }

    public char readChar() {
        return (char) readUtf8(CHAR_SIZE).charAt(0);
    }

    public String readUtf8(long byteCount) {
        bytesRead += byteCount;
        return source.readUtf8(byteCount);
    }

    public short readUnsignedShort() {
        return (short) (readShort() & 0xFFFF);
    }
        public static float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    public static double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    public static long readId() throws IOException {

        switch (identifierByteSize) {
            case 1:
                return readByte();
            case 2:
                return readShort() & 0xFFFFL;
            case 4:
                return readInt() & 0xFFFFFFFFL;
            case 8:
                return readLong();
            default:
                throw new IllegalArgumentException("ID Length must be 1, 2, 4, or 8");
        }
    }
}
public class ClassSkipContentRecord {
  // fields and constructor here

  public ClassSkipContentRecord read() {
    int bytesReadStart = bytesRead;
    readId(); // this.id = readId() in Kotlin

    stackTraceSerialNumber = readInt();
    superclassId = readId();
    classLoaderId = readId();
    signersId = readId();
    protectionDomainId = readId();
    readId(); // readId(); readId(); in Kotlin

    instanceSize = readInt();

    int constantPoolCount = readUnsignedShort();
    for (int i = 0; i < constantPoolCount; i++) {
      skip(SHORT.byteSize);
      skip(sizeOf(readUnsignedByte()));
    }

    int staticFieldCount = readUnsignedShort();
    for (int i = 0; i < staticFieldCount; i++) {
      skip(identifierByteSize);
      int type = readUnsignedByte();
      skip(
          type == PrimitiveType.REFERENCE_HPROF_TYPE
              ? identifierByteSize
              : PrimitiveType.byteSizeByHprofType.get(type)
      );
    }

    int fieldCount = readUnsignedShort();
    skip((identifierByteSize + 1) * fieldCount);
    recordSize = bytesRead - bytesReadStart;
    return this;
  }
}

public class InstanceSkipContentRecord {
  // fields and constructor here

  public InstanceSkipContentRecord read() {
    int bytesReadStart = bytesRead;
    id = readId();
    stackTraceSerialNumber = readInt();
    classId = readId();
    int remainingBytesInInstance = readInt();
    skip(remainingBytesInInstance);
    recordSize = bytesRead - bytesReadStart;
    return this;
  }
}

public class ObjectArraySkipContentRecord {
  // fields and constructor here

  public ObjectArraySkipContentRecord read() {
    int bytesReadStart = bytesRead;
    id = readId();

    stackTraceSerialNumber = readInt();
    size = readInt();
    arrayClassId = readId();
    skip(identifierByteSize * size);
    recordSize = bytesRead - bytesReadStart;
    return this;
  }
}

public class PrimitiveArraySkipContentRecord {
  // fields and constructor here

  public PrimitiveArraySkipContentRecord read() {
    int bytesReadStart = bytesRead;
    id = readId();
    stackTraceSerialNumber = readInt();

    size = readInt();
    int type = PrimitiveType.primitiveTypeByHprofType.get(readUnsignedByte());
    skip(size * type.byteSize);
    recordSize = bytesRead - bytesReadStart;
    return this;
  }
}