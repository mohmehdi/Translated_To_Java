

package shark;

import okio.Source;
import shark.HprofRecord.HeapDumpEndRecord;
import shark.HprofRecord.HeapDumpRecord;
import shark.HprofRecord.HeapDumpRecord.GcRootRecord;
import shark.HprofRecord.HeapDumpRecord.HeapDumpInfoRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassSkipContentRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.InstanceDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.InstanceSkipContentRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ObjectArrayDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ObjectArraySkipContentRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArraySkipContentRecord;
import shark.HprofRecord.LoadClassRecord;
import shark.HprofRecord.StackFrameRecord;
import shark.HprofRecord.StackTraceRecord;
import shark.HprofRecord.StringRecord;
import shark.PrimitiveType.BOOLEAN;
import shark.PrimitiveType.Companion;
import shark.PrimitiveType.INT;
import java.io.File;
import kotlin.reflect.KClass;

public class StreamingHprofReader {
    private final StreamingSourceProvider sourceProvider;
    private final HprofHeader header;

    private StreamingHprofReader(StreamingSourceProvider sourceProvider, HprofHeader header) {
        this.sourceProvider = sourceProvider;
        this.header = header;
    }

    public long readRecords(Set<KClass<? extends HprofRecord>> recordTypes, OnHprofRecordListener listener) {
        boolean readAllRecords = HprofRecord.class in recordTypes;
        boolean readStringRecord = readAllRecords || StringRecord.class in recordTypes;
        boolean readLoadClassRecord = readAllRecords || LoadClassRecord.class in recordTypes;
        boolean readHeapDumpEndRecord = readAllRecords || HeapDumpEndRecord.class in recordTypes;
        boolean readStackFrameRecord = readAllRecords || StackFrameRecord.class in recordTypes;
        boolean readStackTraceRecord = readAllRecords || StackTraceRecord.class in recordTypes;
        boolean readAllHeapDumpRecords = readAllRecords || HeapDumpRecord.class in recordTypes;
        boolean readGcRootRecord = readAllHeapDumpRecords || GcRootRecord.class in recordTypes;
        boolean readHeapDumpInfoRecord = readAllRecords || HeapDumpInfoRecord.class in recordTypes;
        boolean readAllObjectRecords = readAllHeapDumpRecords || ObjectRecord.class in recordTypes;
        boolean readClassDumpRecord = readAllObjectRecords || ClassDumpRecord.class in recordTypes;
        boolean readClassSkipContentRecord = ClassSkipContentRecord.class in recordTypes;
        boolean readInstanceDumpRecord = readAllObjectRecords || InstanceDumpRecord.class in recordTypes;
        boolean readInstanceSkipContentRecord = InstanceSkipContentRecord.class in recordTypes;
        boolean readObjectArrayDumpRecord = readAllObjectRecords || ObjectArrayDumpRecord.class in recordTypes;
        boolean readObjectArraySkipContentRecord = ObjectArraySkipContentRecord.class in recordTypes;
        boolean readPrimitiveArrayDumpRecord = readAllObjectRecords || PrimitiveArrayDumpRecord.class in recordTypes;
        boolean readPrimitiveArraySkipContentRecord = PrimitiveArraySkipContentRecord.class in recordTypes;

        InstanceSkipContentRecord reusedInstanceSkipContentRecord = new InstanceSkipContentRecord(0, 0, 0);
        ClassSkipContentRecord reusedClassSkipContentRecord = new ClassSkipContentRecord(0, 0, 0, 0, 0, 0, 0, 0, 0);
        ObjectArraySkipContentRecord reusedObjectArraySkipContentRecord = new ObjectArraySkipContentRecord(0, 0, 0, 0);
        PrimitiveArraySkipContentRecord reusedPrimitiveArraySkipContentRecord = new PrimitiveArraySkipContentRecord(0, 0, 0, BOOLEAN);
        LoadClassRecord reusedLoadClassRecord = new LoadClassRecord(0, 0, 0, 0);

        return sourceProvider.openStreamingSource().use(source -> {
            HprofRecordReader reader = new HprofRecordReader(header, source);
            reader.skip(header.recordsPosition);

            int intByteSize = INT.byteSize;
            int identifierByteSize = reader.sizeOf(REFERENCE_HPROF_TYPE);

            while (!source.exhausted()) {
                int tag = reader.readUnsignedByte();
                reader.skip(intByteSize);
                long length = reader.readUnsignedInt();

                switch (tag) {
                    case STRING_IN_UTF8:
                        if (readStringRecord) {
                            long recordPosition = reader.bytesRead;
                            StringRecord record = reader.readStringRecord(length);
                            listener.onHprofRecord(recordPosition, record);
                        } else {
                            reader.skip(length);
                        }
                        break;
                    case LOAD_CLASS:
                        if (readLoadClassRecord) {
                            long recordPosition = reader.bytesRead;
                            reusedLoadClassRecord.read();
                            listener.onHprofRecord(recordPosition, reusedLoadClassRecord);
                        } else {
                            reader.skip(length);
                        }
                        break;
                    case STACK_FRAME:
                        if (readStackFrameRecord) {
                            long recordPosition = reader.bytesRead;
                            StackFrameRecord record = reader.readStackFrameRecord();
                            listener.onHprofRecord(recordPosition, record);
                        } else {
                            reader.skip(length);
                        }
                        break;
                    case STACK_TRACE:
                        if (readStackTraceRecord) {
                            long recordPosition = reader.bytesRead;
                            StackTraceRecord record = reader.readStackTraceRecord();
                            listener.onHprofRecord(recordPosition, record);
                        } else {
                            reader.skip(length);
                        }
                        break;
                    case HEAP_DUMP:
                    case HEAP_DUMP_SEGMENT:
                        long heapDumpStart = reader.bytesRead;
                        int previousTag = 0;
                        long previousTagPosition = 0L;
                        while (reader.bytesRead - heapDumpStart < length) {
                            long heapDumpTagPosition = reader.bytesRead;
                            int heapDumpTag = reader.readUnsignedByte();
                            switch (heapDumpTag) {
                                case ROOT_UNKNOWN:
                                    if (readGcRootRecord) {
                                        long recordPosition = reader.bytesRead;
                                        GcRootRecord record = reader.readUnknownGcRootRecord();
                                        listener.onHprofRecord(recordPosition, record);
                                    } else {
                                        reader.skip(identifierByteSize);
                                    }
                                    break;
                                case ROOT_JNI_GLOBAL:
                                    if (readGcRootRecord) {
                                        long recordPosition = reader.bytesRead;
                                        GcRootRecord gcRootRecord = reader.readJniGlobalGcRootRecord();
                                        listener.onHprofRecord(recordPosition, gcRootRecord);
                                    } else {
                                        reader.skip(identifierByteSize + identifierByteSize);
                                    }
                                    break;
                                case ROOT_JNI_LOCAL:
                                    if (readGcRootRecord) {
                                        long recordPosition = reader.bytesRead;
                                        GcRootRecord gcRootRecord = reader.readJniLocalGcRootRecord();
                                        listener.onHprofRecord(recordPosition, gcRootRecord);
                                    } else {
                                        reader.skip(identifierByteSize + intByteSize + intByteSize);
                                    }
                                    break;
                                case ROOT_JAVA_FRAME:
                                    if (readGcRootRecord) {
                                        long recordPosition = reader.bytesRead;
                                        GcRootRecord gcRootRecord = reader.readJavaFrameGcRootRecord();
                                        listener.onHprofRecord(recordPosition, gcRootRecord);
                                    } else {
                                        reader.skip(identifierByteSize + intByteSize + intByteSize);
                                    }
                                    break;
                                case ROOT_NATIVE_STACK:
                                    if (readGcRootRecord) {
                                        long recordPosition = reader.bytesRead;
                                        GcRootRecord gcRootRecord = reader.readNativeStackGcRootRecord();
                                        listener.onHprofRecord(recordPosition, gcRootRecord);
                                    } else {
                                        reader.skip(identifierByteSize + intByteSize);
                                    }
                                    break;
                                case ROOT_STICKY_CLASS:
                                    if (readGcRootRecord) {
                                        long recordPosition = reader.bytesRead;
                                        GcRootRecord gcRootRecord = reader.readStickyClassGcRootRecord();
                                        listener.onHprofRecord(recordPosition, gcRootRecord);
                                    } else {
                                        reader.skip(identifierByteSize);
                                    }
                                    break;
                                case ROOT_THREAD_BLOCK:
                                    if (readGcRootRecord) {
                                        long recordPosition = reader.bytesRead;
                                        listener.onHprofRecord(recordPosition, reader.readThreadBlockGcRootRecord());
                                    } else {
                                        reader.skip(identifierByteSize + intByteSize);
                                    }
                                    break;
                                case ROOT_MONITOR_USED:
                                    if (readGcRootRecord) {
                                        long recordPosition = reader.bytesRead;
                                        GcRootRecord gcRootRecord = reader.readMonitorUsedGcRootRecord();
                                        listener.onHprofRecord(recordPosition, gcRootRecord);
                                    } else {
                                        reader.skip(identifierByteSize);
                                    }
                                    break;
                                case ROOT_THREAD_OBJECT:
                                    if (readGcRootRecord) {
                                        long recordPosition = reader.bytesRead;
                                        GcRootRecord gcRootRecord = reader.readThreadObjectGcRootRecord();
                                        listener.onHprofRecord(recordPosition, gcRootRecord);
                                    } else {
                                        reader.skip(identifierByteSize + intByteSize + intByteSize);
                                    }
                                    break;
                                case ROOT_INTERNED_STRING:
                                    if (readGcRootRecord) {
                                        long recordPosition = reader.bytesRead;
                                        GcRootRecord gcRootRecord = reader.readInternedStringGcRootRecord();
                                        listener.onHprofRecord(recordPosition, gcRootRecord);
                                    } else {
                                        reader.skip(identifierByteSize);
                                    }
                                    break;
                                case ROOT_FINALIZING:
                                    if (readGcRootRecord) {
                                        long recordPosition = reader.bytesRead;
                                        GcRootRecord gcRootRecord = reader.readFinalizingGcRootRecord();
                                        listener.onHprofRecord(recordPosition, gcRootRecord);
                                    } else {
                                        reader.skip(identifierByteSize);
                                    }
                                    break;
                                case ROOT_DEBUGGER:
                                    if (readGcRootRecord) {
                                        long recordPosition = reader.bytesRead;
                                        GcRootRecord gcRootRecord = reader.readDebuggerGcRootRecord();
                                        listener.onHprofRecord(recordPosition, gcRootRecord);
                                    } else {
                                        reader.skip(identifierByteSize);
                                    }
                                    break;
                                case ROOT_REFERENCE_CLEANUP:
                                    if (readGcRootRecord) {
                                        long recordPosition = reader.bytesRead;
                                        GcRootRecord gcRootRecord = reader.readReferenceCleanupGcRootRecord();
                                        listener.onHprofRecord(recordPosition, gcRootRecord);
                                    } else {
                                        reader.skip(identifierByteSize);
                                    }
                                    break;
                                case ROOT_VM_INTERNAL:
                                    if (readGcRootRecord) {
                                        long recordPosition = reader.bytesRead;
                                        GcRootRecord gcRootRecord = reader.readVmInternalGcRootRecord();
                                        listener.onHprofRecord(recordPosition, gcRootRecord);
                                    } else {
                                        reader.skip(identifierByteSize);
                                    }
                                    break;
                                case ROOT_JNI_MONITOR:
                                    if (readGcRootRecord) {
                                        long recordPosition = reader.bytesRead;
                                        GcRootRecord gcRootRecord = reader.readJniMonitorGcRootRecord();
                                        listener.onHprofRecord(recordPosition, gcRootRecord);
                                    } else {
                                        reader.skip(identifierByteSize + intByteSize + intByteSize);
                                    }
                                    break;
                                case ROOT_UNREACHABLE:
                                    if (readGcRootRecord) {
                                        long recordPosition = reader.bytesRead;
                                        GcRootRecord gcRootRecord = reader.readUnreachableGcRootRecord();
                                        listener.onHprofRecord(recordPosition, gcRootRecord);
                                    } else {
                                        reader.skip(identifierByteSize);
                                    }
                                    break;
                                case CLASS_DUMP:
                                    if (readClassDumpRecord) {
                                        long recordPosition = reader.bytesRead;
                                        ClassDumpRecord record = reader.readClassDumpRecord();
                                        listener.onHprofRecord(recordPosition, record);
                                    } else if (readClassSkipContentRecord) {
                                        long recordPosition = reader.bytesRead;
                                        reusedClassSkipContentRecord.read();
                                        listener.onHprofRecord(recordPosition, reusedClassSkipContentRecord);
                                    } else {
                                        reader.skipClassDumpRecord();
                                    }
                                    break;
                                case INSTANCE_DUMP:
                                    if (readInstanceDumpRecord) {
                                        long recordPosition = reader.bytesRead;
                                        InstanceDumpRecord record = reader.readInstanceDumpRecord();
                                        listener.onHprofRecord(recordPosition, record);
                                    } else if (readInstanceSkipContentRecord) {
                                        long recordPosition = reader.bytesRead;
                                        reusedInstanceSkipContentRecord.read();
                                        listener.onHprofRecord(recordPosition, reusedInstanceSkipContentRecord);
                                    } else {
                                        reader.skipInstanceDumpRecord();
                                    }
                                    break;
                                case OBJECT_ARRAY_DUMP:
                                    if (readObjectArrayDumpRecord) {
                                        long recordPosition = reader.bytesRead;
                                        ObjectArrayDumpRecord arrayRecord = reader.readObjectArrayDumpRecord();
                                        listener.onHprofRecord(recordPosition, arrayRecord);
                                    } else if (readObjectArraySkipContentRecord) {
                                        long recordPosition = reader.bytesRead;
                                        reusedObjectArraySkipContentRecord.read();
                                        listener.onHprofRecord(recordPosition, reusedObjectArraySkipContentRecord);
                                    } else {
                                        reader.skipObjectArrayDumpRecord();
                                    }
                                    break;
                                case PRIMITIVE_ARRAY_DUMP:
                                    if (readPrimitiveArrayDumpRecord) {
                                        long recordPosition = reader.bytesRead;
                                        PrimitiveArrayDumpRecord record = reader.readPrimitiveArrayDumpRecord();
                                        listener.onHprofRecord(recordPosition, record);
                                    } else if (readPrimitiveArraySkipContentRecord) {
                                        long recordPosition = reader.bytesRead;
                                        reusedPrimitiveArraySkipContentRecord.read();
                                        listener.onHprofRecord(recordPosition, reusedPrimitiveArraySkipContentRecord);
                                    } else {
                                        reader.skipPrimitiveArrayDumpRecord();
                                    }
                                    break;
                                case PRIMITIVE_ARRAY_NODATA:
                                    throw new UnsupportedOperationException("PRIMITIVE_ARRAY_NODATA cannot be parsed");
                                case HEAP_DUMP_INFO:
                                    if (readHeapDumpInfoRecord) {
                                        long recordPosition = reader.bytesRead;
                                        HeapDumpInfoRecord record = reader.readHeapDumpInfoRecord();
                                        listener.onHprofRecord(recordPosition, record);
                                    } else {
                                        reader.skipHeapDumpInfoRecord();
                                    }
                                    break;
                                default:
                                    throw new IllegalStateException("Unknown tag " + String.format("0x%02x", heapDumpTag) +
                                            " at " + heapDumpTagPosition + " after " + String.format("0x%02x", previousTag) +
                                            " at " + previousTagPosition);
                            }
                            previousTag = heapDumpTag;
                            previousTagPosition = heapDumpTagPosition;
                        }
                        break;
                    case HEAP_DUMP_END:
                        if (readHeapDumpEndRecord) {
                            long recordPosition = reader.bytesRead;
                            HeapDumpEndRecord record = HeapDumpEndRecord;
                            listener.onHprofRecord(recordPosition, record);
                        }
                        break;
                    default:
                        reader.skip(length);
                        break;
                }
            }
            return reader.bytesRead;
        });
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

    public static StreamingHprofReader readerFor(File hprofFile, HprofHeader hprofHeader) {
        FileSourceProvider sourceProvider = new FileSourceProvider(hprofFile);
        return readerFor(sourceProvider, hprofHeader);
    }

    public static StreamingHprofReader readerFor(StreamingSourceProvider hprofSourceProvider, HprofHeader hprofHeader) {
        return new StreamingHprofReader(hprofSourceProvider, hprofHeader);
    }
}