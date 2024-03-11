

package leakcanary.internal;

import com.android.tools.perflib.captures.DataBuffer;
import com.android.tools.perflib.captures.MemoryMappedFileBuffer;
import com.squareup.haha.perflib.ArrayInstance;
import com.squareup.haha.perflib.HeapValue;
import com.squareup.haha.perflib.HeapValue.IntValue;
import com.squareup.haha.perflib.HeapValue.ObjectReference;
import com.squareup.haha.perflib.HprofParser;
import com.squareup.haha.perflib.HprofParser.HydradedInstance;
import com.squareup.haha.perflib.HprofParser.RecordCallbacks;
import com.squareup.haha.perflib.Record.HeapDumpRecord.InstanceDumpRecord;
import com.squareup.haha.perflib.Record.LoadClassRecord;
import com.squareup.haha.perflib.Record.StringRecord;
import okio.Buffer;
import okio.Source;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import sun.nio.ch.DirectBuffer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public class HeapParsingTest {

    @Test
    public void findKeyedWeakReferenceClassInHeapDump() throws Exception {
        File file = fileFromName(HeapDumpFile.ASYNC_TASK_P.filename);

        long before1 = System.nanoTime();
        HprofParser parser = HprofParser.open(file);

        long keyedWeakReferenceStringId = -1L;
        long keyedWeakReferenceClassId = -1L;
        java.util.List<InstanceDumpRecord> keyedWeakReferenceInstances = new java.util.ArrayList<>();
        RecordCallbacks callbacks = new RecordCallbacks()
                .on(StringRecord.class, record -> {
                    if (record.string.equals("com.squareup.leakcanary.KeyedWeakReference")) {
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

        for (InstanceDumpRecord instanceRecord : keyedWeakReferenceInstances) {
            HydradedInstance instance = parser.hydrate(instanceRecord);
            System.out.println("####### Dump for " + instance.record.id + " #######");
            instance.classHierarchy.forEachIndexed((classIndex, hydradedClass) ->
                    hydradedClass.fieldNames.forEachIndexed((fieldIndex, name) -> {
                        HeapValue value = instance.fieldValues[classIndex][fieldIndex];
                        System.out.println(name + " = " + value);
                    }));
        }

        HydradedInstance firstInstance = parser.hydrate(keyedWeakReferenceInstances.get(0));
        ClassHierarchy classHierarchy = firstInstance.classHierarchy.get(0);
        int keyFieldIndex = classHierarchy.fieldNames.indexOf("key");
        ObjectReference keyFieldValue = (ObjectReference) instance.fieldValues[0][keyFieldIndex];
        long keyValue = keyFieldValue.value;
        HeapValue hydratedInstanceById = parser.hydratedInstanceById(keyValue);
        String keyString = ((HydradedInstance) hydratedInstanceById).asString(parser);

        System.out.println("Key String: " + keyString);

        parser.close();

        long after1 = System.nanoTime();

        MappedByteBuffer buffer = MemoryMappedFileBuffer.map(file);
        Snapshot snapshot = Snapshot.createSnapshot(buffer);

        long after2 = System.nanoTime();

        System.out.println("First: " + ((after1 - before1) / 1000000) + "ms Second: " + ((after2 - after1) / 1000000) + "ms");
    }

    public String asString(HprofParser parser) {
        ClassHierarchy stringClass = classHierarchy.get(0);
        Map<String, Integer> fieldMap = new HashMap<>();
        for (int i = 0; i < stringClass.fieldNames.size(); i++) {
            fieldMap.put(stringClass.fieldNames.get(i), i);
        }

        HeapValue field(String name) {
            int index = fieldMap.get(name);
            return instance.fieldValues[0][index];
        }

        IntValue count = (IntValue) field("count");

        if (count.value == 0) {
            return "";
        }

        ObjectReference value = (ObjectReference) field("value");

        ArrayInstance array;
        int offset;
        if (HahaHelper.isCharArray(value)) {
            array = (ArrayInstance) value;

            offset = 0;

            if (HahaHelper.hasField(values, "offset")) {
                offset = HahaHelper.fieldValue(values, "offset");
            }

            char[] chars = array.asCharArray(offset, count.value);
            return new String(chars);
        } else if (HahaHelper.isByteArray(value)) {

            array = (ArrayInstance) value;

            try {
                Method asRawByteArray = ArrayInstance.class.getDeclaredMethod("asRawByteArray", int.class, int.class);
                asRawByteArray.setAccessible(true);
                byte[] rawByteArray = (byte[]) asRawByteArray.invoke(array, 0, count.value);
                return new String(rawByteArray, Charset.forName("UTF-8"));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }

        } else {
            throw new UnsupportedOperationException("Could not find char array in " + this);
        }
    }

    @Test
    @Ignore("resetting does not currently work")
    public void memoryMappedReset() throws Exception {
        File file = fileFromName(HeapDumpFile.ASYNC_TASK_P.filename);

        InputStream inputStream = memoryMappedInputStream(file);
        Source source = inputStream.source().buffer();
        String version1 = source.readUtf8(source.indexOf(0));
        inputStream.position(0);
        String version2 = source.readUtf8(source.indexOf(0));
        Assertions.assertThat(version2).isEqualTo(version1);
        Assertions.assertThat(version1).isEqualTo("JAVA PROFILE 1.0.3");
    }

    public static MemoryMappedFileInputStream memoryMappedInputStream(File file) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(file);
        FileChannel fileChannel = fileInputStream.getChannel();
        long size = fileChannel.size();
        long mappedSize = Math.min(size, DEFAULT_MEMORY_MAPPED_BUFFER_SIZE);
        MappedByteBuffer mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, mappedSize);
        Cleaner cleaner = Cleaner.create();
        cleaner.register(new WeakReference<>(mappedByteBuffer), () -> unmap(mappedByteBuffer));
        return new MemoryMappedFileInputStream(fileInputStream, mappedByteBuffer, mappedSize, cleaner);
    }

    public static class MemoryMappedFileInputStream extends InputStream {

        private int position;

        private long length;

        private MappedByteBuffer[] byteBuffers;

        private int index;

        private int offset;

        public MemoryMappedFileInputStream(File file, int bufferSize, int padding) throws FileNotFoundException {
            FileInputStream inputStream = new FileInputStream(file);
            FileChannel fileChannel = inputStream.getChannel();
            length = fileChannel.size();
            int buffersCount = (int) ((length / bufferSize) + 1);
            byteBuffers = new MappedByteBuffer[buffersCount];
            int offset = 0;
            for (int i = 0; i < buffersCount; i++) {
                int size = (int) Math.min(length - offset, (bufferSize + padding));
                byteBuffers[i] = fileChannel.map(FileChannel.MapMode.READ_ONLY, offset, size).order(DataBuffer.HPROF_BYTE_ORDER);
                offset += bufferSize;
            }
            index = 0;
            offset = 0;
        }

        @Override
        public void close() {
            for (MappedByteBuffer byteBuffer : byteBuffers) {
                if (byteBuffer instanceof DirectBuffer) {
                    ((DirectBuffer) byteBuffer).cleaner().clean();
                }
            }
        }

        @Override
        public int read() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public int read(byte[] b) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (len <= 0) {
                return 0;
            }
            require(len < DEFAULT_MEMORY_MAPPED_BUFFER_SIZE, "len > DEFAULT_MEMORY_MAPPED_BUFFER_SIZE");
            require(b.length - off >= len, "" + (b.length - off) + " >= " + len);
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
                    int split = byteBuffers[index].limit() - byteBuffers[index].position();
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
            offset += bytesRead;
            if (offset == byteBuffers[index].limit()) {
                index++;
                offset = 0;
            }
            return bytesRead;
        }

    }

    final int DEFAULT_MEMORY_MAPPED_BUFFER_SIZE = 1 << 30;

    final int DEFAULT_MEMORY_MAPPED_PADDING = 1024;
}