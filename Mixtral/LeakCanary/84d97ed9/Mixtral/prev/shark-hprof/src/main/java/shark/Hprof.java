

package shark;

import okio.BufferedSource;
import okio.Okio;
import okio.Source;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

public class Hprof implements Closeable {
    private final FileChannel channel;
    private final BufferedSource source;
    private final HprofReader reader;
    private final long heapDumpTimestamp;

    private long lastReaderByteReadCount;
    private long lastKnownPosition;

    private Hprof(FileChannel channel, BufferedSource source, HprofReader reader, long heapDumpTimestamp) {
        this.channel = channel;
        this.source = source;
        this.reader = reader;
        this.heapDumpTimestamp = heapDumpTimestamp;
        this.lastReaderByteReadCount = reader.getByteReadCount();
        this.lastKnownPosition = reader.getByteReadCount();
    }

    @Override
    public void close() throws IOException {
        source.close();
    }

    public void moveReaderTo(long newPosition) {
        long currentPosition = lastKnownPosition + (reader.getByteReadCount() - lastReaderByteReadCount);

        if (currentPosition == newPosition) {
            return;
        }
        source.buffer().clear();
        channel.position(newPosition);
        lastReaderByteReadCount = reader.getByteReadCount();
        lastKnownPosition = newPosition;
    }

    public enum HprofVersion {
        JDK1_2_BETA3("JAVA PROFILE 1.0"),
        JDK1_2_BETA4("JAVA PROFILE 1.0.1"),
        JDK_6("JAVA PROFILE 1.0.2"),
        ANDROID("JAVA PROFILE 1.0.3");

        private final String versionString;

        HprofVersion(String versionString) {
            this.versionString = versionString;
        }


    }

    public static Hprof open(File hprofFile) throws IOException {
        if (hprofFile.length() == 0) {
            throw new IllegalArgumentException("Hprof file is 0 byte length");
        }

        FileInputStream inputStream = new FileInputStream(hprofFile);
        FileChannel channel = inputStream.getChannel();
        Source source = Okio.source(inputStream);
        BufferedSource bufferedSource = source.buffer();

        int endOfVersionString = bufferedSource.indexOf((byte) 0);
        String version = bufferedSource.readUtf8(endOfVersionString);

        bufferedSource.skip(1);
        int objectIdByteSize = bufferedSource.readInt();

        long heapDumpTimestamp = bufferedSource.readLong();

        int byteReadCount = endOfVersionString + 1 + 4 + 8;

        HprofReader reader = new HprofReader(bufferedSource, byteReadCount, objectIdByteSize);

        return new Hprof(channel, bufferedSource, reader, heapDumpTimestamp);
    }
}