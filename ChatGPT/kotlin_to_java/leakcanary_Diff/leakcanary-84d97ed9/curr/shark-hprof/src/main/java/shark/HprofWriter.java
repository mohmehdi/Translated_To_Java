
package shark;

import okio.Buffer;
import okio.BufferedSink;
import okio.ByteString;
import okio.Okio;
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
import shark.Hprof.HprofVersion;
import shark.HprofRecord.HeapDumpEndRecord;
import shark.HprofRecord.HeapDumpRecord.GcRootRecord;
import shark.HprofRecord.HeapDumpRecord.HeapDumpInfoRecord;
import shark.HprofRecord.HeapDumpRecord.ObjectRecord.ClassDumpRecord;
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
import shark.HprofRecord.StackTraceRecord;
import shark.HprofRecord.StringRecord;
import shark.HprofWriter.Companion;
import shark.PrimitiveType;
import shark.ValueHolder.BooleanHolder;
import shark.ValueHolder.ByteHolder;
import shark.ValueHolder.CharHolder;
import shark.ValueHolder.DoubleHolder;
import shark.ValueHolder.FloatHolder;
import shark.ValueHolder.IntHolder;
import shark.ValueHolder.LongHolder;
import shark.ValueHolder.ReferenceHolder;
import shark.ValueHolder.ShortHolder;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

public class HprofWriter implements Closeable {

    private final BufferedSink sink;
    private final int identifierByteSize;
    private final HprofVersion hprofVersion;
    private final Buffer workBuffer = new Buffer();

    private HprofWriter(BufferedSink sink, int identifierByteSize, HprofVersion hprofVersion) {
        this.sink = sink;
        this.identifierByteSize = identifierByteSize;
        this.hprofVersion = hprofVersion;
    }

    public void write(HprofRecord record) throws IOException {
        sink.write(record);
    }

    public byte[] valuesToBytes(List<ValueHolder> values) throws IOException {
        Buffer valuesBuffer = new Buffer();
        for (ValueHolder value : values) {
            valuesBuffer.writeValue(value);
        }
        return valuesBuffer.readByteArray();
    }

    @Override
    public void close() throws IOException {
        sink.flushHeapBuffer();
        sink.close();
    }
private void write(HprofRecord record) throws IOException {
    if (record instanceof StringRecord) {
        writeNonHeapRecord(HprofReader.STRING_IN_UTF8, () -> {
            writeId(((StringRecord) record).getId());
            writeUtf8(((StringRecord) record).getString());
        });
    } else if (record instanceof LoadClassRecord) {
        writeNonHeapRecord(HprofReader.LOAD_CLASS, () -> {
            writeInt(((LoadClassRecord) record).getClassSerialNumber());
            writeId(((LoadClassRecord) record).getId());
            writeInt(((LoadClassRecord) record).getStackTraceSerialNumber());
            writeId(((LoadClassRecord) record).getClassNameStringId());
        });
    } else if (record instanceof StackTraceRecord) {
        writeNonHeapRecord(HprofReader.STACK_TRACE, () -> {
            writeInt(((StackTraceRecord) record).getStackTraceSerialNumber());
            writeInt(((StackTraceRecord) record).getThreadSerialNumber());
            writeInt(((StackTraceRecord) record).getStackFrameIds().size());
            writeIdArray(((StackTraceRecord) record).getStackFrameIds());
        });
    } else if (record instanceof GcRootRecord) {
        GcRootRecord gcRootRecord = (GcRootRecord) record;
        switch (gcRootRecord.getGcRoot().getType()) {
            case Unknown:
                writeByte(HprofReader.ROOT_UNKNOWN);
                writeId(gcRootRecord.getGcRoot().getId());
                break;
            case JniGlobal:
                writeByte(HprofReader.ROOT_JNI_GLOBAL);
                writeId(gcRootRecord.getGcRoot().getId());
                writeId(((JniGlobal) gcRootRecord.getGcRoot()).getJniGlobalRefId());
                break;
            case JniLocal:
                writeByte(HprofReader.ROOT_JNI_LOCAL);
                writeId(gcRootRecord.getGcRoot().getId());
                writeInt(((JniLocal) gcRootRecord.getGcRoot()).getThreadSerialNumber());
                writeInt(((JniLocal) gcRootRecord.getGcRoot()).getFrameNumber());
                break;
            // Add cases for other types of GcRootRecord here
        }
    } else if (record instanceof ClassDumpRecord) {
        ClassDumpRecord classDumpRecord = (ClassDumpRecord) record;
        writeByte(HprofReader.CLASS_DUMP);
        writeId(classDumpRecord.getId());
        writeInt(classDumpRecord.getStackTraceSerialNumber());
        writeId(classDumpRecord.getSuperclassId());
        writeId(classDumpRecord.getClassLoaderId());
        writeId(classDumpRecord.getSignersId());
        writeId(classDumpRecord.getProtectionDomainId());
        writeId(0); // reserved
        writeId(0); // reserved
        writeInt(classDumpRecord.getInstanceSize());
        writeShort(0); // Not writing anything in the constant pool
        writeShort(classDumpRecord.getStaticFields().size());
        for (StaticFieldRecord field : classDumpRecord.getStaticFields()) {
            writeId(field.getNameStringId());
            writeByte(field.getType());
            writeValue(field.getValue());
        }
        writeShort(classDumpRecord.getFields().size());
        for (FieldRecord field : classDumpRecord.getFields()) {
            writeId(field.getNameStringId());
            writeByte(field.getType());
        }
    } else if (record instanceof InstanceDumpRecord) {
        InstanceDumpRecord instanceDumpRecord = (InstanceDumpRecord) record;
        writeByte(HprofReader.INSTANCE_DUMP);
        writeId(instanceDumpRecord.getId());
        writeInt(instanceDumpRecord.getStackTraceSerialNumber());
        writeId(instanceDumpRecord.getClassId());
        writeInt(instanceDumpRecord.getFieldValues().size());
        write(instanceDumpRecord.getFieldValues());
    } else if (record instanceof ObjectArrayDumpRecord) {
        ObjectArrayDumpRecord objectArrayDumpRecord = (ObjectArrayDumpRecord) record;
        writeByte(HprofReader.OBJECT_ARRAY_DUMP);
        writeId(objectArrayDumpRecord.getId());
        writeInt(objectArrayDumpRecord.getStackTraceSerialNumber());
        writeInt(objectArrayDumpRecord.getElementIds().size());
        writeId(objectArrayDumpRecord.getArrayClassId());
        writeIdArray(objectArrayDumpRecord.getElementIds());
    } else if (record instanceof PrimitiveArrayDumpRecord) {
        PrimitiveArrayDumpRecord primitiveArrayDumpRecord = (PrimitiveArrayDumpRecord) record;
        writeByte(HprofReader.PRIMITIVE_ARRAY_DUMP);
        writeId(primitiveArrayDumpRecord.getId());
        writeInt(primitiveArrayDumpRecord.getStackTraceSerialNumber());

        switch (primitiveArrayDumpRecord.getType()) {
            case BOOLEAN_TYPE:
                writeInt(primitiveArrayDumpRecord.getArray().length);
                writeByte(BOOLEAN.hprofType);
                write(primitiveArrayDumpRecord.getArray());
                break;
            case CHAR_TYPE:
                writeInt(primitiveArrayDumpRecord.getArray().length);
                writeByte(CHAR.hprofType);
                write(primitiveArrayDumpRecord.getArray());
                break;
            // Add cases for other primitive array types here
        }
    } else if (record instanceof HeapDumpInfoRecord) {
        writeByte(HprofReader.HEAP_DUMP_INFO);
        writeInt(((HeapDumpInfoRecord) record).getHeapId());
        writeId(((HeapDumpInfoRecord) record).getHeapNameStringId());
    } else if (record instanceof HeapDumpEndRecord) {
        throw new IllegalArgumentException("HprofWriter automatically emits HeapDumpEndRecord");
    }
}
    private void writeValue(ValueHolder wrapper) throws IOException {
        if (wrapper instanceof ReferenceHolder) {
            writeId(((ReferenceHolder) wrapper).value);
        } else if (wrapper instanceof BooleanHolder) {
            writeBoolean(((BooleanHolder) wrapper).value);
        } else if (wrapper instanceof CharHolder) {
            write(((CharHolder) wrapper).value.toString().toCharArray());
        } else if (wrapper instanceof FloatHolder) {
            writeFloat(((FloatHolder) wrapper).value);
        } else if (wrapper instanceof DoubleHolder) {
            writeDouble(((DoubleHolder) wrapper).value);
        } else if (wrapper instanceof ByteHolder) {
            writeByte((byte) ((ByteHolder) wrapper).value);
        } else if (wrapper instanceof ShortHolder) {
            writeShort((short) ((ShortHolder) wrapper).value);
        } else if (wrapper instanceof IntHolder) {
            writeInt(((IntHolder) wrapper).value);
        } else if (wrapper instanceof LongHolder) {
            writeLong(((LongHolder) wrapper).value);
        }
    }

    private void write(HprofRecord record) throws IOException {
        if (record instanceof StringRecord) {
            writeNonHeapRecord(HprofReader.STRING_IN_UTF8, () -> {
                writeId(((StringRecord) record).id);
                writeUtf8(((StringRecord) record).string);
            });
        } else if (record instanceof LoadClassRecord) {
            writeNonHeapRecord(HprofReader.LOAD_CLASS, () -> {
                writeInt(((LoadClassRecord) record).classSerialNumber);
                writeId(((LoadClassRecord) record).id);
                writeInt(((LoadClassRecord) record).stackTraceSerialNumber);
                writeId(((LoadClassRecord) record).classNameStringId);
            });
        } else if (record instanceof StackTraceRecord) {
            writeNonHeapRecord(HprofReader.STACK_TRACE, () -> {
                writeInt(((StackTraceRecord) record).stackTraceSerialNumber);
                writeInt(((StackTraceRecord) record).threadSerialNumber);
                writeInt(((StackTraceRecord) record).stackFrameIds.size());
                writeIdArray(((StackTraceRecord) record).stackFrameIds);
            });
        } else if (record instanceof GcRootRecord) {
            GcRootRecord gcRootRecord = (GcRootRecord) record;
            switch (gcRootRecord.gcRoot) {
                case UNKNOWN:
                    workBuffer.writeByte(HprofReader.ROOT_UNKNOWN);
                    writeId(((Unknown) gcRootRecord.gcRoot).id);
                    break;
                case JNI_GLOBAL:
                    workBuffer.writeByte(HprofReader.ROOT_JNI_GLOBAL);
                    writeId(((JniGlobal) gcRootRecord.gcRoot).id);
                    writeId(((JniGlobal) gcRootRecord.gcRoot).jniGlobalRefId);
                    break;
                // Add cases for other GcRoot types
            }
        }
        // Add cases for other record types
    }

    private void writeDouble(double value) throws IOException {
        writeLong(Double.doubleToLongBits(value));
    }

    private void writeFloat(float value) throws IOException {
        writeInt(Float.floatToIntBits(value));
    }

    private void writeBoolean(boolean value) throws IOException {
        writeByte((byte) (value ? 1 : 0));
    }

    private void writeIdArray(long[] array) throws IOException {
        for (long id : array) {
            writeId(id);
        }
    }

    private void write(boolean[] array) throws IOException {
        for (boolean value : array) {
            writeByte((byte) (value ? 1 : 0));
        }
    }

    private void write(char[] array) throws IOException {
        writeString(new String(array), ByteString.encodeUtf8().charset());
    }

    private void write(float[] array) throws IOException {
        for (float value : array) {
            writeFloat(value);
        }
    }

    private void write(double[] array) throws IOException {
        for (double value : array) {
            writeDouble(value);
        }
    }

    private void write(short[] array) throws IOException {
        for (short value : array) {
            writeShort(value);
        }
    }

    private void write(int[] array) throws IOException {
        for (int value : array) {
            writeInt(value);
        }
    }

    private void write(long[] array) throws IOException {
        for (long value : array) {
            writeLong(value);
        }
    }

    private void writeNonHeapRecord(int tag, Runnable block) throws IOException {
        flushHeapBuffer();
        block.run();
        writeTagHeader(tag, workBuffer.size());
        sink.writeAll(workBuffer);
    }

    private void flushHeapBuffer() throws IOException {
        if (workBuffer.size() > 0) {
            writeTagHeader(HprofReader.HEAP_DUMP, workBuffer.size());
            sink.writeAll(workBuffer);
            writeTagHeader(HprofReader.HEAP_DUMP_END, 0);
        }
    }

    private void writeTagHeader(int tag, long length) throws IOException {
        sink.writeByte(tag);
        sink.writeInt(0);
        sink.writeInt((int) length);
    }

    private void writeId(long id) throws IOException {
        switch (identifierByteSize) {
            case 1:
                sink.writeByte((byte) id);
                break;
            case 2:
                sink.writeShort((short) id);
                break;
            case 4:
                sink.writeInt((int) id);
                break;
            case 8:
                sink.writeLong(id);
                break;
            default:
                throw new IllegalArgumentException("ID Length must be 1, 2, 4, or 8");
        }
    }

    public static HprofWriter open(File hprofFile, int identifierByteSize, HprofVersion hprofVersion) throws IOException {
        BufferedSink sink = Okio.buffer(Okio.sink(hprofFile));
        sink.writeUtf8(hprofVersion.versionString);
        sink.writeByte(0);
        sink.writeInt(identifierByteSize);
        long heapDumpTimestamp = System.currentTimeMillis();
        sink.writeLong(heapDumpTimestamp);
        return new HprofWriter(sink, identifierByteSize, hprofVersion);
    }
}