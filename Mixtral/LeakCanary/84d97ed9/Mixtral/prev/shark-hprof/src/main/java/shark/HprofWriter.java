

package shark;

import okio.Buffer;
import okio.BufferedSink;
import okio.ByteString;
import okio.Sink;
import okio.Source;
import okio.buffer;
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
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

public class HprofWriter implements Closeable {
    private BufferedSink sink;
    private Buffer workBuffer;
    private final int idSize;

    private HprofWriter(BufferedSink sink, int idSize) {
        this.sink = sink;
        this.workBuffer = new Buffer();
        this.idSize = idSize;
    }

    public void write(HprofRecord record) throws IOException {
        sink.write(record);
    }

    private void write(BufferedSink sink, HprofRecord record) throws IOException {
        if (record instanceof StringRecord) {
            writeNonHeapRecord(HprofReader.STRING_IN_UTF8, sink -> {
                writeId(sink, ((StringRecord) record).id);
                writeUtf8(sink, ((StringRecord) record).string);
            });
        } else if (record instanceof LoadClassRecord) {
            writeNonHeapRecord(HprofReader.LOAD_CLASS, sink -> {
                writeInt(sink, ((LoadClassRecord) record).classSerialNumber);
                writeId(sink, ((LoadClassRecord) record).id);
                writeInt(sink, ((LoadClassRecord) record).stackTraceSerialNumber);
                writeId(sink, ((LoadClassRecord) record).classNameStringId);
            });
        } else if (record instanceof StackTraceRecord) {
            writeNonHeapRecord(HprofReader.STACK_TRACE, sink -> {
                writeInt(sink, ((StackTraceRecord) record).stackTraceSerialNumber);
                writeInt(sink, ((StackTraceRecord) record).threadSerialNumber);
                writeInt(sink, ((StackTraceRecord) record).stackFrameIds.size);
                writeIdArray(sink, ((StackTraceRecord) record).stackFrameIds);
            });
        } else if (record instanceof GcRootRecord) {
            GcRoot gcRoot = ((GcRootRecord) record).gcRoot;
            switch (gcRoot) {
                case Unknown:
                    writeByte(HprofReader.ROOT_UNKNOWN);
                    writeId(sink, gcRoot.id);
                    break;
                case JniGlobal:
                    writeByte(HprofReader.ROOT_JNI_GLOBAL);
                    writeId(sink, gcRoot.id);
                    writeId(sink, gcRoot.jniGlobalRefId);
                    break;
                case JniLocal:
                    writeByte(HprofReader.ROOT_JNI_LOCAL);
                    writeId(sink, gcRoot.id);
                    writeInt(sink, gcRoot.threadSerialNumber);
                    writeInt(sink, gcRoot.frameNumber);
                    break;
                case JavaFrame:
                    writeByte(HprofReader.ROOT_JAVA_FRAME);
                    writeId(sink, gcRoot.id);
                    writeInt(sink, gcRoot.threadSerialNumber);
                    writeInt(sink, gcRoot.frameNumber);
                    break;
                case NativeStack:
                    writeByte(HprofReader.ROOT_NATIVE_STACK);
                    writeId(sink, gcRoot.id);
                    writeInt(sink, gcRoot.threadSerialNumber);
                    break;
                case StickyClass:
                    writeByte(HprofReader.ROOT_STICKY_CLASS);
                    writeId(sink, gcRoot.id);
                    break;
                case ThreadBlock:
                    writeByte(HprofReader.ROOT_THREAD_BLOCK);
                    writeId(sink, gcRoot.id);
                    writeInt(sink, gcRoot.threadSerialNumber);
                    break;
                case MonitorUsed:
                    writeByte(HprofReader.ROOT_MONITOR_USED);
                    writeId(sink, gcRoot.id);
                    break;
                case ThreadObject:
                    writeByte(HprofReader.ROOT_THREAD_OBJECT);
                    writeId(sink, gcRoot.id);
                    writeInt(sink, gcRoot.threadSerialNumber);
                    writeInt(sink, gcRoot.stackTraceSerialNumber);
                    break;
                case ReferenceCleanup:
                    writeByte(HprofReader.ROOT_REFERENCE_CLEANUP);
                    writeId(sink, gcRoot.id);
                    break;
                case VmInternal:
                    writeByte(HprofReader.ROOT_VM_INTERNAL);
                    writeId(sink, gcRoot.id);
                    break;
                case JniMonitor:
                    writeByte(HprofReader.ROOT_JNI_MONITOR);
                    writeId(sink, gcRoot.id);
                    writeInt(sink, gcRoot.stackTraceSerialNumber);
                    writeInt(sink, gcRoot.stackDepth);
                    break;
                case InternedString:
                    writeByte(HprofReader.ROOT_INTERNED_STRING);
                    writeId(sink, gcRoot.id);
                    break;
                case Finalizing:
                    writeByte(HprofReader.ROOT_FINALIZING);
                    writeId(sink, gcRoot.id);
                    break;
                case Debugger:
                    writeByte(HprofReader.ROOT_DEBUGGER);
                    writeId(sink, gcRoot.id);
                    break;
                case Unreachable:
                    writeByte(HprofReader.ROOT_UNREACHABLE);
                    writeId(sink, gcRoot.id);
                    break;
            }
        } else if (record instanceof ClassDumpRecord) {
            writeByte(HprofReader.CLASS_DUMP);
            writeId(sink, record.id);
            writeInt(sink, ((ClassDumpRecord) record).stackTraceSerialNumber);
            writeId(sink, ((ClassDumpRecord) record).superClassId);
            writeId(sink, ((ClassDumpRecord) record).classLoaderId);
            writeId(sink, ((ClassDumpRecord) record).signersId);
            writeId(sink, ((ClassDumpRecord) record).protectionDomainId);
            writeId(sink, 0);
            writeId(sink, 0);
            writeInt(sink, ((ClassDumpRecord) record).instanceSize);
            writeShort(sink, 0);
            writeShort(sink, ((ClassDumpRecord) record).staticFields.size);
            for (Field field : ((ClassDumpRecord) record).staticFields) {
                writeId(sink, field.nameStringId);
                writeByte(sink, field.type);
                writeValue(sink, field.value);
            }
            writeShort(sink, ((ClassDumpRecord) record).fields.size);
            for (Field field : ((ClassDumpRecord) record).fields) {
                writeId(sink, field.nameStringId);
                writeByte(sink, field.type);
            }
        } else if (record instanceof InstanceDumpRecord) {
            writeByte(HprofReader.INSTANCE_DUMP);
            writeId(sink, record.id);
            writeInt(sink, ((InstanceDumpRecord) record).stackTraceSerialNumber);
            writeId(sink, ((InstanceDumpRecord) record).classId);
            writeInt(sink, ((InstanceDumpRecord) record).fieldValues.size);
            writeFieldValues(sink, ((InstanceDumpRecord) record).fieldValues);
        } else if (record instanceof ObjectArrayDumpRecord) {
            writeByte(HprofReader.OBJECT_ARRAY_DUMP);
            writeId(sink, record.id);
            writeInt(sink, ((ObjectArrayDumpRecord) record).stackTraceSerialNumber);
            writeInt(sink, ((ObjectArrayDumpRecord) record).elementIds.size);
            writeId(sink, ((ObjectArrayDumpRecord) record).arrayClassId);
            writeIdArray(sink, ((ObjectArrayDumpRecord) record).elementIds);
        } else if (record instanceof PrimitiveArrayDumpRecord) {
            PrimitiveArrayDumpRecord primitiveArrayDumpRecord = (PrimitiveArrayDumpRecord) record;
            writeByte(HprofReader.PRIMITIVE_ARRAY_DUMP);
            writeId(sink, record.id);
            writeInt(sink, primitiveArrayDumpRecord.stackTraceSerialNumber);
            if (primitiveArrayDumpRecord instanceof BooleanArrayDump) {
                BooleanArrayDump booleanArrayDump = (BooleanArrayDump) primitiveArrayDumpRecord;
                writeInt(sink, booleanArrayDump.array.length);
                writeByte(sink, BOOLEAN.hprofType);
                writeBooleanArray(sink, booleanArrayDump.array);
            } else if (primitiveArrayDumpRecord instanceof ByteArrayDump) {
                ByteArrayDump byteArrayDump = (ByteArrayDump) primitiveArrayDumpRecord;
                writeInt(sink, byteArrayDump.array.length);
                writeByte(sink, BYTE.hprofType);
                writeByteArray(sink, byteArrayDump.array);
            } else if (primitiveArrayDumpRecord instanceof CharArrayDump) {
                CharArrayDump charArrayDump = (CharArrayDump) primitiveArrayDumpRecord;
                writeInt(sink, charArrayDump.array.length);
                writeByte(sink, CHAR.hprofType);
                writeCharArray(sink, charArrayDump.array);
            } else if (primitiveArrayDumpRecord instanceof DoubleArrayDump) {
                DoubleArrayDump doubleArrayDump = (DoubleArrayDump) primitiveArrayDumpRecord;
                writeInt(sink, doubleArrayDump.array.length);
                writeByte(sink, DOUBLE.hprofType);
                writeDoubleArray(sink, doubleArrayDump.array);
            } else if (primitiveArrayDumpRecord instanceof FloatArrayDump) {
                FloatArrayDump floatArrayDump = (FloatArrayDump) primitiveArrayDumpRecord;
                writeInt(sink, floatArrayDump.array.length);
                writeByte(sink, FLOAT.hprofType);
                writeFloatArray(sink, floatArrayDump.array);
            } else if (primitiveArrayDumpRecord instanceof IntArrayDump) {
                IntArrayDump intArrayDump = (IntArrayDump) primitiveArrayDumpRecord;
                writeInt(sink, intArrayDump.array.length);
                writeByte(sink, INT.hprofType);
                writeIntArray(sink, intArrayDump.array);
            } else if (primitiveArrayDumpRecord instanceof LongArrayDump) {
                LongArrayDump longArrayDump = (LongArrayDump) primitiveArrayDumpRecord;
                writeInt(sink, longArrayDump.array.length);
                writeByte(sink, LONG.hprofType);
                writeLongArray(sink, longArrayDump.array);
            } else if (primitiveArrayDumpRecord instanceof ShortArrayDump) {
                ShortArrayDump shortArrayDump = (ShortArrayDump) primitiveArrayDumpRecord;
                writeInt(sink, shortArrayDump.array.length);
                writeByte(sink, SHORT.hprofType);
                writeShortArray(sink, shortArrayDump.array);
            }
        } else if (record instanceof HeapDumpInfoRecord) {
            writeByte(HprofReader.HEAP_DUMP_INFO);
            writeInt(sink, ((HeapDumpInfoRecord) record).heapId);
            writeId(sink, ((HeapDumpInfoRecord) record).heapNameStringId);
        } else if (record instanceof HeapDumpEndRecord) {
            throw new IllegalArgumentException("HprofWriter automatically emits HeapDumpEndRecord");
        }
    }

    private void writeValue(BufferedSink sink, ValueHolder wrapper) throws IOException {
        if (wrapper instanceof ReferenceHolder) {
            writeId(sink, ((ReferenceHolder) wrapper).value);
        } else if (wrapper instanceof BooleanHolder) {
            writeBoolean(sink, ((BooleanHolder) wrapper).value);
        } else if (wrapper instanceof CharHolder) {
            writeCharArray(sink, new char[]{((CharHolder) wrapper).value});
        } else if (wrapper instanceof FloatHolder) {
            writeFloat(sink, ((FloatHolder) wrapper).value);
        } else if (wrapper instanceof DoubleHolder) {
            writeDouble(sink, ((DoubleHolder) wrapper).value);
        } else if (wrapper instanceof ByteHolder) {
            writeByte(sink, ((ByteHolder) wrapper).value);
        } else if (wrapper instanceof ShortHolder) {
            writeShort(sink, ((ShortHolder) wrapper).value);
        } else if (wrapper instanceof IntHolder) {
            writeInt(sink, ((IntHolder) wrapper).value);
        } else if (wrapper instanceof LongHolder) {
            writeLong(sink, ((LongHolder) wrapper).value);
        }
    }
    private void writeDouble(BufferedSink sink, double value) {
        sink.writeLong(Double.doubleToRawLongBits(value));
    }

    private void writeFloat(BufferedSink sink, float value) {
        sink.writeInt(Float.floatToRawIntBits(value));
    }

    private void writeBoolean(BufferedSink sink, boolean value) {
        sink.writeByte((byte) (value ? 1 : 0));
    }

    private void writeIdArray(BufferedSink sink, long[] array) {
        for (long id : array) {
            writeId(sink, id);
        }
    }

        private void write(boolean[] array) throws IOException {
        for (boolean value : array) {
            sink.writeByte(value ? (byte) 1 : (byte) 0);
        }
    }

    private void write(char[] array) throws IOException {
        sink.writeString(new String(array), Charsets.UTF_16BE);
    }

    private void write(float[] array) throws IOException {
        for (float value : array) {
            sink.writeFloat(value);
        }
    }

    private void write(double[] array) throws IOException {
        for (double value : array) {
            sink.writeDouble(value);
        }
    }

    private void write(short[] array) throws IOException {
        for (short value : array) {
            sink.writeShort(value);
        }
    }

    private void write(int[] array) throws IOException {
        for (int value : array) {
            sink.writeInt(value);
        }
    }

    private void write(long[] array) throws IOException {
        for (long value : array) {
            sink.writeLong(value);
        }
    }


    private void writeNonHeapRecord(int tag, BufferedSink.() -> Unit block) throws IOException {
        flushHeapBuffer();
        workBuffer.reset();
        block.invoke(workBuffer);
        writeTagHeader(tag, workBuffer.size());
        sink.writeAll(workBuffer);
    }

    private void flushHeapBuffer() throws IOException {
        if (workBuffer.size() > 0) {
            workTagHeader(HprofReader.HEAP_DUMP, workBuffer.size());
            sink.writeAll(workBuffer);
            workTagHeader(HprofReader.HEAP_DUMP_END, 0);
        }
    }

    private void writeTagHeader(BufferedSink sink, int tag, long length) {
        sink.writeByte((byte) tag);

        sink.writeInt(0);
        sink.writeInt((int) length);
    }

    private void writeId(BufferedSink sink, long id) throws IOException {
        switch (idSize) {
            case 1:
                writeByte(sink, (int) id);
                break;
            case 2:
                writeShort(sink, (int) id);
                break;
            case 4:
                writeInt(sink, (int) id);
                break;
            case 8:
                writeLong(sink, id);
                break;
            default:
                throw new IllegalArgumentException("ID Length must be 1, 2, 4, or 8");
        }
    }

    @Override
    public void close() throws IOException {
        flushHeapBuffer();
        sink.close();
    }

    public static HprofWriter open(File hprofFile, int idSize) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(hprofFile);
        Sink sink = Okio.sink(fileOutputStream);
        BufferedSink bufferedSink = sink.buffer();

        String hprofVersion = "JAVA PROFILE 1.0.3";
        bufferedSink.writeUtf8(hprofVersion);
        bufferedSink.writeByte(0);
        bufferedSink.writeInt(idSize);

        long heapDumpTimestamp = System.currentTimeMillis();
        bufferedSink.writeLong(heapDumpTimestamp);

        return new HprofWriter(bufferedSink, idSize);
    }
}