
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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

public class HprofWriter implements Closeable {

    private final BufferedSink sink;
    private final int idSize;
    private final Buffer workBuffer = new Buffer();

    private HprofWriter(BufferedSink sink, int idSize) {
        this.sink = sink;
        this.idSize = idSize;
    }

    public void write(HprofRecord record) {
        sink.write(record);
    }

    private void write(BufferedSink sink, HprofRecord record) throws IOException {
        if (record instanceof StringRecord) {
            writeNonHeapRecord(HprofReader.STRING_IN_UTF8, () -> {
                sink.writeId(((StringRecord) record).id);
                sink.writeUtf8(((StringRecord) record).string);
            });
        } else if (record instanceof LoadClassRecord) {
            writeNonHeapRecord(HprofReader.LOAD_CLASS, () -> {
                sink.writeInt(((LoadClassRecord) record).classSerialNumber);
                sink.writeId(((LoadClassRecord) record).id);
                sink.writeInt(((LoadClassRecord) record).stackTraceSerialNumber);
                sink.writeId(((LoadClassRecord) record).classNameStringId);
            });
        } else if (record instanceof StackTraceRecord) {
            writeNonHeapRecord(HprofReader.STACK_TRACE, () -> {
                sink.writeInt(((StackTraceRecord) record).stackTraceSerialNumber);
                sink.writeInt(((StackTraceRecord) record).threadSerialNumber);
                sink.writeInt(((StackTraceRecord) record).stackFrameIds.length);
                sink.writeIdArray(((StackTraceRecord) record).stackFrameIds);
            });
        } else if (record instanceof GcRootRecord) {
            // Handle GcRootRecord
        } else if (record instanceof ClassDumpRecord) {
            // Handle ClassDumpRecord
        } else if (record instanceof InstanceDumpRecord) {
            // Handle InstanceDumpRecord
        } else if (record instanceof ObjectArrayDumpRecord) {
            // Handle ObjectArrayDumpRecord
        } else if (record instanceof PrimitiveArrayDumpRecord) {
            // Handle PrimitiveArrayDumpRecord
        } else if (record instanceof HeapDumpInfoRecord) {
            // Handle HeapDumpInfoRecord
        } else if (record instanceof HeapDumpEndRecord) {
            throw new IllegalArgumentException("HprofWriter automatically emits HeapDumpEndRecord");
        }
    }

    public void writeValue(ValueHolder wrapper) {
        sink.writeValue(wrapper);
    }

    private void writeDouble(double value) throws IOException {
        sink.writeLong(Double.doubleToLongBits(value));
    }

    private void writeFloat(float value) throws IOException {
        sink.writeInt(Float.floatToIntBits(value));
    }

    private void writeBoolean(boolean value) throws IOException {
        sink.writeByte((byte) (value ? 1 : 0));
    }

    private void writeIdArray(long[] array) throws IOException {
        for (long id : array) {
            sink.writeId(id);
        }
    }

    private void write(boolean[] array) throws IOException {
        for (boolean b : array) {
            sink.writeByte((byte) (b ? 1 : 0));
        }
    }

    private void write(char[] array) throws IOException {
        sink.writeUtf8(new String(array));
    }

    private void write(float[] array) throws IOException {
        for (float f : array) {
            sink.writeFloat(f);
        }
    }

    private void write(double[] array) throws IOException {
        for (double d : array) {
            sink.writeDouble(d);
        }
    }

    private void write(short[] array) throws IOException {
        for (short s : array) {
            sink.writeShort(s);
        }
    }

    private void write(int[] array) throws IOException {
        for (int i : array) {
            sink.writeInt(i);
        }
    }

    private void write(long[] array) throws IOException {
        for (long l : array) {
            sink.writeLong(l);
        }
    }

    private void writeNonHeapRecord(int tag, Runnable block) throws IOException {
        flushHeapBuffer();
        workBuffer.clear();
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
        sink.writeByte((byte) tag);
        sink.writeInt(0);
        sink.writeInt((int) length);
    }

    private void writeId(long id) throws IOException {
        switch (idSize) {
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

    @Override
    public void close() throws IOException {
        sink.flush();
        sink.close();
    }

    public static HprofWriter open(File hprofFile, int idSize) throws IOException {
        BufferedSink sink = Okio.buffer(Okio.sink(hprofFile));
        String hprofVersion = "JAVA PROFILE 1.0.3";
        sink.writeUtf8(hprofVersion);
        sink.writeByte(0);
        sink.writeInt(idSize);
        long heapDumpTimestamp = System.currentTimeMillis();
        sink.writeLong(heapDumpTimestamp);
        return new HprofWriter(sink, idSize);
    }
}