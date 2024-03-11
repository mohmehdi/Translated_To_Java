

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
    private int identifierByteSize;
    private long byteReadCount;
    private final int[] typeSizes = new int[PrimitiveType.values().length];

    public HprofReader(BufferedSource source, int identifierByteSize, long startByteReadCount) {
        this.source = source;
        this.identifierByteSize = identifierByteSize;
        this.byteReadCount = startByteReadCount;
        initTypeSizes();
    }



    public void readHprofRecords(
            Set<Class<? extends HprofRecord>> recordTypes,
            OnHprofRecordListener listener) {
        if (byteReadCount != startByteReadCount) {
            throw new IllegalStateException(
                    "readHprofRecords() should only be called on a unused HprofReader instance");
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

            long length = readUnsignedInt();

            switch (tag) {
                case STRING_IN_UTF8: {
                    if (readStringRecord) {
                        long recordPosition = byteReadCount;
                        int id = (int) readId();
                        int stringLength = (int) (length - identifierByteSize);
                        String string = readUtf8(stringLength);
                        StringRecord record = new StringRecord(id, string);
                        listener.onHprofRecord(recordPosition, record);
                    } else {
                        skip(length);
                    }
                    break;
                }
                case LOAD_CLASS: {
                    if (readLoadClassRecord) {
                        long recordPosition = byteReadCount;
                        int classSerialNumber = (int) readInt();
                        int id = (int) readId();
                        int stackTraceSerialNumber = (int) readInt();
                        int classNameStringId = (int) readId();
                        LoadClassRecord record = new LoadClassRecord(
                                classSerialNumber,
                                id,
                                stackTraceSerialNumber,
                                classNameStringId);
                        listener.onHprofRecord(recordPosition, record);
                    } else {
                        skip(length);
                    }
                    break;
                }
                case STACK_FRAME: {
                    if (readStackFrameRecord) {
                        long recordPosition = byteReadCount;
                        int id = (int) readId();
                        int methodNameStringId = (int) readId();
                        int methodSignatureStringId = (int) readId();
                        int sourceFileNameStringId = (int) readId();
                        int classSerialNumber = (int) readInt();
                        int lineNumber = (int) readInt();
                        StackFrameRecord record = new StackFrameRecord(
                                id,
                                methodNameStringId,
                                methodSignatureStringId,
                                sourceFileNameStringId,
                                classSerialNumber,
                                lineNumber);
                        listener.onHprofRecord(recordPosition, record);
                    } else {
                        skip(length);
                    }
                    break;
                }
                case STACK_TRACE: {
                    if (readStackTraceRecord) {
                        long recordPosition = byteReadCount;
                        int stackTraceSerialNumber = (int) readInt();
                        int threadSerialNumber = (int) readInt();
                        int frameCount = (int) readInt();
                        long[] stackFrameIds = readIdArray(frameCount);
                        StackTraceRecord record = new StackTraceRecord(
                                stackTraceSerialNumber,
                                threadSerialNumber,
                                stackFrameIds);
                        listener.onHprofRecord(recordPosition, record);
                    } else {
                        skip(length);
                    }
                    break;
                }
                case HEAP_DUMP:
                case HEAP_DUMP_SEGMENT: {
                    long heapDumpStart = byteReadCount;
                    int previousTag = 0;
                    while (byteReadCount - heapDumpStart < length) {
                        int heapDumpTag = readUnsignedByte();

                        switch (heapDumpTag) {
                            case ROOT_UNKNOWN: {
                                if (readGcRootRecord) {
                                    long recordPosition = byteReadCount;
                                    GcRootRecord record = new GcRootRecord(
                                            gcRoot = new Unknown(id));
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skip(identifierByteSize);
                                }
                                break;
                            }
                            case ROOT_JNI_GLOBAL: {
                                if (readGcRootRecord) {
                                    long recordPosition = byteReadCount;
                                    GcRootRecord record = new GcRootRecord(
                                            gcRoot = new JniGlobal(id, jniGlobalRefId));
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skip(identifierByteSize + identifierByteSize);
                                }
                                break;
                            }

                            case ROOT_JNI_LOCAL: {
                                if (readGcRootRecord) {
                                    long recordPosition = byteReadCount;
                                    GcRootRecord record = new GcRootRecord(
                                            gcRoot = new JniLocal(
                                                    id,
                                                    threadSerialNumber,
                                                    frameNumber));
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skip(identifierByteSize + intByteSize + intByteSize);
                                }
                                break;
                            }

                            case ROOT_JAVA_FRAME: {
                                if (readGcRootRecord) {
                                    long recordPosition = byteReadCount;
                                    GcRootRecord record = new GcRootRecord(
                                            gcRoot = new JavaFrame(
                                                    id,
                                                    threadSerialNumber,
                                                    frameNumber));
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skip(identifierByteSize + intByteSize + intByteSize);
                                }
                                break;
                            }

                            case ROOT_NATIVE_STACK: {
                                if (readGcRootRecord) {
                                    long recordPosition = byteReadCount;
                                    GcRootRecord record = new GcRootRecord(
                                            gcRoot = new NativeStack(id, threadSerialNumber));
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skip(identifierByteSize + intByteSize);
                                }
                                break;
                            }

                            case ROOT_STICKY_CLASS: {
                                if (readGcRootRecord) {
                                    long recordPosition = byteReadCount;
                                    GcRootRecord record = new GcRootRecord(
                                            gcRoot = new StickyClass(id));
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skip(identifierByteSize);
                                }
                                break;
                            }

                            case ROOT_THREAD_BLOCK: {
                                if (readGcRootRecord) {
                                    long recordPosition = byteReadCount;
                                    GcRootRecord record = new GcRootRecord(
                                            gcRoot = new ThreadBlock(id, threadSerialNumber));
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skip(identifierByteSize + intByteSize);
                                }
                                break;
                            }

                            case ROOT_MONITOR_USED: {
                                if (readGcRootRecord) {
                                    long recordPosition = byteReadCount;
                                    GcRootRecord record = new GcRootRecord(
                                            gcRoot = new MonitorUsed(id));
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skip(identifierByteSize);
                                }
                                break;
                            }

                            case ROOT_THREAD_OBJECT: {
                                if (readGcRootRecord) {
                                    long recordPosition = byteReadCount;
                                    GcRootRecord record = new GcRootRecord(
                                            gcRoot = new ThreadObject(
                                                    id,
                                                    threadSerialNumber,
                                                    stackTraceSerialNumber));
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skip(identifierByteSize + intByteSize + intByteSize);
                                }
                                break;
                            }

                            case ROOT_INTERNED_STRING: {
                                if (readGcRootRecord) {
                                    long recordPosition = byteReadCount;
                                    GcRootRecord record = new GcRootRecord(
                                            gcRoot = new InternedString(id));
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skip(identifierByteSize);
                                }
                                break;
                            }

                            case ROOT_FINALIZING: {
                                if (readGcRootRecord) {
                                    long recordPosition = byteReadCount;
                                    GcRootRecord record = new GcRootRecord(
                                            gcRoot = new Finalizing(id));
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skip(identifierByteSize);
                                }
                                break;
                            }

                            case ROOT_DEBUGGER: {
                                if (readGcRootRecord) {
                                    long recordPosition = byteReadCount;
                                    GcRootRecord record = new GcRootRecord(
                                            gcRoot = new Debugger(id));
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skip(identifierByteSize);
                                }
                                break;
                            }

                            case ROOT_REFERENCE_CLEANUP: {
                                if (readGcRootRecord) {
                                    long recordPosition = byteReadCount;
                                    GcRootRecord record = new GcRootRecord(
                                            gcRoot = new ReferenceCleanup(id));
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skip(identifierByteSize);
                                }
                                break;
                            }

                            case ROOT_VM_INTERNAL: {
                                if (readGcRootRecord) {
                                    long recordPosition = byteReadCount;
                                    GcRootRecord record = new GcRootRecord(
                                            gcRoot = new VmInternal(id));
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skip(identifierByteSize);
                                }
                                break;
                            }

                            case ROOT_JNI_MONITOR: {
                                if (readGcRootRecord) {
                                    long recordPosition = byteReadCount;
                                    GcRootRecord record = new GcRootRecord(
                                            gcRoot = new JniMonitor(
                                                    id,
                                                    stackTraceSerialNumber,
                                                    stackDepth));
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skip(identifierByteSize + intByteSize + intByteSize);
                                }
                                break;
                            }

                            case ROOT_UNREACHABLE: {
                                if (readGcRootRecord) {
                                    long recordPosition = byteReadCount;
                                    GcRootRecord record = new GcRootRecord(
                                            gcRoot = new Unreachable(id));
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skip(identifierByteSize);
                                }
                                break;
                            }
                            case CLASS_DUMP: {
                                if (readClassDumpRecord) {
                                    long recordPosition = byteReadCount;
                                    ClassDumpRecord record = readClassDumpRecord();
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skipClassDumpRecord();
                                }
                                break;
                            }

                            case INSTANCE_DUMP: {
                                if (readInstanceDumpRecord) {
                                    long recordPosition = byteReadCount;
                                    InstanceDumpRecord record = readInstanceDumpRecord();
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skipInstanceDumpRecord();
                                }
                                break;
                            }

                            case OBJECT_ARRAY_DUMP: {
                                if (readObjectArrayDumpRecord) {
                                    long recordPosition = byteReadCount;
                                    ObjectArrayDumpRecord record = readObjectArrayDumpRecord();
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skipObjectArrayDumpRecord();
                                }
                                break;
                            }

                            case PRIMITIVE_ARRAY_DUMP: {
                                if (readPrimitiveArrayDumpRecord) {
                                    long recordPosition = byteReadCount;
                                    PrimitiveArrayDumpRecord record = readPrimitiveArrayDumpRecord();
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skipPrimitiveArrayDumpRecord();
                                }
                                break;
                            }

                            case PRIMITIVE_ARRAY_NODATA: {
                                throw new UnsupportedOperationException("PRIMITIVE_ARRAY_NODATA cannot be parsed");
                            }

                            case HEAP_DUMP_INFO: {
                                if (readHeapDumpInfoRecord) {
                                    long recordPosition = byteReadCount;
                                    HeapDumpInfoRecord record = readHeapDumpInfoRecord();
                                    listener.onHprofRecord(recordPosition, record);
                                } else {
                                    skipHeapDumpInfoRecord();
                                }
                                break;
                            }
                            default:
                                throw new IllegalStateException("Unknown tag " + heapDumpTag + " after " + previousTag);
                        }
                        previousTag = heapDumpTag;
                    }
                    break;
                }
                case HEAP_DUMP_END: {
                    if (readHeapDumpEndRecord) {
                        long recordPosition = byteReadCount;
                        HeapDumpEndRecord record = new HeapDumpEndRecord();
                        listener.onHprofRecord(recordPosition, record);
                    }
                    break;
                }
                default:
                    skip(length);
            }
        }
    }

    private InstanceDumpRecord readInstanceDumpRecord() {
        int id = (int) readId();
        int stackTraceSerialNumber = (int) readInt();
        int classId = (int) readId();
        int remainingBytesInInstance = (int) readInt();
        byte[] fieldValues = readByteArray(remainingBytesInInstance);
        return new InstanceDumpRecord(
                id,
                stackTraceSerialNumber,
                classId,
                fieldValues);
    }

    private ClassDumpRecord readClassDumpRecord() {
        int id = (int) readId();

        int stackTraceSerialNumber = (int) readInt();
        int superclassId = (int) readId();

        int classLoaderId = (int) readId();

        int signersId = (int) readId();

        int protectionDomainId = (int) readId();

        readId();

        readId();

        int instanceSize = (int) readInt();

        int constantPoolCount = (int) readUnsignedShort();
        for (int i = 0; i < constantPoolCount; i++) {

            skip(SHORT_SIZE);
            skip(typeSize(readUnsignedByte()));
        }

        int staticFieldCount = (int) readUnsignedShort();
        ArrayList<StaticFieldRecord> staticFields = new ArrayList<>(staticFieldCount);
        for (int i = 0; i < staticFieldCount; i++) {

            int nameStringId = (int) readId();
            int type = (int) readUnsignedByte();
            ValueHolder value = readValue(type);

            staticFields.add(
                    new StaticFieldRecord(
                            nameStringId,
                            type,
                            value));
        }

        int fieldCount = (int) readUnsignedShort();
        ArrayList<FieldRecord> fields = new ArrayList<>(fieldCount);
        for (int i = 0; i < fieldCount; i++) {
            fields.add(new FieldRecord(nameStringId, type));
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
                fields);
    }

    private PrimitiveArrayDumpRecord readPrimitiveArrayDumpRecord() {
        int id = (int) readId();
        int stackTraceSerialNumber = (int) readInt();

        int arrayLength = (int) readInt();
        return switch (readUnsignedByte()) {
            case BOOLEAN_TYPE -> new BooleanArrayDump(
                    id, stackTraceSerialNumber, readBooleanArray(arrayLength));
            case CHAR_TYPE -> new CharArrayDump(
                    id, stackTraceSerialNumber, readCharArray(arrayLength));
            case FLOAT_TYPE -> new FloatArrayDump(
                    id, stackTraceSerialNumber, readFloatArray(arrayLength));
            case DOUBLE_TYPE -> new DoubleArrayDump(
                    id, stackTraceSerialNumber, readDoubleArray(arrayLength));
            case BYTE_TYPE -> new ByteArrayDump(
                    id, stackTraceSerialNumber, readByteArray(arrayLength));
            case SHORT_TYPE -> new ShortArrayDump(
                    id, stackTraceSerialNumber, readShortArray(arrayLength));
            case INT_TYPE -> new IntArrayDump(
                    id, stackTraceSerialNumber, readIntArray(arrayLength));
            case LONG_TYPE -> new LongArrayDump(
                    id, stackTraceSerialNumber, readLongArray(arrayLength));
            default -> throw new IllegalStateException("Unexpected type " + type);
        };
    }

    private ObjectArrayDumpRecord readObjectArrayDumpRecord() {
        int id = (int) readId();

        int stackTraceSerialNumber = (int) readInt();
        int arrayLength = (int) readInt();
        int arrayClassId = (int) readId();
        long[] elementIds = readIdArray(arrayLength);
        return new ObjectArrayDumpRecord(
                id,
                stackTraceSerialNumber,
                arrayClassId,
                elementIds);
    }

    private ValueHolder readValue(int type) {
        return switch (type) {
            case PrimitiveType.REFERENCE_HPROF_TYPE -> new ReferenceHolder(readId());
            case BOOLEAN_TYPE -> new BooleanHolder(readBoolean());
            case CHAR_TYPE -> new CharHolder(readChar());
            case FLOAT_TYPE -> new FloatHolder(readFloat());
            case DOUBLE_TYPE -> new DoubleHolder(readDouble());
            case BYTE_TYPE -> new ByteHolder(readByte());
            case SHORT_TYPE -> new ShortHolder(readShort());
            case INT_TYPE -> new IntHolder(readInt());
            case LONG_TYPE -> new LongHolder(readLong());
            default -> throw new IllegalStateException("Unknown type " + type);
        };
    }

    private int typeSize(int type) {
        return typeSizes[type];
    }

    private int readShort() {
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
        return readString(CHAR_SIZE * arrayLength, Charsets.UTF_16BE).toCharArray();
    }

    private String readString(
            int byteCount,
            Charset charset) {
        byteReadCount += byteCount;
        return source.readUtf8(byteCount);
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
            array[i] = Double.longBitsToDouble(readLong());
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
        byteReadCount += LONG_SIZE;
        return source.readLong();
    }

    private boolean exhausted() {
        return source.exhausted();
    }

    private void skip(long byteCount) {
        byteReadCount += byteCount;
        source.skip(byteCount);
    }

    private int readByte() {
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
                return source.readByte() & 0xFFL;
            case 2:
                return source.readShort() & 0xFFFFL;
            case 4:
                return source.readInt() & 0xFFFFFFFFL;
            case 8:
                return source.readLong();
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
        return source.readByte() & BYTE_MASK;
    }

    private int readUnsignedShort() {
        return source.readShort() & 0xFFFF;
    }

    private void skip(int byteCount) {
        byteReadCount += byteCount;
        source.skip(byteCount);
    }

    private void skipInstanceDumpRecord() {
        skip(identifierByteSize + INT_SIZE + identifierByteSize);
        int remainingBytesInInstance = (int) readInt();
        skip(remainingBytesInInstance);
    }

    private void skipClassDumpRecord() {
        skip(
                identifierByteSize + INT_SIZE + identifierByteSize + identifierByteSize + identifierByteSize + identifierByteSize + identifierByteSize + identifierByteSize + INT_SIZE
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
        int arrayLength = (int) readInt();
        skip(identifierByteSize + arrayLength * identifierByteSize);
    }

    private void skipPrimitiveArrayDumpRecord() {
        skip(identifierByteSize + INT_SIZE);
        int arrayLength = (int) readInt();
        int type = readUnsignedByte();
        skip(identifierByteSize + arrayLength * typeSize(type));
    }

    private HeapDumpInfoRecord readHeapDumpInfoRecord() {
        int heapId = (int) readInt();
        return new HeapDumpInfoRecord(heapId, readId());
    }

    private void skipHeapDumpInfoRecord() {
        skip(identifierByteSize + identifierByteSize);
    }

    companion object {
        private final int BOOLEAN_SIZE = BOOLEAN.byteSize;
        private final int CHAR_SIZE = CHAR.byteSize;
        private final int FLOAT_SIZE = FLOAT.byteSize;
        private final int DOUBLE_SIZE = DOUBLE.byteSize;
        private final int BYTE_SIZE = BYTE.byteSize;
        private final int SHORT_SIZE = SHORT.byteSize;
        private final int INT_SIZE = INT.byteSize;
        private final int LONG_SIZE = LONG.byteSize;

        private final int BOOLEAN_TYPE = BOOLEAN.hprofType;
        private final int CHAR_TYPE = CHAR.hprofType;
        private final int FLOAT_TYPE = FLOAT.hprofType;
        private final int DOUBLE_TYPE = DOUBLE.hprofType;
        private final int BYTE_TYPE = BYTE.hprofType;
        private final int SHORT_TYPE = SHORT.hprofType;
        private final int INT_TYPE = INT.hprofType;
        private final int LONG_TYPE = LONG.hprofType;

        private final long INT_MASK = 0xffffffffL;
        private final int BYTE_MASK = 0xff;

        internal final int STRING_IN_UTF8 = 0x01;
        internal final int LOAD_CLASS = 0x02;
        internal final int UNLOAD_CLASS = 0x03;
        internal final int STACK_FRAME = 0x04;
        internal final int STACK_TRACE = 0x05;
        internal final int ALLOC_SITES = 0x06;
        internal final int HEAP_SUMMARY = 0x07;

        internal final int START_THREAD = 0x0a;
        internal final int END_THREAD = 0x0b;
        internal final int HEAP_DUMP = 0x0c;
        internal final int HEAP_DUMP_SEGMENT = 0x1c;
        internal final int HEAP_DUMP_END = 0x2c;
        internal final int CPU_SAMPLES = 0x0d;
        internal final int CONTROL_SETTINGS = 0x0e;
        internal final int ROOT_UNKNOWN = 0xff;
        internal final int ROOT_JNI_GLOBAL = 0x01;
        internal final int ROOT_JNI_LOCAL = 0x02;
        internal final int ROOT_JAVA_FRAME = 0x03;
        internal final int ROOT_NATIVE_STACK = 0x04;
        internal final int ROOT_STICKY_CLASS = 0x05;
        internal final int ROOT_THREAD_BLOCK = 0x06;
        internal final int ROOT_MONITOR_USED = 0x07;
        internal final int ROOT_THREAD_OBJECT = 0x08;
        internal final int CLASS_DUMP = 0x20;
        internal final int INSTANCE_DUMP = 0x21;
        internal final int OBJECT_ARRAY_DUMP = 0x22;
        internal final int PRIMITIVE_ARRAY_DUMP = 0x23;

        internal final int HEAP_DUMP_INFO = 0xfe;
        internal final int ROOT_INTERNED_STRING = 0x89;
        internal final int ROOT_FINALIZING = 0x8a;
        internal final int ROOT_DEBUGGER = 0x8b;
        internal final int ROOT_REFERENCE_CLEANUP = 0x8c;
        internal final int ROOT_VM_INTERNAL = 0x8d;
        internal final int ROOT_JNI_MONITOR = 0x8e;
        internal final int ROOT_UNREACHABLE = 0x90;
        internal final int PRIMITIVE_ARRAY_NODATA = 0xc3;
    }
}