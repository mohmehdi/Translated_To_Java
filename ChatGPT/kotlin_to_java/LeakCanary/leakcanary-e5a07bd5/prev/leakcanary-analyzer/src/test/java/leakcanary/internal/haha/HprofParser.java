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
import leakcanary.internal.haha.HprofParser.RecordCallbacks;
import leakcanary.internal.haha.HprofReader.Companion.BOOLEAN_TYPE;
import leakcanary.internal.haha.HprofReader.Companion.BYTE_SIZE;
import leakcanary.internal.haha.HprofReader.Companion.BYTE_TYPE;
import leakcanary.internal.haha.HprofReader.Companion.CHAR_TYPE;
import leakcanary.internal.haha.HprofReader.Companion.DOUBLE_TYPE;
import leakcanary.internal.haha.HprofReader.Companion.FLOAT_TYPE;
import leakcanary.internal.haha.HprofReader.Companion.INT_SIZE;
import leakcanary.internal.haha.HprofReader.Companion.INT_TYPE;
import leakcanary.internal.haha.HprofReader.Companion.LONG_TYPE;
import leakcanary.internal.haha.HprofReader.Companion.SHORT_TYPE;
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
import okio.ByteString.Companion.toByteString;
import okio.Buffer;
import okio.ByteString;
import okio.Okio;
import okio.Source;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.security.auth.callback.Callback;

public class HprofParser implements Closeable {

    private SeekableHprofReader reader;
    private boolean scanning = false;
    private boolean indexBuilt = false;
    private Map<Long, Pair<Long, Long>> hprofStringPositions = new HashMap<>();
    private Map<Long, Long> classNames = new HashMap<>();
    private Map<Long, Long> classPositions = new HashMap<>();
    private Map<Long, Long> instancePositions = new HashMap<>();

    public HprofParser(SeekableHprofReader reader) {
        this.reader = reader;
    }

    public void close() throws IOException {
        reader.close();
    }

    public void scan(RecordCallbacks callbacks) {
        reader.scan(callbacks);
    }

    private void scan(SeekableHprofReader reader, RecordCallbacks callbacks) {
        if (!reader.isOpen()) {
            throw new IllegalStateException("Reader closed");
        }

        if (reader.scanning) {
            throw new UnsupportedOperationException("Cannot scan while already scanning.");
        }

        reader.scanning = true;

        reader.reset();

        reader.skipLong();

        int heapId = 0;
        while (!reader.exhausted()) {
            int tag = reader.readUnsignedByte();

            reader.skipInt();

            long length = reader.readUnsignedInt();

            switch (tag) {
                case STRING_IN_UTF8:
                    StringRecordCallback stringCallback = callbacks.get(StringRecord.class);
                    if (stringCallback != null || !reader.indexBuilt) {
                        long id = reader.readId();
                        if (!reader.indexBuilt) {
                            reader.hprofStringPositions.put(id, new Pair<>(reader.position, length - reader.idSize));
                        }
                        if (stringCallback != null) {
                            String string = reader.readUtf8((int) (length - reader.idSize));
                            stringCallback.call(new StringRecord(id, string));
                        } else {
                            reader.skip(length - reader.idSize);
                        }
                    } else {
                        reader.skip(length);
                    }
                    break;
                case LOAD_CLASS:
                    LoadClassRecordCallback loadClassCallback = callbacks.get(LoadClassRecord.class);
                    if (loadClassCallback != null || !reader.indexBuilt) {
                        int classSerialNumber = reader.readInt();
                        long id = reader.readId();
                        int stackTraceSerialNumber = reader.readInt();
                        long classNameStringId = reader.readId();
                        if (!reader.indexBuilt) {
                            reader.classNames.put(id, classNameStringId);
                        }
                        if (loadClassCallback != null) {
                            loadClassCallback.call(new LoadClassRecord(classSerialNumber, id, stackTraceSerialNumber, classNameStringId));
                        }
                    } else {
                        reader.skip(length);
                    }
                    break;
                case HEAP_DUMP:
                case HEAP_DUMP_SEGMENT:
                    long heapDumpStart = reader.position;
                    int previousTag = 0;
                    while (reader.position - heapDumpStart < length) {
                        int heapDumpTag = reader.readUnsignedByte();
                        switch (heapDumpTag) {
                            case ROOT_UNKNOWN:
                                GcRootRecordCallback gcRootUnknownCallback = callbacks.get(GcRootRecord.class);
                                if (gcRootUnknownCallback != null) {
                                    gcRootUnknownCallback.call(new GcRootRecord(new Unknown(reader.readId())));
                                } else {
                                    reader.skip(reader.idSize);
                                }
                                break;
                            case ROOT_JNI_GLOBAL:
                                GcRootRecordCallback gcRootJniGlobalCallback = callbacks.get(GcRootRecord.class);
                                if (gcRootJniGlobalCallback != null) {
                                    gcRootJniGlobalCallback.call(new GcRootRecord(new JniGlobal(reader.readId(), reader.readId())));
                                } else {
                                    reader.skip(reader.idSize + reader.idSize);
                                }
                                break;
                            case ROOT_JNI_LOCAL:
                                GcRootRecordCallback gcRootJniLocalCallback = callbacks.get(GcRootRecord.class);
                                if (gcRootJniLocalCallback != null) {
                                    gcRootJniLocalCallback.call(new GcRootRecord(new JniLocal(reader.readId(), reader.readInt(), reader.readInt())));
                                } else {
                                    reader.skip(reader.idSize + Integer.SIZE + Integer.SIZE);
                                }
                                break;
                            case ROOT_JAVA_FRAME:
                                GcRootRecordCallback gcRootJavaFrameCallback = callbacks.get(GcRootRecord.class);
                                if (gcRootJavaFrameCallback != null) {
                                    gcRootJavaFrameCallback.call(new GcRootRecord(new JavaFrame(reader.readId(), reader.readInt(), reader.readInt())));
                                } else {
                                    reader.skip(reader.idSize + Integer.SIZE + Integer.SIZE);
                                }
                                break;
                            case ROOT_NATIVE_STACK:
                                GcRootRecordCallback gcRootNativeStackCallback = callbacks.get(GcRootRecord.class);
                                if (gcRootNativeStackCallback != null) {
                                    gcRootNativeStackCallback.call(new GcRootRecord(new NativeStack(reader.readId(), reader.readInt())));
                                } else {
                                    reader.skip(reader.idSize + Integer.SIZE);
                                }
                                break;
                            case ROOT_STICKY_CLASS:
                                GcRootRecordCallback gcRootStickyClassCallback = callbacks.get(GcRootRecord.class);
                                if (gcRootStickyClassCallback != null) {
                                    gcRootStickyClassCallback.call(new GcRootRecord(new StickyClass(reader.readId())));
                                } else {
                                    reader.skip(reader.idSize);
                                }
                                break;
                            case ROOT_THREAD_BLOCK:
                                GcRootRecordCallback gcRootThreadBlockCallback = callbacks.get(GcRootRecord.class);
                                if (gcRootThreadBlockCallback != null) {
                                    gcRootThreadBlockCallback.call(new GcRootRecord(new ThreadBlock(reader.readId(), reader.readInt())));
                                } else {
                                    reader.skip(reader.idSize + Integer.SIZE);
                                }
                                break;
                            case ROOT_MONITOR_USED:
                                GcRootRecordCallback gcRootMonitorUsedCallback = callbacks.get(GcRootRecord.class);
                                if (gcRootMonitorUsedCallback != null) {
                                    gcRootMonitorUsedCallback.call(new GcRootRecord(new MonitorUsed(reader.readId())));
                                } else {
                                    reader.skip(reader.idSize);
                                }
                                break;
                            case ROOT_THREAD_OBJECT:
                                GcRootRecordCallback gcRootThreadObjectCallback = callbacks.get(GcRootRecord.class);
                                if (gcRootThreadObjectCallback != null) {
                                    gcRootThreadObjectCallback.call(new GcRootRecord(new ThreadObject(reader.readId(), reader.readInt(), reader.readInt())));
                                } else {
                                    reader.skip(reader.idSize + Integer.SIZE + Integer.SIZE);
                                }
                                break;
                            case CLASS_DUMP:
                                ClassDumpRecordCallback classDumpCallback = callbacks.get(ClassDumpRecord.class);
                                long classId = reader.readId();
                                if (!reader.indexBuilt) {
                                    reader.classPositions.put(classId, reader.position);
                                }
                                if (classDumpCallback != null) {
                                    ClassDumpRecord classDumpRecord = reader.readClassDumpRecord(classId);
                                    classDumpCallback.call(classDumpRecord);
                                } else {
                                    reader.skip(Integer.SIZE + reader.idSize + reader.idSize + reader.idSize + reader.idSize + reader.idSize + reader.idSize + Integer.SIZE);
                                    int constantPoolCount = reader.readUnsignedShort();
                                    for (int i = 0; i < constantPoolCount; i++) {
                                        reader.skipShort();
                                        reader.skip(reader.typeSize(reader.readUnsignedByte()));
                                    }
                                    int staticFieldCount = reader.readUnsignedShort();
                                    for (int i = 0; i < staticFieldCount; i++) {
                                        reader.skip(reader.idSize);
                                        int type = reader.readUnsignedByte();
                                        reader.skip(reader.typeSize(type));
                                    }
                                    int fieldCount = reader.readUnsignedShort();
                                    reader.skip(fieldCount * (reader.idSize + Byte.SIZE));
                                }
                                break;
                            case INSTANCE_DUMP:
                                long instanceId = reader.readId();
                                if (!reader.indexBuilt) {
                                    reader.instancePositions.put(instanceId, reader.position);
                                }
                                InstanceDumpRecordCallback instanceDumpCallback = callbacks.get(InstanceDumpRecord.class);
                                if (instanceDumpCallback != null) {
                                    InstanceDump
                                    InstanceDumpRecordCallback instanceDumpCallback = callbacks.get(InstanceDumpRecord.class);
                                    if (instanceDumpCallback != null) {
                                        InstanceDumpRecord instanceDumpRecord = reader.readInstanceDumpRecord(instanceId);
                                        instanceDumpCallback.call(instanceDumpRecord);
                                    } else {
                                        reader.skip(Integer.SIZE + reader.idSize);
                                        int remainingBytesInInstance = reader.readInt();
                                        reader.skip(remainingBytesInInstance);
                                    }
                                    break;
                                case OBJECT_ARRAY_DUMP:
                                    ObjectArrayDumpRecordCallback objectArrayDumpCallback = callbacks.get(ObjectArrayDumpRecord.class);
                                    if (objectArrayDumpCallback != null) {
                                        long arrayId = reader.readId();
                                        int stackTraceSerialNumber = reader.readInt();
                                        int arrayLength = reader.readInt();
                                        long arrayClassId = reader.readId();
                                        long[] elementIds = reader.readIdArray(arrayLength);
                                        objectArrayDumpCallback.call(new ObjectArrayDumpRecord(arrayId, stackTraceSerialNumber, arrayClassId, elementIds));
                                    } else {
                                        reader.skip(reader.idSize + Integer.SIZE);
                                        int arrayLength = reader.readInt();
                                        reader.skip(reader.idSize + arrayLength * reader.idSize);
                                    }
                                    break;
                                case PRIMITIVE_ARRAY_DUMP:
                                    PrimitiveArrayDumpRecordCallback primitiveArrayDumpCallback = callbacks.get(PrimitiveArrayDumpRecord.class);
                                    if (primitiveArrayDumpCallback != null) {
                                        long arrayId = reader.readId();
                                        int stackTraceSerialNumber = reader.readInt();
                                        int arrayLength = reader.readInt();
                                        int type = reader.readUnsignedByte();
                                        PrimitiveArrayDumpRecord primitiveArrayDumpRecord;
                                        switch (type) {
                                            case BOOLEAN_TYPE:
                                                primitiveArrayDumpRecord = new BooleanArrayDump(arrayId, stackTraceSerialNumber, reader.readBooleanArray(arrayLength));
                                                break;
                                            case CHAR_TYPE:
                                                primitiveArrayDumpRecord = new CharArrayDump(arrayId, stackTraceSerialNumber, reader.readCharArray(arrayLength));
                                                break;
                                            case FLOAT_TYPE:
                                                primitiveArrayDumpRecord = new FloatArrayDump(arrayId, stackTraceSerialNumber, reader.readFloatArray(arrayLength));
                                                break;
                                            case DOUBLE_TYPE:
                                                primitiveArrayDumpRecord = new DoubleArrayDump(arrayId, stackTraceSerialNumber, reader.readDoubleArray(arrayLength));
                                                break;
                                            case BYTE_TYPE:
                                                primitiveArrayDumpRecord = new ByteArrayDump(arrayId, stackTraceSerialNumber, reader.readByteArray(arrayLength));
                                                break;
                                            case SHORT_TYPE:
                                                primitiveArrayDumpRecord = new ShortArrayDump(arrayId, stackTraceSerialNumber, reader.readShortArray(arrayLength));
                                                break;
                                            case INT_TYPE:
                                                primitiveArrayDumpRecord = new IntArrayDump(arrayId, stackTraceSerialNumber, reader.readIntArray(arrayLength));
                                                break;
                                            case LONG_TYPE:
                                                primitiveArrayDumpRecord = new LongArrayDump(arrayId, stackTraceSerialNumber, reader.readLongArray(arrayLength));
                                                break;
                                            default:
                                                throw new IllegalStateException("Unexpected type " + type);
                                        }
                                        primitiveArrayDumpCallback.call(primitiveArrayDumpRecord);
                                    } else {
                                        reader.skip(reader.idSize + Integer.SIZE);
                                        int arrayLength = reader.readInt();
                                        int type = reader.readUnsignedByte();
                                        reader.skip(arrayLength * reader.typeSize(type));
                                    }
                                    break;
                                case PRIMITIVE_ARRAY_NODATA:
                                    throw new UnsupportedOperationException("PRIMITIVE_ARRAY_NODATA cannot be parsed");
                                case HEAP_DUMP_INFO:
                                    heapId = reader.readInt();
                                    HeapDumpInfoRecordCallback heapDumpInfoCallback = callbacks.get(HeapDumpInfoRecord.class);
                                    if (heapDumpInfoCallback != null) {
                                        heapDumpInfoCallback.call(new HeapDumpInfoRecord(heapId, reader.readId()));
                                    } else {
                                        reader.skip(reader.idSize);
                                    }
                                    break;
                                case ROOT_INTERNED_STRING:
                                    GcRootRecordCallback gcRootInternedStringCallback = callbacks.get(GcRootRecord.class);
                                    if (gcRootInternedStringCallback != null) {
                                        gcRootInternedStringCallback.call(new GcRootRecord(new InternedString(reader.readId())));
                                    } else {
                                        reader.skip(reader.idSize);
                                    }
                                    break;
                                case ROOT_FINALIZING:
                                    GcRootRecordCallback gcRootFinalizingCallback = callbacks.get(GcRootRecord.class);
                                    if (gcRootFinalizingCallback != null) {
                                        gcRootFinalizingCallback.call(new GcRootRecord(new Finalizing(reader.readId())));
                                    } else {
                                        reader.skip(reader.idSize);
                                    }
                                    break;
                                case ROOT_DEBUGGER:
                                    GcRootRecordCallback gcRootDebuggerCallback = callbacks.get(GcRootRecord.class);
                                    if (gcRootDebuggerCallback != null) {
                                        gcRootDebuggerCallback.call(new GcRootRecord(new Debugger(reader.readId())));
                                    } else {
                                        reader.skip(reader.idSize);
                                    }
                                    break;
                                case ROOT_REFERENCE_CLEANUP:
                                    GcRootRecordCallback gcRootReferenceCleanupCallback = callbacks.get(GcRootRecord.class);
                                    if (gcRootReferenceCleanupCallback != null) {
                                        gcRootReferenceCleanupCallback.call(new GcRootRecord(new ReferenceCleanup(reader.readId())));
                                    } else {
                                        reader.skip(reader.idSize);
                                    }
                                    break;
                                case ROOT_VM_INTERNAL:
                                    GcRootRecordCallback gcRootVmInternalCallback = callbacks.get(GcRootRecord.class);
                                    if (gcRootVmInternalCallback != null) {
                                        gcRootVmInternalCallback.call(new GcRootRecord(new VmInternal(reader.readId())));
                                    } else {
                                        reader.skip(reader.idSize);
                                    }
                                    break;
                                case ROOT_JNI_MONITOR:
                                    GcRootRecordCallback gcRootJniMonitorCallback = callbacks.get(GcRootRecord.class);
                                    if (gcRootJniMonitorCallback != null) {
                                        gcRootJniMonitorCallback.call(new GcRootRecord(new JniMonitor(reader.readId(), reader.readInt(), reader.readInt())));
                                    } else {
                                        reader.skip(reader.idSize + Integer.SIZE + Integer.SIZE);
                                    }
                                    break;
                                case ROOT_UNREACHABLE:
                                    GcRootRecordCallback gcRootUnreachableCallback = callbacks.get(GcRootRecord.class);
                                    if (gcRootUnreachableCallback != null) {
                                        gcRootUnreachableCallback.call(new GcRootRecord(new Unreachable(reader.readId())));
                                    } else {
                                        reader.skip(reader.idSize);
                                    }
                                    break;
                                default:
                                    throw new IllegalStateException("Unknown tag " + heapDumpTag + " after " + previousTag);
                            }
                            previousTag = heapDumpTag;
                        }
                        heapId = 0;
                        break;
                    default:
                        reader.skip(length);
                }
            }
        
            reader.scanning = false;
            reader.indexBuilt = true;
        }
    }

    public String hprofStringById(long id) throws IOException {
        Pair<Long, Long> positionAndLength = hprofStringPositions.get(id);
        if (positionAndLength != null) {
            reader.moveTo(positionAndLength.first);
            return reader.readUtf8(positionAndLength.second);
        }
        return null;
    }

    public ClassDumpRecord classDumpRecordById(long id) throws IOException {
        Long position = classPositions.get(id);
        if (position != null) {
            reader.moveTo(position);
            return reader.readClassDumpRecord(id);
        }
        return null;
    }

    public InstanceDumpRecord instanceDumpRecordById(long id) throws IOException {
        Long position = instancePositions.get(id);
        if (position != null) {
            reader.moveTo(position);
            return reader.readInstanceDumpRecord(id);
        }
        return null;
    }

    public class HydradedInstance {
        public final InstanceDumpRecord record;
        public final List<HydradedClass> classHierarchy;
        public final List<List<HeapValue>> fieldValues;

        public HydradedInstance(InstanceDumpRecord record, List<HydradedClass> classHierarchy,
                List<List<HeapValue>> fieldValues) {
            this.record = record;
            this.classHierarchy = classHierarchy;
            this.fieldValues = fieldValues;
        }
    }

    public class HydradedClass {
        public final ClassDumpRecord record;
        public final String className;
        public final List<String> staticFieldNames;
        public final List<String> fieldNames;

        public HydradedClass(ClassDumpRecord record, String className, List<String> staticFieldNames,
                List<String> fieldNames) {
            this.record = record;
            this.className = className;
            this.staticFieldNames = staticFieldNames;
            this.fieldNames = fieldNames;
        }
    }

    public HydradedInstance hydratedInstanceById(long id) {
        return hydrate(instanceDumpRecordById(id));
    }

    public HydradedInstance hydrate(InstanceDumpRecord instanceRecord) {
        long classId = instanceRecord.getClassId();

        List<HydradedClass> classHierarchy = new ArrayList<>();
        do {
            ClassDumpRecord classRecord = classDumpRecordById(classId);
            String className = hprofStringById(classNames.get(classRecord.getId()));

            List<String> staticFieldNames = classRecord.getStaticFields().stream()
                    .map(field -> hprofStringById(field.getNameStringId()))
                    .collect(Collectors.toList());

            List<String> fieldNames = classRecord.getFields().stream()
                    .map(field -> hprofStringById(field.getNameStringId()))
                    .collect(Collectors.toList());

            classHierarchy.add(new HydradedClass(classRecord, className, staticFieldNames, fieldNames));
            classId = classRecord.getSuperClassId();
        } while (classId != 0L);

        ByteString valuesByteString = instanceRecord.getFieldValues().toByteString(0,
                instanceRecord.getFieldValues().size());

        Buffer buffer = new Buffer();
        buffer.write(valuesByteString);
        HprofReader valuesReader = new HprofReader(buffer, 0, reader.getIdSize());

        List<List<Object>> allFieldValues = classHierarchy.stream()
                .map(hydratedClass -> hydratedClass.getRecord().getFields().stream()
                        .map(field -> valuesReader.readValue(field.getType()))
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());

        return new HydradedInstance(instanceRecord, classHierarchy, allFieldValues);
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

    public static HprofParser open(File heapDump) throws IOException {
        FileInputStream inputStream = new FileInputStream(heapDump);
        FileChannel channel = inputStream.getChannel();
        Source source = Okio.source(inputStream).buffer();
        int endOfVersionString = source.indexOf((byte) 0);
        source.skip(endOfVersionString + 1);
        int idSize = source.readInt();
        int startPosition = endOfVersionString + 1 + 4;
        SeekableHprofReader hprofReader = new SeekableHprofReader(channel, source, startPosition, idSize);
        return new HprofParser(hprofReader);
    }

    public static class RecordCallbacks {
        private Map<Class<? extends Record>, Object> callbacks = new HashMap<>();

        public <T extends Record> RecordCallbacks on(Class<T> recordClass, Callback<T> callback) {
            callbacks.put(recordClass, callback);
            return this;
        }

        public <T extends Record> Callback<T> get(Class<T> recordClass) {
            return (Callback<T>) callbacks.get(recordClass);
        }

        public <T extends Record> Callback<T> get() {
            return get(Record.class);
        }
    }

}