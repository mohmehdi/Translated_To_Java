package shark;

import okio.Buffer;
import okio.BufferedSink;
import okio.ByteString;
import okio.Sink;
import okio.Source;
import okio.buffer;
import okio.sink;
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

public class HprofWriter implements Closeable {
  private BufferedSink sink;
  private int identifierByteSize;
  private HprofVersion hprofVersion;
  private Buffer workBuffer;

  public HprofWriter(
      BufferedSink sink,
      int identifierByteSize,
      HprofVersion hprofVersion) {
    this.sink = sink;
    this.identifierByteSize = identifierByteSize;
    this.hprofVersion = hprofVersion;
    this.workBuffer = new Buffer();
  }

  public void write(HprofRecord record) throws IOException {
    sink.write(record);
  }

  public byte[] valuesToBytes(List<ValueHolder> values) {
    Buffer valuesBuffer = new Buffer();
    for (ValueHolder value : values) {
      writeValue(valuesBuffer, value);
    }
    return valuesBuffer.readByteArray();
  }

  @Override
  public void close() throws IOException {
    sink.flushHeapBuffer();
    sink.close();
  }

    public void writeValue(ValueHolder wrapper) throws IOException {
        if (wrapper instanceof ReferenceHolder) {
            writeId(((ReferenceHolder) wrapper).value);
        } else if (wrapper instanceof BooleanHolder) {
            writeBoolean(((BooleanHolder) wrapper).value);
        } else if (wrapper instanceof CharHolder) {
            write(new char[] {((CharHolder) wrapper).value});
        } else if (wrapper instanceof FloatHolder) {
            writeFloat(((FloatHolder) wrapper).value);
        } else if (wrapper instanceof DoubleHolder) {
            writeDouble(((DoubleHolder) wrapper).value);
        } else if (wrapper instanceof ByteHolder) {
            writeByte(((ByteHolder) wrapper).value.intValue());
        } else if (wrapper instanceof ShortHolder) {
            writeShort(((ShortHolder) wrapper).value.intValue());
        } else if (wrapper instanceof IntHolder) {
            writeInt(((IntHolder) wrapper).value);
        } else if (wrapper instanceof LongHolder) {
            writeLong(((LongHolder) wrapper).value);
        }
    }

        private void write(HprofRecord record) throws IOException {
        if (record instanceof StringRecord) {
            StringRecord stringRecord = (StringRecord) record;
            writeNonHeapRecord(HprofReader.STRING_IN_UTF8, () -> {
                writeId(stringRecord.id);
                writeUtf8(stringRecord.string);
            });
        } else if (record instanceof LoadClassRecord) {
            LoadClassRecord loadClassRecord = (LoadClassRecord) record;
            writeNonHeapRecord(HprofReader.LOAD_CLASS, () -> {
                writeInt(loadClassRecord.classSerialNumber);
                writeId(loadClassRecord.id);
                writeInt(loadClassRecord.stackTraceSerialNumber);
                writeId(loadClassRecord.classNameStringId);
            });
        } else if (record instanceof StackTraceRecord) {
            StackTraceRecord stackTraceRecord = (StackTraceRecord) record;
            writeNonHeapRecord(HprofReader.STACK_TRACE, () -> {
                writeInt(stackTraceRecord.stackTraceSerialNumber);
                writeInt(stackTraceRecord.threadSerialNumber);
                writeInt(stackTraceRecord.stackFrameIds.size);
                writeIdArray(stackTraceRecord.stackFrameIds);
            });
        } else if (record instanceof GcRootRecord) {
            GcRootRecord gcRootRecord = (GcRootRecord) record;
            writeGcRootRecord(gcRootRecord.gcRoot, gcRootRecord.id);
        } else if (record instanceof ClassDumpRecord) {
            ClassDumpRecord classDumpRecord = (ClassDumpRecord) record;
            writeClassDumpRecord(classDumpRecord);
        } else if (record instanceof InstanceDumpRecord) {
            InstanceDumpRecord instanceDumpRecord = (InstanceDumpRecord) record;
            writeInstanceDumpRecord(instanceDumpRecord);
        } else if (record instanceof ObjectArrayDumpRecord) {
            ObjectArrayDumpRecord objectArrayDumpRecord = (ObjectArrayDumpRecord) record;
            writeObjectArrayDumpRecord(objectArrayDumpRecord);
        } else if (record instanceof PrimitiveArrayDumpRecord) {
            PrimitiveArrayDumpRecord primitiveArrayDumpRecord = (PrimitiveArrayDumpRecord) record;
            writePrimitiveArrayDumpRecord(primitiveArrayDumpRecord);
        } else if (record instanceof HeapDumpInfoRecord) {
            HeapDumpInfoRecord heapDumpInfoRecord = (HeapDumpInfoRecord) record;
            writeHeapDumpInfoRecord(heapDumpInfoRecord);
        } else if (record instanceof HeapDumpEndRecord) {
            throw new IllegalArgumentException("HprofWriter automatically emits HeapDumpEndRecord");
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
            writeByte(value ? (byte) 1 : (byte) 0);
        }
    }

    private void write(char[] array) throws IOException {
        writeString(new String(array), Charsets.UTF_16BE);
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

  private void writeNonHeapRecord(
      int tag,
      BufferedSink sink,
      Consumer<BufferedSink> block) throws IOException {
    flushHeapBuffer();
    workBuffer.clear();
    block.accept(sink);
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

  private void writeId(BufferedSink sink, long id) throws IOException {
    switch (identifierByteSize) {
      case 1 -> sink.writeByte((int) id);
      case 2 -> {
        byte[] idBytes = new byte[2];
        for (int i = 0; i < 2; i++) {
          idBytes[i] = (byte) (id >> (8 * i));
        }
        sink.write(ByteString.of(idBytes));
      }
      case 4 -> sink.writeInt((int) id);
      case 8 -> sink.writeLong(id);
      default -> throw new IllegalArgumentException("ID Length must be 1, 2, 4, or 8");
    }
  }

  public static HprofWriter open(
      File hprofFile,
      int identifierByteSize,
      HprofVersion hprofVersion) throws IOException {
    FileOutputStream fos = new FileOutputStream(hprofFile);
    BufferedSink sink = Okio.buffer(fos.getOutputStream());
    sink.writeUtf8(hprofVersion.versionString);
    sink.writeByte(0);
    sink.writeInt(identifierByteSize);
    long heapDumpTimestamp = System.currentTimeMillis();
    sink.writeLong(heapDumpTimestamp);
    return new HprofWriter(sink, identifierByteSize, hprofVersion);
  }
}