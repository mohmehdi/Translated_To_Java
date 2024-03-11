

package leakcanary.internal;

import com.android.tools.perflib.captures.DataBuffer;
import com.android.tools.perflib.captures.MemoryMappedFileBuffer;
import com.squareup.haha.perflib.Snapshot;
import leakcanary.internal.haha.HeapValue.ObjectReference;
import leakcanary.internal.haha.HprofParser;
import leakcanary.internal.haha.HprofParser.HydratedInstance;
import leakcanary.internal.haha.HprofParser.RecordCallbacks;
import leakcanary.internal.haha.Record.HeapDumpRecord.ObjectRecord.InstanceDumpRecord;
import leakcanary.internal.haha.Record.LoadClassRecord;
import leakcanary.internal.haha.Record.StringRecord;
import okio.Buffer;
import okio.Source;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;
import sun.nio.ch.DirectBuffer;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class HeapParsingTest {

  @Test
  public void findKeyedWeakReferenceClassInHeapDump() throws IOException {
    HeapDump heapDump = HeapDump.ASYNC_TASK_P;
    String heapDumpFileName = heapDump.filename;
    File file = new File(heapDumpFileName);

    long before1 = System.nanoTime();
    HprofParser parser = HprofParser.open(file);

    long keyedWeakReferenceStringId = -1;
    long keyedWeakReferenceClassId = -1;
    List<InstanceDumpRecord> keyedWeakReferenceInstances = new ArrayList<>();
    Callbacks callbacks = new RecordCallbacks()
        .on(StringRecord.class, record -> {
          if ("com.squareup.leakcanary.KeyedWeakReference".equals(record.string)) {
            keyedWeakReferenceStringId = record.id;
          }
        })
        .on(LoadClassRecord.class, record -> {
          if (record.classNameStringId == keyedWeakReferenceStringId) {
            keyedWeakReferenceClassId = record.id;
          }
        })
        .on(InstanceDumpRecord.class, record -> {
          if (record.classId == keyedWeakReferenceClassId) {
            keyedWeakReferenceInstances.add(record);
          }
        });
    parser.scan(callbacks);

    InstanceDumpRecord targetWeakRef = keyedWeakReferenceInstances.stream()
        .map(parser::hydrateInstance)
        .filter(instance -> {
          String keyString = parser.retrieveString(instance.fieldValue("key"));
          return heapDump.referenceKey.equals(keyString);
        })
        .findFirst()
        .orElse(null);

    String found = parser.retrieveString(targetWeakRef.fieldValue("key"));

    parser.close();

    long after1 = System.nanoTime();

    Path path = Paths.get(heapDumpFileName);
    MemoryMappedFileBuffer buffer = new MemoryMappedFileBuffer(Files.newByteChannel(path));
    Snapshot snapshot = Snapshot.createSnapshot(buffer);

    long after2 = System.nanoTime();

    System.out.println("First: " + TimeUnit.MILLISECONDS.convert(after1 - before1, TimeUnit.NANOSECONDS) + "ms");
    System.out.println("Second: " + TimeUnit.MILLISECONDS.convert(after2 - after1, TimeUnit.NANOSECONDS) + "ms");

    Assert.assertEquals(keyedWeakReferenceClassId, snapshot.findClass("com.squareup.leakcanary.KeyedWeakReference").id);
  }

  public boolean hasField(HydratedInstance instance, String name) {
    for (HydratedClass hydratedClass : instance.classHierarchy) {
      for (String fieldName : hydratedClass.fieldNames) {
        if (fieldName.equals(name)) {
          return true;
        }
      }
    }
    return false;
  }

  @Test
  @Ignore("resetting does not currently work")
  public void memoryMappedReset() throws FileNotFoundException {
    File file = fileFromName(HeapDumpFile.ASYNC_TASK_P.filename);

    InputStream inputStream = file.memoryMappedInputStream();
    Source source = inputStream.source().buffer();
    try {
      String version1 = source.readUtf8(source.indexOf(0));
      inputStream.position = 0;
      String version2 = source.readUtf8(source.indexOf(0));
      Assertions.assertThat(version2).isEqualTo(version1);
      Assertions.assertThat(version1).isEqualTo("JAVA PROFILE 1.0.3");
    } finally {
      source.close();
    }
  }
}


public class MemoryMappedFileInputStream extends InputStream {

  private long position = 0;

  private long length;

  private ByteBuffer[] byteBuffers;

  private int index;

  private int offset;

  public MemoryMappedFileInputStream(File file, int bufferSize, int padding) throws FileNotFoundException {
    length = file.length();

    int buffersCount = (int) ((length / bufferSize)) + 1;
    byteBuffers = new ByteBuffer[buffersCount];

    RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
    FileChannel fileChannel = randomAccessFile.getChannel();
    try {
      long offset = 0;
      for (int i = 0; i < buffersCount; i++) {
        long size = Math.min(length - offset, (bufferSize + padding));

        byteBuffers[i] = fileChannel.map(MapMode.READ_ONLY, offset, size).order(DataBuffer.HPROF_BYTE_ORDER);
        offset += bufferSize;
      }
    } finally {
      fileChannel.close();
      randomAccessFile.close();
    }
  }

  @Override
  public void close() {
    for (ByteBuffer byteBuffer : byteBuffers) {
      if (byteBuffer instanceof DirectBuffer) {
        ((DirectBuffer) byteBuffer).cleaner().clean();
      }
    }
  }

  @Override
  public int read() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int read(byte[] b) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int read(byte[] b, int off, int len) {
    if (len == 0) {
      return 0;
    }
    require(len > 0, "len < 0: " + len);
    require(len < DEFAULT_MEMORY_MAPPED_BUFFER_SIZE, "$len > $DEFAULT_MEMORY_MAPPED_BUFFER_SIZE");
    require(b.length - off >= len, "${b.length} - $off >= $len");
    byteBuffers[index].position(offset);
    int bytesRead;
    if (len <= byteBuffers[index].remaining()) {
      byteBuffers[index].get(b, off, len);
      bytesRead = len;
    } else {
      if (index == byteBuffers.length - 1) {
        bytesRead = byteBuffers[index].remaining();
        byteBuffers[index].get(b, off, bytesRead);
      } else {
        int split = bufferSize - byteBuffers[index].position();
        byteBuffers[index].get(b, off, split);
        byteBuffers[index + 1].position(0);

        int readInNextBuffer = len - split;

        int remainingInNextBuffer = byteBuffers[index + 1].remaining();
        if (remainingInNextBuffer < readInNextBuffer) {
          bytesRead = split + remainingInNextBuffer;
          byteBuffers[index + 1].get(b, off + split, remainingInNextBuffer);
        } else {
          byteBuffers[index + 1].get(b, off + split, readInNextBuffer);
          bytesRead = len;
        }
      }
    }
    position += bytesRead;

    return bytesRead;
  }
}