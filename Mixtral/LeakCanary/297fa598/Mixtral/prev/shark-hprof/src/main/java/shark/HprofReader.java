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
import shark.PrimitiveType;
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
    private int identifierByteSize;
    private long position;
    private int[] typeSizes;

    private final InstanceSkipContentRecord reusedInstanceSkipContentRecord = new InstanceSkipContentRecord(0, 0, 0);
    private final ClassSkipContentRecord reusedClassSkipContentRecord = new ClassSkipContentRecord(0, 0, 0, 0, 0, 0, 0, 0, 0);
    private final ObjectArraySkipContentRecord reusedObjectArraySkipContentRecord = new ObjectArraySkipContentRecord(0, 0, 0, 0);
    private final PrimitiveArraySkipContentRecord reusedPrimitiveArraySkipContentRecord = new PrimitiveArraySkipContentRecord(0, 0, 0, BOOLEAN.INSTANCE);
    private final LoadClassRecord reusedLoadClassRecord = new LoadClassRecord(0, 0, 0, 0);

    public HprofReader(BufferedSource source, int identifierByteSize, long startPosition) {
        this.source = source;
        this.identifierByteSize = identifierByteSize;
        this.position = startPosition;

        this.typeSizes = new int[PrimitiveType.values().length + 1];
        for (PrimitiveType type : PrimitiveType.values()) {
            typeSizes[type.hprofType] = type.byteSize;
        }
        typeSizes[PrimitiveType.REFERENCE_HPROF_TYPE] = identifierByteSize;
    }


    public void readHprofRecords(Set<Class<? extends HprofRecord>> recordTypes,
                                  OnHprofRecordListener listener) {
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
        boolean readClassSkipContentRecord = recordTypes.contains(ClassSkipContentRecord.class);
        boolean readInstanceDumpRecord = readAllObjectRecords || recordTypes.contains(InstanceDumpRecord.class);
        boolean readInstanceSkipContentRecord = recordTypes.contains(InstanceSkipContentRecord.class);
        boolean readObjectArrayDumpRecord =
                readAllObjectRecords || recordTypes.contains(ObjectArrayDumpRecord.class);
        boolean readObjectArraySkipContentRecord = recordTypes.contains(ObjectArraySkipContentRecord.class);
        boolean readPrimitiveArrayDumpRecord =
                readAllObjectRecords || recordTypes.contains(PrimitiveArrayDumpRecord.class);
        boolean readPrimitiveArraySkipContentRecord = recordTypes.contains(PrimitiveArraySkipContentRecord.class);

        int intByteSize = Integer.BYTES;

        while (!exhausted()) {

            byte tag = readUnsignedByte();

            skip(intByteSize);

            int length = readUnsignedInt();

            switch (tag) {
                case STRING_IN_UTF8:
                    if (readStringRecord) {
                        int recordPosition = position();
                        int id = readId();
                        int stringLength = length - identifierByteSize();
                        String string = readUtf8(stringLength);
                        StringRecord record = new StringRecord(id, string);
                        listener.onHprofRecord(recordPosition, record);
                    } else {
                        skip(length);
                    }
                    break;
                case LOAD_CLASS:
                    if (readLoadClassRecord) {
                        int recordPosition = position();
                        int classSerialNumber = readInt();
                        int id = readId();
                        int stackTraceSerialNumber = readInt();
                        int classNameStringId = readId();
                        reusedLoadClassRecord.classSerialNumber = classSerialNumber;
                        reusedLoadClassRecord.id = id;
                        reusedLoadClassRecord.stackTraceSerialNumber = stackTraceSerialNumber;
                        reusedLoadClassRecord.classNameStringId = classNameStringId;
                        listener.onHprofRecord(recordPosition, reusedLoadClassRecord);
                    } else {
                        skip(length);
                    }
                    break;
                case STACK_FRAME:
                    if (readStackFrameRecord) {
                        int recordPosition = position();
                        StackFrameRecord record = new StackFrameRecord(
                                id,
                                methodNameStringId,
                                methodSignatureStringId,
                                sourceFileNameStringId,
                                classSerialNumber,
                                lineNumber
                        );
                        listener.onHprofRecord(recordPosition, record);
                    } else {
                        skip(length);
                    }
                    break;
                case STACK_TRACE:
                    if (readStackTraceRecord) {
                        int recordPosition = position();
                        int stackTraceSerialNumber = readInt();
                        int threadSerialNumber = readInt();
                        int frameCount = readInt();
                        int[] stackFrameIds = readIdArray(frameCount);
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
                    int heapDumpStart = position();
                    byte previousTag = 0;
                    long previousTagPosition = 0L;
                    while (position() - heapDumpStart < length) {
                        long heapDumpTagPosition = position();
                        byte heapDumpTag = readUnsignedByte();

                        switch (heapDumpTag) {
                            case ROOT_UNKNOWN:
                                if (readGcRootRecord) {
                                    int recordPosition = position();
                                    GcRootRecord record = new GcRootRecord(
                                            new Unknown(readId())
                                    );
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skip(identifierByteSize());
                                }
                                break;
                            // ... other cases omitted for brevity ...
                        }
                        previousTag = heapDumpTag;
                        previousTagPosition = heapDumpTagPosition;
                    }
                    break;
                case HEAP_DUMP_END:
                    if (readHeapDumpEndRecord) {
                        int recordPosition = position();
                        HeapDumpEndRecord record = new HeapDumpEndRecord();
                        listener.onHprofRecord(recordPosition, record);
                    }
                    break;
                default:
                    skip(length);
            }
        }
    }

    public InstanceDumpRecord readInstanceDumpRecord() {
        int id = readId();
        int stackTraceSerialNumber = readInt();
        int classId = readId();
        int remainingBytesInInstance = readInt();
        byte[] fieldValues = readByteArray(remainingBytesInInstance);

        return new InstanceDumpRecord(
                id,
                stackTraceSerialNumber,
                classId,
                fieldValues
        );
    }

    public InstanceSkipContentRecord readInstanceSkipContentRecord() {
        int id = readId();
        int stackTraceSerialNumber = readInt();
        int classId = readId();
        int remainingBytesInInstance = readInt();
        skip(remainingBytesInInstance);

        InstanceSkipContentRecord reusedInstanceSkipContentRecord = // Get the reused object
        reusedInstanceSkipContentRecord.setId(id);
        reusedInstanceSkipContentRecord.setStackTraceSerialNumber(stackTraceSerialNumber);
        reusedInstanceSkipContentRecord.setClassId(classId);

        return reusedInstanceSkipContentRecord;
    }

    public ClassDumpRecord readClassDumpRecord() {
        int id = readId();

        int stackTraceSerialNumber = readInt();
        int superclassId = readId();

        int classLoaderId = readId();

        int signersId = readId();

        int protectionDomainId = readId();

        readId();
        readId();

        int instanceSize = readInt();

        int constantPoolCount = readUnsignedShort();
        for (int i = 0; i < constantPoolCount; i++) {
            skip(SHORT_SIZE);
            skip(typeSize(readUnsignedByte()));
        }

        int staticFieldCount = readUnsignedShort();
        ArrayList<StaticFieldRecord> staticFields = new ArrayList<>(staticFieldCount);
        for (int i = 0; i < staticFieldCount; i++) {
            int nameStringId = readId();
            byte type = readUnsignedByte();
            Object value = readValue(type);

            staticFields.add(
                    new StaticFieldRecord(
                            nameStringId,
                            type,
                            value
                    )
            );
        }

        int fieldCount = readUnsignedShort();
        ArrayList<FieldRecord> fields = new ArrayList<>(fieldCount);
        for (int i = 0; i < fieldCount; i++) {
            fields.add(new FieldRecord(readId(), readUnsignedByte()));
        }

        return new ClassDumpRecord(
                id,
                stackTraceSerialNumber,
                superclassId,
                classLoaderId,
                signersId,
                protectionDomainId,
                instanceSize,
                staticFields,
                fields
        );
    }

    public ClassSkipContentRecord readClassSkipContentRecord() {
        int id = readId();

        int stackTraceSerialNumber = readInt();
        int superclassId = readId();

        int classLoaderId = readId();

        int signersId = readId();

        int protectionDomainId = readId();

        readId();
        readId();

        int instanceSize = readInt();

        int constantPoolCount = readUnsignedShort();
        for (int i = 0; i < constantPoolCount; i++) {
            skip(SHORT_SIZE);
            skip(typeSize(readUnsignedByte()));
        }

        int staticFieldCount = readUnsignedShort();
        for (int i = 0; i < staticFieldCount; i++) {
            skip(IDENTIFIER_BYTE_SIZE);
            byte type = readUnsignedByte();
            skip(
                    type == PrimitiveType.REFERENCE_HPROF_TYPE
                            ? IDENTIFIER_BYTE_SIZE
                            : PrimitiveType.byteSizeByHprofType.get(type)
            );
        }

        int fieldCount = readUnsignedShort();

        skip((IDENTIFIER_BYTE_SIZE + 1) * fieldCount);

        ClassSkipContentRecord reusedClassSkipContentRecord = // Get the reused object
        reusedClassSkipContentRecord.setId(id);
        reusedClassSkipContentRecord.setStackTraceSerialNumber(stackTraceSerialNumber);
        reusedClassSkipContentRecord.setSuperclassId(superclassId);
        reusedClassSkipContentRecord.setClassLoaderId(classLoaderId);
        reusedClassSkipContentRecord.setSignersId(signersId);
        reusedClassSkipContentRecord.setProtectionDomainId(protectionDomainId);
        reusedClassSkipContentRecord.setInstanceSize(instanceSize);
        reusedClassSkipContentRecord.setStaticFieldCount(staticFieldCount);
        reusedClassSkipContentRecord.setFieldCount(fieldCount);

        return reusedClassSkipContentRecord;
    }
    PrimitiveArrayDumpRecord readPrimitiveArrayDumpRecord() {
    int id = readId();
    int stackTraceSerialNumber = readInt();

    int arrayLength = readInt();
    PrimitiveType type = PrimitiveType.primitiveTypeByHprofType.get(readUnsignedByte());

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

PrimitiveArraySkipContentRecord readPrimitiveArraySkipContentRecord() {
    int id = readId();
    int stackTraceSerialNumber = readInt();

    int arrayLength = readInt();
    PrimitiveType type = PrimitiveType.primitiveTypeByHprofType.get(readUnsignedByte());
    skip(arrayLength * type.byteSize);

    ReusedPrimitiveArraySkipContentRecord reusedRecord = reusedPrimitiveArraySkipContentRecord;
    reusedRecord.id = id;
    reusedRecord.stackTraceSerialNumber = stackTraceSerialNumber;
    reusedRecord.size = arrayLength;
    reusedRecord.type = type;

    return reusedRecord;
}

ObjectArrayDumpRecord readObjectArrayDumpRecord() {
    int id = readId();

    int stackTraceSerialNumber = readInt();
    int arrayLength = readInt();
    int arrayClassId = readId();
    int[] elementIds = readIdArray(arrayLength);

    return new ObjectArrayDumpRecord(id, stackTraceSerialNumber, arrayClassId, elementIds);
}

ObjectArraySkipContentRecord readObjectArraySkipContentRecord() {
    int id = readId();

    int stackTraceSerialNumber = readInt();
    int arrayLength = readInt();
    int arrayClassId = readId();

    skip(identifierByteSize * arrayLength);

    ReusedObjectArraySkipContentRecord reusedRecord = reusedObjectArraySkipContentRecord;
    reusedRecord.id = id;
    reusedRecord.stackTraceSerialNumber = stackTraceSerialNumber;
    reusedRecord.arrayClassId = arrayClassId;
    reusedRecord.size = arrayLength;

    return reusedRecord;
}

ValueHolder readValue(int type) {
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
    position += SHORT_SIZE;
    return source.readShort();
}

private int readInt() {
    position += INT_SIZE;
    return source.readInt();
}

private long[] readIdArray(int arrayLength) {
    LongArray result = new LongArray(arrayLength);
    for (int i = 0; i < arrayLength; i++) {
        result[i] = readId();
    }
    return result.toArray();
}

private boolean[] readBooleanArray(int arrayLength) {
    BooleanArray result = new BooleanArray(arrayLength);
    for (int i = 0; i < arrayLength; i++) {
        result[i] = readByte() != 0;
    }
    return result.toArray();
}

private char[] readCharArray(int arrayLength) {
    String str = readString(CHAR_SIZE * arrayLength, Charsets.UTF_16BE);
    return str.toCharArray();
}

private String readString(int byteCount, Charset charset) {
    position += byteCount;
    return source.readString(byteCount, charset);
}

private float[] readFloatArray(int arrayLength) {
    FloatArray result = new FloatArray(arrayLength);
    for (int i = 0; i < arrayLength; i++) {
        result[i] = readFloat();
    }
    return result.toArray();
}

private double[] readDoubleArray(int arrayLength) {
    DoubleArray result = new DoubleArray(arrayLength);
    for (int i = 0; i < arrayLength; i++) {
        result[i] = readDouble();
    }
    return result.toArray();
}

private short[] readShortArray(int arrayLength) {
    ShortArray result = new ShortArray(arrayLength);
    for (int i = 0; i < arrayLength; i++) {
        result[i] = readShort();
    }
    return result.toArray();
}

private int[] readIntArray(int arrayLength) {
    IntArray result = new IntArray(arrayLength);
    for (int i = 0; i < arrayLength; i++) {
        result[i] = readInt();
    }
    return result.toArray();
}

private long[] readLongArray(int arrayLength) {
    LongArray result = new LongArray(arrayLength);
    for (int i = 0; i < arrayLength; i++) {
        result[i] = readLong();
    }
    return result.toArray();
}

private long readLong() {
    position += LONG_SIZE;
    return source.readLong();
}

private boolean exhausted() {
    return source.exhausted();
}

private void skip(long byteCount) {
    position += byteCount;
    source.skip(byteCount);
}

private byte readByte() {
    position += BYTE_SIZE;
    return source.readByte();
}

private boolean readBoolean() {
    position += BOOLEAN_SIZE;
    return source.readByte() != 0;
}

private byte[] readByteArray(int byteCount) {
    position += byteCount;
    return source.readByteArray(byteCount);
}

private char readChar() {
    return readString(CHAR_SIZE, Charsets.UTF_16BE).charAt(0);
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
            return readByte() & 0xFFL;
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

private String readUtf8(long byteCount) {
    position += byteCount;
    return source.readUtf8(byteCount);
}

private long readUnsignedInt() {
    return (long) readInt() & INT_MASK;
}

private int readUnsignedByte() {
    return readByte() & BYTE_MASK;
}

private int readUnsignedShort() {
    return readShort() & 0xFFFF;
}

private void skip(int byteCount) {
    position += byteCount;
    return source.skip(byteCount << 3); // Convert to bytes as Java's skip method uses long for byte count
}

private void skipInstanceDumpRecord() {
    skip(identifierByteSize + INT_SIZE + identifierByteSize);
    int remainingBytesInInstance = readInt();
    skip(remainingBytesInInstance);
}

private void skipClassDumpRecord() {
    skip(
            identifierByteSize + INT_SIZE + identifierByteSize + identifierByteSize + identifierByteSize +
                    identifierByteSize + identifierByteSize + identifierByteSize + INT_SIZE
    );

    int constantPoolCount = readUnsignedShort();
    for (int i = 0; i < constantPoolCount; i++) {
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
    skip(arrayLength * typeSize(type));
}

private HeapDumpInfoRecord readHeapDumpInfoRecord() {
    int heapId = readInt();
    int heapNameStringId = readId();
    return new HeapDumpInfoRecord(heapId, heapNameStringId);
}

private void skipHeapDumpInfoRecord() {
    skip(identifierByteSize + identifierByteSize);
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
}