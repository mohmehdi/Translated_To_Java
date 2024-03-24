

package shark;

import okio.BufferedSource;

import java.nio.charset.Charset;
import java.util.ArrayList;

public class HprofRecordReader {
    private long bytesRead = 0L;
    private final int identifierByteSize;
    private final int[] typeSizes;

    public HprofRecordReader(HprofHeader header, BufferedSource source) {
        this.identifierByteSize = header.identifierByteSize;
        this.source = source;

        int maxKey = PrimitiveType.byteSizeByHprofType.keySet().stream().max(Integer::compareTo).orElse(0);

        typeSizes = new int[maxKey + 1];
        PrimitiveType.byteSizeByHprofType.forEach((key, value) -> typeSizes[key] = value);
        typeSizes[PrimitiveType.REFERENCE_HPROF_TYPE] = identifierByteSize;
    }

    public int sizeOf(int type) {
        return typeSizes[type];
    }

    public StringRecord readStringRecord(long length) {
        return new StringRecord(readId(), readUtf8(length - identifierByteSize));
    }

    public void read(LoadClassRecord loadClassRecord) {
        loadClassRecord.classSerialNumber = readInt();
        loadClassRecord.id = readId();
        loadClassRecord.stackTraceSerialNumber = readInt();
        loadClassRecord.classNameStringId = readId();
    }

    public StackFrameRecord readStackFrameRecord() {
        return new StackFrameRecord(
                readId(),
                readId(),
                readId(),
                readId(),
                readInt(),
                readInt()
        );
    }

    public StackTraceRecord readStackTraceRecord() {
        return new StackTraceRecord(
                readInt(),
                readInt(),
                readIdArray(readInt())
        );
    }

    public GcRootRecord readUnknownGcRootRecord() {
        return new GcRootRecord(new Unknown(readId()));
    }

    public GcRootRecord readJniGlobalGcRootRecord() {
        return new GcRootRecord(new JniGlobal(readId(), readId()));
    }

    public GcRootRecord readJniLocalGcRootRecord() {
        return new GcRootRecord(new JniLocal(readId(), readInt(), readInt()));
    }

    public GcRootRecord readJavaFrameGcRootRecord() {
        return new GcRootRecord(new JavaFrame(readId(), readInt(), readInt()));
    }

    public GcRootRecord readNativeStackGcRootRecord() {
        return new GcRootRecord(new NativeStack(readId(), readInt()));
    }

    public GcRootRecord readStickyClassGcRootRecord() {
        return new GcRootRecord(new StickyClass(readId()));
    }

    public GcRootRecord readThreadBlockGcRootRecord() {
        return new GcRootRecord(new ThreadBlock(readId(), readInt()));
    }

    public GcRootRecord readMonitorUsedGcRootRecord() {
        return new GcRootRecord(new MonitorUsed(readId()));
    }

    public GcRootRecord readThreadObjectGcRootRecord() {
        return new GcRootRecord(new ThreadObject(readId(), readInt(), readInt()));
    }

    public GcRootRecord readInternedStringGcRootRecord() {
        return new GcRootRecord(new InternedString(readId()));
    }

    public GcRootRecord readFinalizingGcRootRecord() {
        return new GcRootRecord(new Finalizing(readId()));
    }

    public GcRootRecord readDebuggerGcRootRecord() {
        return new GcRootRecord(new Debugger(readId()));
    }

    public GcRootRecord readReferenceCleanupGcRootRecord() {
        return new GcRootRecord(new ReferenceCleanup(readId()));
    }

    public GcRootRecord readVmInternalGcRootRecord() {
        return new GcRootRecord(new VmInternal(readId()));
    }

    public GcRootRecord readJniMonitorGcRootRecord() {
        return new GcRootRecord(new JniMonitor(readId(), readInt(), readInt()));
    }

    public GcRootRecord readUnreachableGcRootRecord() {
        return new GcRootRecord(new Unreachable(readId()));
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
        return new HeapDumpInfoRecord(heapId, readId());
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
        int constantPoolCount = readUnsignedShort();
        for (int i = 0; i < constantPoolCount; i++) {
            skip(SHORT_SIZE);
            skip(typeSizes[readUnsignedByte()]);
        }
        int staticFieldCount = readUnsignedShort();
        ArrayList<StaticFieldRecord> staticFields = new ArrayList<>(staticFieldCount);
        for (int i = 0; i < staticFieldCount; i++) {
            long nameStringId = readId();
            int type = readUnsignedByte();
            ValueHolder value = readValue(type);
            staticFields.add(new StaticFieldRecord(nameStringId, type, value));
        }
        int fieldCount = readUnsignedShort();
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

    public ClassSkipContentRecord read(ClassSkipContentRecord classSkipContentRecord) {
        long bytesReadStart = bytesRead;
        classSkipContentRecord.id = readId();
        classSkipContentRecord.stackTraceSerialNumber = readInt();
        classSkipContentRecord.superclassId = readId();
        classSkipContentRecord.classLoaderId = readId();
        classSkipContentRecord.signersId = readId();
        classSkipContentRecord.protectionDomainId = readId();
        readId();
        readId();
        classSkipContentRecord.instanceSize = readInt();
        int constantPoolCount = readUnsignedShort();
        for (int i = 0; i < constantPoolCount; i++) {
            skip(SHORT_SIZE);
            skip(typeSizes[readUnsignedByte()]);
        }
        classSkipContentRecord.staticFieldCount = readUnsignedShort();
        for (int i = 0; i < classSkipContentRecord.staticFieldCount; i++) {
            skip(identifierByteSize);
            int type = readUnsignedByte();
            skip(typeSizes[type]);
        }
        classSkipContentRecord.fieldCount = readUnsignedShort();
        skip((identifierByteSize + 1) * classSkipContentRecord.fieldCount);
        classSkipContentRecord.recordSize = bytesRead - bytesReadStart;
        return classSkipContentRecord;
    }

    public InstanceSkipContentRecord read(InstanceSkipContentRecord instanceSkipContentRecord) {
        long bytesReadStart = bytesRead;
        instanceSkipContentRecord.id = readId();
        instanceSkipContentRecord.stackTraceSerialNumber = readInt();
        instanceSkipContentRecord.classId = readId();
        int remainingBytesInInstance = readInt();
        skip(remainingBytesInInstance);
        instanceSkipContentRecord.recordSize = bytesRead - bytesReadStart;
        return instanceSkipContentRecord;
    }

    public ObjectArraySkipContentRecord read(ObjectArraySkipContentRecord objectArraySkipContentRecord) {
        long bytesReadStart = bytesRead;
        objectArraySkipContentRecord.id = readId();
        objectArraySkipContentRecord.stackTraceSerialNumber = readInt();
        objectArraySkipContentRecord.size = readInt();
        objectArraySkipContentRecord.arrayClassId = readId();
        skip(identifierByteSize * objectArraySkipContentRecord.size);
        objectArraySkipContentRecord.recordSize = bytesRead - bytesReadStart;
        return objectArraySkipContentRecord;
    }

    public PrimitiveArraySkipContentRecord read(PrimitiveArraySkipContentRecord primitiveArraySkipContentRecord) {
        long bytesReadStart = bytesRead;
        primitiveArraySkipContentRecord.id = readId();
        primitiveArraySkipContentRecord.stackTraceSerialNumber = readInt();
        primitiveArraySkipContentRecord.size = readInt();
        primitiveArraySkipContentRecord.type = PrimitiveType.primitiveTypeByHprofType.get(readUnsignedByte());
        skip(primitiveArraySkipContentRecord.size * primitiveArraySkipContentRecord.type.byteSize);
        primitiveArraySkipContentRecord.recordSize = bytesRead - bytesReadStart;
        return primitiveArraySkipContentRecord;
    }

    public void skipInstanceDumpRecord() {
        skip(identifierByteSize + INT_SIZE + identifierByteSize);
        int remainingBytesInInstance = readInt();
        skip(remainingBytesInInstance);
    }

    public void skipClassDumpRecord() {
        skip(identifierByteSize + INT_SIZE + identifierByteSize + identifierByteSize + identifierByteSize + identifierByteSize + identifierByteSize + identifierByteSize + INT_SIZE);
        int constantPoolCount = readUnsignedShort();
        for (int i = 0; i < constantPoolCount; i++) {
            skip(SHORT_SIZE);
            skip(typeSizes[readUnsignedByte()]);
        }
        int staticFieldCount = readUnsignedShort();
        for (int i = 0; i < staticFieldCount; i++) {
            skip(identifierByteSize);
            int type = readUnsignedByte();
            skip(typeSizes[type]);
        }
        int fieldCount = readUnsignedShort();
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
        int type = readUnsignedByte();
        skip(arrayLength * typeSizes[type]);
    }

    public void skipHeapDumpInfoRecord() {
        skip(identifierByteSize + identifierByteSize);
    }

    public void skip(int byteCount) {
        bytesRead += byteCount;
        source.skip(byteCount);
    }

    public void skip(long byteCount) {
        bytesRead += byteCount;
        source.skip(byteCount);
    }

    public long readUnsignedInt() {
        return readInt() & INT_MASK;
    }

    public int readUnsignedByte() {
        return source.readByte() & BYTE_MASK;
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
            array[i] = readFloat();
        }
        return array;
    }

    public double[] readDoubleArray(int arrayLength) {
        double[] array = new double[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            array[i] = readDouble();
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
        return source.readByte();
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
        return readString(CHAR_SIZE, Charset.forName("UTF-16BE")).charAt(0);
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public long readId() {
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

    public String readUtf8(long byteCount) {
        bytesRead += byteCount;
        return source.readUtf8(byteCount);
    }

    public int readUnsignedShort() {
        return readShort() & 0xFFFF;
    }

    private static final int BOOLEAN_SIZE = PrimitiveType.BOOLEAN.byteSize;
    private static final int CHAR_SIZE = PrimitiveType.CHAR.byteSize;
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

    private static final long INT_MASK = 0xffffffffL;
    private static final int BYTE_MASK = 0xff;
}