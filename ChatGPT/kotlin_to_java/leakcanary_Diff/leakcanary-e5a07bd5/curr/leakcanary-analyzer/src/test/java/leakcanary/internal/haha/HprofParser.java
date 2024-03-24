package leakcanary.internal.haha

import leakcanary.internal.haha.GcRoot;
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
import leakcanary.internal.haha.HeapValue.IntValue;
import leakcanary.internal.haha.HeapValue.ObjectReference;
import leakcanary.internal.haha.HprofReader;
import leakcanary.internal.haha.Record;
import leakcanary.internal.haha.Record.HeapDumpRecord.ClassDumpRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.GcRootRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.HeapDumpInfoRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.InstanceDumpRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectArrayDumpRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.PrimitiveArrayDumpRecord;
import leakcanary.internal.haha.Record.HeapDumpRecord.PrimitiveArrayDumpRecord.ByteArrayDump;
import leakcanary.internal.haha.Record.LoadClassRecord;
import leakcanary.internal.haha.Record.StringRecord;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class HprofParser implements Closeable {

    private final SeekableHprofReader reader;
    private boolean scanning = false;
    private boolean indexBuilt = false;
    private final Map<Long, Pair<Long, Long>> hprofStringPositions = new HashMap<>();
    private final Map<Long, Long> classNames = new HashMap<>();
    private final Map<Long, Long> objectPositions = new HashMap<>();

    private HprofParser(SeekableHprofReader reader) {
        this.reader = reader;
    }

    public static HprofParser open(File heapDump) throws IOException {
        BufferedSource source = Okio.buffer(Okio.source(heapDump));
        int endOfVersionString = source.indexOf((byte) 0);
        source.skip(endOfVersionString + 1);
        int idSize = source.readInt();
        int startPosition = endOfVersionString + 1 + 4;

        FileChannel channel = source.fileChannel();
        SeekableHprofReader hprofReader = new SeekableHprofReader(channel, source, startPosition, idSize);
        return new HprofParser(hprofReader);
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    public void scan(RecordCallbacks callbacks) throws IOException {
        reader.scan(callbacks);
    }    

    private void scan(SeekableHprofReader reader, RecordCallbacks callbacks) throws IOException {
        if (!reader.isOpen()) {
            throw new IllegalStateException("Reader closed");
        }

        if (scanning) {
            throw new UnsupportedOperationException("Cannot scan while already scanning.");
        }

        scanning = true;

        reader.reset();

        reader.skip(LONG_SIZE);

        int heapId = 0;
        while (!reader.exhausted()) {
            int tag = reader.readUnsignedByte();

            reader.skip(INT_SIZE);

            long length = reader.readUnsignedInt();

            switch (tag) {
                case STRING_IN_UTF8: {
                    StringRecordCallback callback = callbacks.get(StringRecordCallback.class);
                    if (callback != null || !indexBuilt) {
                        long id = reader.readId();
                        if (!indexBuilt) {
                            hprofStringPositions.put(id, reader.position() + length - idSize);
                        }
                        if (callback != null) {
                            String string = reader.readUtf8((int) (length - idSize));
                            callback.onStringRecord(new StringRecord(id, string));
                        } else {
                            reader.skip((int) (length - idSize));
                        }
                    } else {
                        reader.skip((int) length);
                    }
                    break;
                }
                case LOAD_CLASS: {
                    LoadClassRecordCallback callback = callbacks.get(LoadClassRecordCallback.class);
                    if (callback != null || !indexBuilt) {
                        int classSerialNumber = reader.readInt();
                        long id = reader.readId();
                        int stackTraceSerialNumber = reader.readInt();
                        long classNameStringId = reader.readId();
                        if (!indexBuilt) {
                            classNames.put(id, classNameStringId);
                        }
                        if (callback != null) {
                            callback.onLoadClassRecord(new LoadClassRecord(classSerialNumber, id, stackTraceSerialNumber, classNameStringId));
                        }
                    } else {
                        reader.skip((int) length);
                    }
                    break;
                }
                case HEAP_DUMP:
                case HEAP_DUMP_SEGMENT: {
                    long heapDumpStart = reader.position();
                    int previousTag = 0;
                    while (reader.position() - heapDumpStart < length) {
                        int heapDumpTag = reader.readUnsignedByte();

                        switch (heapDumpTag) {
                            case ROOT_UNKNOWN:
                                handleGcRootUnknown(callbacks, reader);
                                break;
                            case ROOT_JNI_GLOBAL:
                                handleGcRootJniGlobal(callbacks, reader);
                                break;
                            case ROOT_JNI_LOCAL:
                                handleGcRootJniLocal(callbacks, reader);
                                break;
                            case ROOT_JAVA_FRAME:
                                handleGcRootJavaFrame(callbacks, reader);
                                break;
                            case ROOT_NATIVE_STACK:
                                handleGcRootNativeStack(callbacks, reader);
                                break;
                            case ROOT_STICKY_CLASS:
                                handleGcRootStickyClass(callbacks, reader);
                                break;
                            case ROOT_THREAD_BLOCK:
                                handleGcRootThreadBlock(callbacks, reader);
                                break;
                            case ROOT_MONITOR_USED:
                                handleGcRootMonitorUsed(callbacks, reader);
                                break;
                            case ROOT_THREAD_OBJECT:
                                handleGcRootThreadObject(callbacks, reader);
                                break;
                            case CLASS_DUMP:
                                handleClassDump(callbacks, reader);
                                break;
                            case INSTANCE_DUMP:
                                handleInstanceDump(callbacks, reader);
                                break;
                            case OBJECT_ARRAY_DUMP:
                                handleObjectArrayDump(callbacks, reader);
                                break;
                            case PRIMITIVE_ARRAY_DUMP:
                                handlePrimitiveArrayDump(callbacks, reader);
                                break;
                            case PRIMITIVE_ARRAY_NODATA:
                                throw new UnsupportedOperationException("PRIMITIVE_ARRAY_NODATA cannot be parsed");
                            case HEAP_DUMP_INFO:
                                heapId = reader.readInt();
                                handleHeapDumpInfo(callbacks, reader, heapId);
                                break;
                            case ROOT_INTERNED_STRING:
                                handleGcRootInternedString(callbacks, reader);
                                break;
                            case ROOT_FINALIZING:
                                handleGcRootFinalizing(callbacks, reader);
                                break;
                            case ROOT_DEBUGGER:
                                handleGcRootDebugger(callbacks, reader);
                                break;
                            case ROOT_REFERENCE_CLEANUP:
                                handleGcRootReferenceCleanup(callbacks, reader);
                                break;
                            case ROOT_VM_INTERNAL:
                                handleGcRootVmInternal(callbacks, reader);
                                break;
                            case ROOT_JNI_MONITOR:
                                handleGcRootJniMonitor(callbacks, reader);
                                break;
                            case ROOT_UNREACHABLE:
                                handleGcRootUnreachable(callbacks, reader);
                                break;
                            default:
                                throw new IllegalStateException("Unknown tag " + heapDumpTag + " after " + previousTag);
                        }
                        previousTag = heapDumpTag;
                    }
                    heapId = 0;
                    break;
                }
                default:
                    reader.skip((int) length);
                    break;
            }
        }

        scanning = false;
        indexBuilt = true;
    }

    public class RecordCallbacks {
        private final Map<Class<? extends Record>, Object> callbacks = new HashMap<>();

        public <T extends Record> RecordCallbacks on(Class<T> recordClass, Callback<T> callback) {
            callbacks.put(recordClass, callback);
            return this;
        }

        public <T extends Record> Callback<T> get(Class<T> recordClass) {
            @SuppressWarnings("unchecked")
            Callback<T> callback = (Callback<T>) callbacks.get(recordClass);
            return callback;
        }

        public <T extends Record> Callback<T> get() {
            return get(T.class);
        }
    }


    public String hprofStringById(long id) throws IOException {
        Pair<Long, Long> pair = hprofStringPositions.get(id);
        if (pair == null) {
            throw new IllegalArgumentException("Unknown string id " + id);
        }
        reader.moveTo(pair.first);
        return reader.readUtf8(pair.second.intValue());
    }

    public String retrieveString(ObjectReference reference) throws IOException {
        InstanceDumpRecord instanceRecord = (InstanceDumpRecord) retrieveRecord(reference);
        HydratedInstance instance = hydrateInstance(instanceRecord);
        return instanceAsString(instance);
    }

    public ObjectRecord retrieveRecord(ObjectReference reference) throws IOException {
        return retrieveRecord(reference.getValue());
    }

    public ObjectRecord retrieveRecord(long objectId) throws IOException {
        Long position = objectPositions.get(objectId);
        if (position == null) {
            throw new IllegalArgumentException("Unknown object id " + objectId);
        }
        reader.moveTo(position);
        int heapDumpTag = reader.readUnsignedByte();
        reader.skip(reader.idSize());
        switch (heapDumpTag) {
            case HprofParser.CLASS_DUMP:
                return reader.readClassDumpRecord(objectId);
            case HprofParser.INSTANCE_DUMP:
                return reader.readInstanceDumpRecord(objectId);
            case HprofParser.OBJECT_ARRAY_DUMP:
                return reader.readObjectArrayDumpRecord(objectId);
            case HprofParser.PRIMITIVE_ARRAY_DUMP:
                return reader.readPrimitiveArrayDumpRecord(objectId);
            default:
                throw new IllegalStateException(
                        "Unexpected tag " + heapDumpTag + " for id " + objectId + " at position " + position);
        }
    }

    public class HydratedInstance {
        private final InstanceDumpRecord record;
        private final List<HydratedClass> classHierarchy;
        private final List<List<HeapValue>> fieldValues;

        public HydratedInstance(InstanceDumpRecord record, List<HydratedClass> classHierarchy,
                List<List<HeapValue>> fieldValues) {
            this.record = record;
            this.classHierarchy = classHierarchy;
            this.fieldValues = fieldValues;
        }

        public <T extends HeapValue> T fieldValue(String name) {
            return fieldValueOrNull(name)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Could not find field " + name + " in instance with id " + record.getId()));
        }

        public <T extends HeapValue> java.util.Optional<T> fieldValueOrNull(String name) {
            for (int classIndex = 0; classIndex < classHierarchy.size(); classIndex++) {
                HydratedClass hydratedClass = classHierarchy.get(classIndex);
                for (int fieldIndex = 0; fieldIndex < hydratedClass.getFieldNames().size(); fieldIndex++) {
                    String fieldName = hydratedClass.getFieldNames().get(fieldIndex);
                    if (fieldName.equals(name)) {
                        @SuppressWarnings("unchecked")
                        T value = (T) fieldValues.get(classIndex).get(fieldIndex);
                        return java.util.Optional.of(value);
                    }
                }
            }
            return java.util.Optional.empty();
        }
    }

    public class HydratedClass {
        public final ClassDumpRecord record;
        public final String className;
        public final List<String> staticFieldNames;
        public final List<String> fieldNames;

        public HydratedClass(ClassDumpRecord record, String className, List<String> staticFieldNames,
                List<String> fieldNames) {
            this.record = record;
            this.className = className;
            this.staticFieldNames = staticFieldNames;
            this.fieldNames = fieldNames;
        }
    }

    public HydratedInstance hydrateInstance(InstanceDumpRecord instanceRecord) throws IOException {
        long classId = instanceRecord.getClassId();
        List<HydratedClass> classHierarchy = new ArrayList<>();
        do {
            ClassDumpRecord classRecord = (ClassDumpRecord) retrieveRecord(classId);
            String className = hprofStringById(classNames.get(classRecord.getId()));
            List<String> staticFieldNames = classRecord.getStaticFields().stream().map(StaticField::getNameStringId)
                    .map(this::hprofStringById).collect(Collectors.toList());
            List<String> fieldNames = classRecord.getFields().stream().map(Field::getNameStringId)
                    .map(this::hprofStringById).collect(Collectors.toList());
            classHierarchy.add(new HydratedClass(classRecord, className, staticFieldNames, fieldNames));
            classId = classRecord.getSuperClassId();
        } while (classId != 0);
        Buffer buffer = new Buffer();
        buffer.write(instanceRecord.getFieldValues());
        HprofReader valuesReader = new HprofReader(buffer, 0, reader.idSize());
        List<List<HeapValue>> allFieldValues = classHierarchy.stream()
                .map(hydratedClass -> hydratedClass.getRecord().getFields().stream().map(field -> {
                    try {
                        return valuesReader.readValue(field.getType());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toList())).collect(Collectors.toList());
        return new HydratedInstance(instanceRecord, classHierarchy, allFieldValues);
    }

    public String instanceAsString(HydratedInstance instance) {
        int count = instance.<IntValue>fieldValue("count").getValue();
        if (count == 0) {
            return "";
        }
        ObjectReference value = instance.fieldValue("value");
        ObjectRecord valueRecord = retrieveRecord(value);
        switch (valueRecord.getTag()) {
            case HprofParser.CHAR_ARRAY_DUMP:
                int offset = 0;
                IntValue offsetValue = instance.fieldValueOrNull("offset");
                if (offsetValue != null) {
                    offset = offsetValue.getValue();
                }
                ByteArrayDump arrayDump = (ByteArrayDump) valueRecord;
                byte[] chars = Arrays.copyOfRange(arrayDump.getArray(), offset, offset + count);
                return new String(chars);
            case HprofParser.BYTE_ARRAY_DUMP:
                return new String(((ByteArrayDump) valueRecord).getArray(), Charset.forName("UTF-8"));
            default:
                throw new UnsupportedOperationException(
                        "'value' field was expected to be either a char or byte array in string instance with id "
                                + instance.getRecord().getId());
        }
    }

}
