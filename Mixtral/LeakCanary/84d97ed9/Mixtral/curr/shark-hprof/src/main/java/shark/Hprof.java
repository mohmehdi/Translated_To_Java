

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
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public class Hprof implements Closeable {
    private final FileChannel channel;
    private final BufferedSource source;
    private final HprofReader reader;
    private final long heapDumpTimestamp;
    private final HprofVersion hprofVersion;

    private long lastReaderByteReadCount;
    private long lastKnownPosition;

    private Hprof(FileChannel channel, BufferedSource source, HprofReader reader, long heapDumpTimestamp, HprofVersion hprofVersion) {
        this.channel = channel;
        this.source = source;
        this.reader = reader;
        this.heapDumpTimestamp = heapDumpTimestamp;
        this.hprofVersion = hprofVersion;

        this.lastReaderByteReadCount = reader.getByteReadCount();
        this.lastKnownPosition = reader.getByteReadCount();
    }

    @Override
    public void close() {
        try {
            source.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void moveReaderTo(long newPosition) {
        long currentPosition = lastKnownPosition + (reader.getByteReadCount() - lastReaderByteReadCount);

        if (currentPosition == newPosition) {
            return;
        }

        source.buffer().clear();
        try {
            channel.position(newPosition);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

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



        private static final Map<String, HprofVersion> supportedVersions;

        static {
            Map<String, HprofVersion> map = new EnumMap<>(HprofVersion.class);
            for (HprofVersion version : values()) {
                map.put(version.getVersionString(), version);
            }
            supportedVersions = map;
        }

    }

    public static Hprof open(File hprofFile) {
        if (hprofFile.length() == 0) {
            throw new IllegalArgumentException("Hprof file is 0 byte length");
        }

        try (FileInputStream inputStream = new FileInputStream(hprofFile)) {
            FileChannel channel = inputStream.getChannel();
            Source source = Okio.source(channel);
            BufferedSource bufferedSource = Okio.buffer(source);

            int endOfVersionString = bufferedSource.indexOf((byte) 0);
            String versionName = bufferedSource.readUtf8(endOfVersionString);

            HprofVersion hprofVersion = HprofVersion.getSupportedVersion(versionName);

            bufferedSource.skip(1);
            int identifierByteSize = bufferedSource.readInt();

            long heapDumpTimestamp = bufferedSource.readLong();

            long byteReadCount = endOfVersionString + 1 + 4 + 8;

            HprofReader reader = new HprofReader(bufferedSource, identifierByteSize, byteReadCount);

            return new Hprof(channel, bufferedSource, reader, heapDumpTimestamp, hprofVersion);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}