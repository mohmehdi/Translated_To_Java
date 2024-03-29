
package shark;

import okio.BufferedSource;
import okio.Buffer;
import okio.Okio;
import okio.Source;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class Hprof implements Closeable {

    private final FileChannel channel;
    private final BufferedSource source;
    private final HprofReader reader;
    private final long heapDumpTimestamp;
    private int lastReaderByteReadCount;
    private long lastKnownPosition;

    private Hprof(FileChannel channel, BufferedSource source, HprofReader reader, long heapDumpTimestamp) {
        this.channel = channel;
        this.source = source;
        this.reader = reader;
        this.heapDumpTimestamp = heapDumpTimestamp;
        this.lastReaderByteReadCount = reader.byteReadCount;
        this.lastKnownPosition = reader.byteReadCount;
    }

    @Override
    public void close() throws IOException {
        source.close();
    }

    public void moveReaderTo(long newPosition) throws IOException {
        long currentPosition = lastKnownPosition + (reader.byteReadCount - lastReaderByteReadCount);

        if (currentPosition == newPosition) {
            return;
        }
        source.buffer().clear();
        channel.position(newPosition);
        lastReaderByteReadCount = reader.byteReadCount;
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

    private static final String[] supportedVersions = new String[]{
            HprofVersion.JDK1_2_BETA3.versionString,
            HprofVersion.JDK1_2_BETA4.versionString,
            HprofVersion.JDK_6.versionString,
            HprofVersion.ANDROID.versionString
    };

    public static Hprof open(File hprofFile) throws IOException {
        if (hprofFile.length() == 0L) {
            throw new IllegalArgumentException("Hprof file is 0 byte length");
        }
        FileInputStream inputStream = new FileInputStream(hprofFile);
        FileChannel channel = inputStream.getChannel();
        Source source = Okio.source(inputStream).buffer();

        Buffer buffer = new Buffer();
        source.read(buffer, 1);
        String version = buffer.readUtf8();
        if (!isVersionSupported(version)) {
            throw new IllegalArgumentException("Unsupported Hprof version [" + version + "] not in supported list " + String.join(", ", supportedVersions));
        }

        source.skip(1);
        int objectIdByteSize = source.readInt();

        long heapDumpTimestamp = source.readLong();

        int byteReadCount = version.length() + 1 + 4 + 8;

        HprofReader reader = new HprofReader(source, byteReadCount, objectIdByteSize);

        return new Hprof(channel, (BufferedSource) source, reader, heapDumpTimestamp);
    }

    private final List<String> supportedVersions;
}