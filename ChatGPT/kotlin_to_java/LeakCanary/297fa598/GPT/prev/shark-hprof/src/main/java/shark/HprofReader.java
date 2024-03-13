

package shark;

import okio.BufferedSource;
import kotlin.reflect.KClass;

import java.nio.charset.Charset;
import java.util.Set;

import static shark.PrimitiveType.INT;
import static shark.PrimitiveType.ReferenceHolder;
import static shark.PrimitiveType.REFERENCE_HPROF_TYPE;
import static shark.PrimitiveType.byteSizeByHprofType;

public class HprofReader {

    private BufferedSource source;
    private final int identifierByteSize;
    private final long startPosition;

    private long position = startPosition;

    private final int BOOLEAN_SIZE = BOOLEAN.byteSize;
    private final int CHAR_SIZE = CHAR.byteSize;
    private final int FLOAT_SIZE = FLOAT.byteSize;
    private final int DOUBLE_SIZE = DOUBLE.byteSize;
    private final int BYTE_SIZE = BYTE.byteSize;
    private final int SHORT_SIZE = SHORT.byteSize;
    private final int INT_SIZE = INT.byteSize;
    private final int LONG_SIZE = LONG.byteSize;

    private final byte BOOLEAN_TYPE = BOOLEAN.hprofType;
    private final byte CHAR_TYPE = CHAR.hprofType;
    private final byte FLOAT_TYPE = FLOAT.hprofType;
    private final byte DOUBLE_TYPE = DOUBLE.hprofType;
    private final byte BYTE_TYPE = BYTE.hprofType;
    private final byte SHORT_TYPE = SHORT.hprofType;
    private final byte INT_TYPE = INT.hprofType;
    private final byte LONG_TYPE = LONG.hprofType;

    private final long INT_MASK = 0xffffffffL;
    private final long BYTE_MASK = 0xff;

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

    private final int[] typeSizes =
            PrimitiveType.byteSizeByHprofType + (PrimitiveType.REFERENCE_HPROF_TYPE to identifierByteSize);

    private final InstanceSkipContentRecord reusedInstanceSkipContentRecord = new InstanceSkipContentRecord(0, 0, 0);
    private final ClassSkipContentRecord reusedClassSkipContentRecord = new ClassSkipContentRecord(0, 0, 0, 0, 0, 0, 0, 0, 0);
    private final ObjectArraySkipContentRecord reusedObjectArraySkipContentRecord = new ObjectArraySkipContentRecord(0, 0, 0, 0);
    private final PrimitiveArraySkipContentRecord reusedPrimitiveArraySkipContentRecord =
            new PrimitiveArraySkipContentRecord(0, 0, 0, BOOLEAN);
    private final LoadClassRecord reusedLoadClassRecord = new LoadClassRecord(0, 0, 0, 0);

    public HprofReader(BufferedSource source, int identifierByteSize, long startPosition) {
        this.source = source;
        this.identifierByteSize = identifierByteSize;
        this.startPosition = startPosition;
    }

    public void readHprofRecords(Set<KClass<? extends HprofRecord>> recordTypes, OnHprofRecordListener listener) {
        boolean readAllRecords = HprofRecord.class.isAssignableFrom(HprofRecord.class);
        boolean readStringRecord = readAllRecords || StringRecord.class.isAssignableFrom(StringRecord.class);
        boolean readLoadClassRecord = readAllRecords || LoadClassRecord.class.isAssignableFrom(LoadClassRecord.class);
        boolean readHeapDumpEndRecord = readAllRecords || HeapDumpEndRecord.class.isAssignableFrom(HeapDumpEndRecord.class);
        boolean readStackFrameRecord = readAllRecords || StackFrameRecord.class.isAssignableFrom(StackFrameRecord.class);
        boolean readStackTraceRecord = readAllRecords || StackTraceRecord.class.isAssignableFrom(StackTraceRecord.class);

        boolean readAllHeapDumpRecords = readAllRecords || HeapDumpRecord.class.isAssignableFrom(HeapDumpRecord.class);

        boolean readGcRootRecord = readAllHeapDumpRecords || GcRootRecord.class.isAssignableFrom(GcRootRecord.class);
        boolean readHeapDumpInfoRecord = readAllRecords || HeapDumpInfoRecord.class.isAssignableFrom(HeapDumpInfoRecord.class);

        boolean readAllObjectRecords = readAllHeapDumpRecords || ObjectRecord.class.isAssignableFrom(ObjectRecord.class);

        boolean readClassDumpRecord = readAllObjectRecords || ClassDumpRecord.class.isAssignableFrom(ClassDumpRecord.class);
        boolean readClassSkipContentRecord = ClassSkipContentRecord.class.isAssignableFrom(ClassSkipContentRecord.class);
        boolean readInstanceDumpRecord = readAllObjectRecords || InstanceDumpRecord.class.isAssignableFrom(InstanceDumpRecord.class);
        boolean readInstanceSkipContentRecord = InstanceSkipContentRecord.class.isAssignableFrom(InstanceSkipContentRecord.class);
        boolean readObjectArrayDumpRecord = readAllObjectRecords || ObjectArrayDumpRecord.class.isAssignableFrom(ObjectArrayDumpRecord.class);
        boolean readObjectArraySkipContentRecord = ObjectArraySkipContentRecord.class.isAssignableFrom(ObjectArraySkipContentRecord.class);
        boolean readPrimitiveArrayDumpRecord = readAllObjectRecords || PrimitiveArrayDumpRecord.class.isAssignableFrom(PrimitiveArrayDumpRecord.class);
        boolean readPrimitiveArraySkipContentRecord = PrimitiveArraySkipContentRecord.class.isAssignableFrom(PrimitiveArraySkipContentRecord.class);

        int int

ByteSize = INT.byteSize;

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
                        long recordPosition = position;
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
                        long recordPosition = position;
                        int classSerialNumber = readInt();
                        long id = readId();
                        int stackTraceSerialNumber = readInt();
                        long classNameStringId = readId();
                        reusedLoadClassRecord.setClassSerialNumber(classSerialNumber);
                        reusedLoadClassRecord.setId(id);
                        reusedLoadClassRecord.setStackTraceSerialNumber(stackTraceSerialNumber);
                        reusedLoadClassRecord.setClassNameStringId(classNameStringId);
                        listener.onHprofRecord(recordPosition, reusedLoadClassRecord);
                    } else {
                        skip(length);
                    }
                    break;
                case STACK_FRAME:
                    if (readStackFrameRecord) {
                        long recordPosition = position;
                        StackFrameRecord record = new StackFrameRecord(
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
                        long recordPosition = position;
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
                    long heapDumpStart = position;
                    int previousTag = 0;
                    long previousTagPosition = 0L;
                    while (position - heapDumpStart < length) {
                        long heapDumpTagPosition = position;
                        int heapDumpTag = readUnsignedByte();

                        switch (heapDumpTag) {
                            case ROOT_UNKNOWN:
                                if (readGcRootRecord) {
                                    long recordPosition = position;
                                    GcRootRecord record = new GcRootRecord(new Unknown(readId()));
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skip(identifierByteSize);
                                }
                                break;
                            case ROOT_JNI_GLOBAL:
                                if (readGcRootRecord) {
                                    long recordPosition = position;
                                    GcRootRecord gcRootRecord = new GcRootRecord(new JniGlobal(readId(), readId()));
                                    listener.onHprofRecord(recordPosition, gcRootRecord);
                                } else {
                                    skip(identifierByteSize + identifierByteSize);
                                }
                                break;

                            case ROOT_JNI_LOCAL:
                                if (readGcRootRecord) {
                                    long recordPosition = position;
                                    GcRootRecord gcRootRecord = new GcRootRecord(
                                            new JniLocal(readId(), readInt(), readInt())
                                    );
                                    listener.onHprofRecord(recordPosition, gcRootRecord);
                                } else {
                                    skip(identifierByteSize + intByteSize + intByteSize);
                                }
                                break;

                            case ROOT_JAVA_FRAME:
                                if (readGcRootRecord) {
                                    long recordPosition = position;
                                    GcRootRecord gcRootRecord = new GcRootRecord(
                                            new JavaFrame(readId(), readInt(), readInt())
                                    );
                                    listener.onHprofRecord(recordPosition, gcRootRecord);
                                } else {
                                    skip(identifierByteSize + intByteSize + intByteSize);
                                }
                                break;

                            case ROOT_NATIVE_STACK:
                                if (readGcRootRecord) {
                                    long recordPosition = position;
                                    GcRootRecord gcRootRecord = new GcRootRecord(
                                            new NativeStack(readId(), readInt())
                                    );
                                    listener.onHprofRecord(recordPosition, gcRootRecord);
                                } else {
                                    skip(identifierByteSize + intByteSize);
                                }
                                break;

                            case ROOT_STICKY_CLASS:
                                if (readGcRootRecord) {
                                    long recordPosition = position;
                                    GcRootRecord gcRootRecord = new GcRootRecord(
                                            new StickyClass(readId())
                                    );
                                    listener.onHprofRecord(recordPosition, gcRootRecord);
                                } else {
                                    skip(identifierByteSize);
                                }
                                break;

                            case ROOT_THREAD_BLOCK:
                                if (readGcRootRecord) {
                                    long recordPosition = position;
                                    GcRootRecord gcRootRecord = new GcRootRecord(
                                            new ThreadBlock(readId(), readInt())
                                    );
                                    listener.onHprofRecord(recordPosition, gcRootRecord);
                                } else {
                                    skip(identifierByteSize + intByteSize);
                                }
                                break;

                            case ROOT_MONITOR_USED:
                                if (readGcRootRecord) {
                                    long recordPosition = position;
                                    GcRootRecord gcRootRecord = new GcRootRecord(
                                            new MonitorUsed(readId())
                                    );
                                    listener.onHprofRecord(recordPosition, gcRootRecord);
                                } else {
                                    skip(identifierByteSize);
                                }
                                break;

                            case ROOT_THREAD_OBJECT:
                                if (readGcRootRecord) {
                                    long recordPosition = position;
                                    GcRootRecord gcRootRecord = new GcRootRecord(
                                            new ThreadObject(readId(), readInt(), readInt())
                                    );
                                    listener.onHprofRecord(recordPosition, gcRootRecord);
                                } else {
                                    skip(identifierByteSize + intByteSize + intByteSize);
                                }
                                break;

                            case ROOT_INTERNED_STRING:
                                if (readGcRootRecord) {
                                    long recordPosition = position;
                                    GcRootRecord gcRootRecord = new GcRootRecord(new InternedString(readId()));
                                    listener.onHprofRecord(recordPosition, gcRootRecord);
                                } else {
                                    skip(identifierByteSize);
                                }
                                break;

                            case ROOT_FINALIZING:
                                if (readGcRootRecord) {
                                    long recordPosition = position;
                                    GcRootRecord gcRootRecord = new GcRootRecord(
                                            new Finalizing(readId())
                                    );
                                    listener.onHprofRecord(recordPosition, gcRootRecord);
                                } else {
                                    skip(identifierByteSize);
                                }
                                break;

                            case ROOT_DEBUGGER:
                                if (readGcRootRecord) {
                                    long recordPosition = position;
                                    GcRootRecord gcRootRecord = new GcRootRecord(
                                            new Debugger(readId())
                                    );
                                    listener.onHprofRecord(recordPosition, gcRootRecord);
                                } else {
                                    skip(identifierByteSize);
                                }
                                break;

                            case ROOT_REFERENCE_CLEANUP:
                                if (readGcRootRecord) {


                                    long recordPosition = position;
                                    GcRootRecord gcRootRecord = new GcRootRecord(
                                            new ReferenceCleanup(readId())
                                    );
                                    listener.onHprofRecord(recordPosition, gcRootRecord);
                                } else {
                                    skip(identifierByteSize);
                                }
                                break;

                            case ROOT_VM_INTERNAL:
                                if (readGcRootRecord) {
                                    long recordPosition = position;
                                    GcRootRecord gcRootRecord = new GcRootRecord(
                                            new VmInternal(readId())
                                    );
                                    listener.onHprofRecord(recordPosition, gcRootRecord);
                                } else {
                                    skip(identifierByteSize);
                                }
                                break;

                            case ROOT_JNI_MONITOR:
                                if (readGcRootRecord) {
                                    long recordPosition = position;
                                    GcRootRecord gcRootRecord = new GcRootRecord(
                                            new JniMonitor(readId(), readInt(), readInt())
                                    );
                                    listener.onHprofRecord(recordPosition, gcRootRecord);
                                } else {
                                    skip(identifierByteSize + intByteSize + intByteSize);
                                }
                                break;

                            case ROOT_UNREACHABLE:
                                if (readGcRootRecord) {
                                    long recordPosition = position;
                                    GcRootRecord gcRootRecord = new GcRootRecord(
                                            new Unreachable(readId())
                                    );
                                    listener.onHprofRecord(recordPosition, gcRootRecord);
                                } else {
                                    skip(identifierByteSize);
                                }
                                break;
                            case CLASS_DUMP:
                                if (readClassDumpRecord) {
                                    long recordPosition = position;
                                    HprofRecord record = readClassDumpRecord();
                                    listener.onHprofRecord(recordPosition, record);
                                } else if (readClassSkipContentRecord) {
                                    long recordPosition = position;
                                    HprofRecord record = readClassSkipContentRecord();
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skipClassDumpRecord();
                                }
                                break;
                            case INSTANCE_DUMP:
                                if (readInstanceDumpRecord) {
                                    long recordPosition = position;
                                    HprofRecord record = readInstanceDumpRecord();
                                    listener.onHprofRecord(recordPosition, record);
                                } else if (readInstanceSkipContentRecord) {
                                    long recordPosition = position;
                                    HprofRecord record = readInstanceSkipContentRecord();
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skipInstanceDumpRecord();
                                }
                                break;
                            case OBJECT_ARRAY_DUMP:
                                if (readObjectArrayDumpRecord) {
                                    long recordPosition = position;
                                    HprofRecord arrayRecord = readObjectArrayDumpRecord();
                                    listener.onHprofRecord(recordPosition, arrayRecord);
                                } else if (readObjectArraySkipContentRecord) {
                                    long recordPosition = position;
                                    HprofRecord arrayRecord = readObjectArraySkipContentRecord();
                                    listener.onHprofRecord(recordPosition, arrayRecord);
                                } else {
                                    skipObjectArrayDumpRecord();
                                }
                                break;
                            case PRIMITIVE_ARRAY_DUMP:
                                if (readPrimitiveArrayDumpRecord) {
                                    long recordPosition = position;
                                    HprofRecord record = readPrimitiveArrayDumpRecord();
                                    listener.onHprofRecord(recordPosition, record);
                                } else if (readPrimitiveArraySkipContentRecord) {
                                    long recordPosition = position;
                                    HprofRecord record = readPrimitiveArraySkipContentRecord();
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skipPrimitiveArrayDumpRecord();
                                }
                                break;
                            case PRIMITIVE_ARRAY_NODATA:
                                throw new UnsupportedOperationException("PRIMITIVE_ARRAY_NODATA cannot be parsed");
                            case HEAP_DUMP_INFO:
                                if (readHeapDumpInfoRecord) {
                                    long recordPosition = position;
                                    HprofRecord record = readHeapDumpInfoRecord();
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skipHeapDumpInfoRecord();
                                }
                                break;
                            default:
                                throw new IllegalStateException(
                                        "Unknown tag " + String.format("0x%02x", heapDumpTag) +
                                                " at " + heapDumpTagPosition +
                                                " after " + String.format("0x%02x", previousTag) +
                                                " at " + previousTagPosition
                                );
                        }
                        previousTag = heapDumpTag;
                        previousTagPosition = heapDumpTagPosition;
                    }
                    break;
                case HEAP_DUMP_END:
                    if (readHeapDumpEndRecord) {
                        long recordPosition = position;
                        HeapDumpEndRecord record = HeapDumpEndRecord.INSTANCE;
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

        public InstanceSkipContentRecord readInstanceSkipContentRecord() {
        long id = readId();
        int stackTraceSerialNumber = readInt();
        long classId = readId();
        int remainingBytesInInstance = readInt();
        skip(remainingBytesInInstance);
        return reusedInstanceSkipContentRecord.apply(
                (record) -> {
                    record.setId(id);
                    record.setStackTraceSerialNumber(stackTraceSerialNumber);
                    record.setClassId(classId);
                }
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
        int constantPoolCount = readUnsignedShort();
        for (int i = 0; i < constantPoolCount; i++) {
            skip(SHORT_SIZE);
            skip(typeSize(readUnsignedByte()));
        }
        int staticFieldCount = readUnsignedShort();
        ArrayList<StaticFieldRecord> staticFields = new ArrayList<>();
        for (int i = 0; i < staticFieldCount; i++) {
            long nameStringId = readId();
            int type = readUnsignedByte();
            ValueHolder value = readValue(type);
            staticFields.add(new StaticFieldRecord(nameStringId, type, value));
        }
        int fieldCount = readUnsignedShort();
        ArrayList<FieldRecord> fields = new ArrayList<>();
        for (int i = 0; i < fieldCount; i++) {
            fields.add(new FieldRecord(readId(), readUnsignedByte()));
        }
        return new ClassDumpRecord(id, stackTraceSerialNumber, superclassId, classLoaderId,
                signersId, protectionDomainId, instanceSize, staticFields, fields);
    }

    public ClassSkipContentRecord readClassSkipContentRecord() {
        long id = readId();
        int stackTraceSerialNumber = readInt();
        long superclassId = readId();
        long classLoaderId = readId();
        long signersId = readId();
        long protectionDomainId = readId();
        readId(); // reserved
        readId(); // reserved
        int instanceSize = readInt();
        int constantPoolCount = readUnsignedShort();
        for (int i = 0; i < constantPoolCount; i++) {
            skip(SHORT_SIZE);
            skip(typeSize(readUnsignedByte()));
        }
        int staticFieldCount = readUnsignedShort();
        for (int i = 0; i < staticFieldCount; i++) {
            skip(identifierByteSize);
            int type = readUnsignedByte();
            skip(type == PrimitiveType.REFERENCE_HPROF_TYPE ? identifierByteSize :
                    PrimitiveType.byteSizeByHprofType.get(type));
        }
        int fieldCount = readUnsignedShort();
        skip((identifierByteSize + 1) * fieldCount);
        return reusedClassSkipContentRecord.apply(
                (record) -> {
                    record.setId(id);
                    record.setStackTraceSerialNumber(stackTraceSerialNumber);
                    record.setSuperclassId(superclassId);
                    record.setClassLoaderId(classLoaderId);
                    record.setSignersId(signersId);
                    record.setProtectionDomainId(protectionDomainId);
                    record.setInstanceSize(instanceSize);
                    record.setStaticFieldCount(staticFieldCount);
                    record.setFieldCount(fieldCount);
                }
        );
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

    public PrimitiveArraySkipContentRecord readPrimitiveArraySkipContentRecord() {
        long id = readId();
        int stackTraceSerialNumber = readInt();
        int arrayLength = readInt();
        int type = PrimitiveType.primitiveTypeByHprofType.get(readUnsignedByte());
        skip(arrayLength * type.byteSize);
        return reusedPrimitiveArraySkipContentRecord.apply(
                (record) -> {
                    record.setId(id);
                    record.setStackTraceSerialNumber(stackTraceSerialNumber);
                    record.setSize(arrayLength);
                    record.setType(type);
                }
        );
    }

    public ObjectArrayDumpRecord readObjectArrayDumpRecord() {
        long id = readId();
        int stackTraceSerialNumber = readInt();
        int arrayLength = readInt();
        long arrayClassId = readId();
        long[] elementIds = readIdArray(arrayLength);
        return new ObjectArrayDumpRecord(id, stackTraceSerialNumber, arrayClassId, elementIds);
    }

    public ObjectArraySkipContentRecord readObjectArraySkipContentRecord() {
        long id = readId();
        int stackTraceSerialNumber = readInt();
        int arrayLength = readInt();
        long arrayClassId = readId();
        skip(identifierByteSize * arrayLength);
        return reusedObjectArraySkipContentRecord.apply(
                (record) -> {
                    record.setId(id);
                    record.setStackTraceSerialNumber(stackTraceSerialNumber);
                    record.setArrayClassId(arrayClassId);
                    record.setSize(arrayLength);
                }
        );
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
        position += SHORT_SIZE;
        return source.readShort();
    }

    private int readInt() {
        position += INT_SIZE;
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
        String str = readString(CHAR_SIZE * arrayLength, Charset.forName("UTF-16BE"));
        return str.toCharArray();
    }

    private String readString(int byteCount, Charset charset) {
        position += byteCount;
        return source.readString(byteCount, charset);
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
        return readString(CHAR_SIZE, Charset.forName("UTF-16BE")).charAt(0);
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
        position += byteCount;
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
        position += byteCount;
        source.skip(byteCount);
    }

    private void skipInstanceDumpRecord() {
        skip(identifierByteSize + INT_SIZE + identifierByteSize);
        int remainingBytesInInstance = readInt();
        skip(remainingBytesInInstance);
    }

    private void skipClassDumpRecord() {
        skip(identifierByteSize + INT_SIZE + identifierByteSize + identifierByteSize +
                identifierByteSize + identifierByteSize + identifierByteSize +
                identifierByteSize + INT_SIZE);
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
        return new HeapDumpInfoRecord(heapId, readId());
    }

    private void skipHeapDumpInfoRecord() {
        skip(identifierByteSize + identifierByteSize);
    }
}