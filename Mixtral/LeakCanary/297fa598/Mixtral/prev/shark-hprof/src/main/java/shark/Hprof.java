

package shark;

import okio.BufferedSource;
import okio.Okio;
import shark.Hprof.HprofVersion;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

public class Hprof implements Closeable {
    private final FileChannel channel;
    private final BufferedSource source;
    private final HprofReader reader;
    private final long heapDumpTimestamp;
    private final HprofVersion hprofVersion;
    private final long fileLength;

    private Hprof(
            FileChannel channel,
            BufferedSource source,
            HprofReader reader,
            long heapDumpTimestamp,
            HprofVersion hprofVersion,
            long fileLength) {
        this.channel = channel;
        this.source = source;
        this.reader = reader;
        this.heapDumpTimestamp = heapDumpTimestamp;
        this.hprofVersion = hprofVersion;
        this.fileLength = fileLength;
    }

    @Override
    public void close() {
        try {
            source.close();
            channel.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void moveReaderTo(long newPosition) {
        long currentPosition = reader.position;

        if (currentPosition == newPosition) {
            return;
        }
        source.buffer().clear();
        try {
            channel.position(newPosition);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        reader.position = newPosition;
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
                map.put(version.versionString, version);
            }
            supportedVersions = map;
        }
    }

    public static Hprof open(File hprofFile) {
        long fileLength = hprofFile.length();
        if (fileLength == 0) {
            throw new IllegalArgumentException("Hprof file is 0 byte length");
        }

        try (FileInputStream inputStream = new FileInputStream(hprofFile);
             FileChannel channel = inputStream.getChannel()) {

            long endOfVersionString = 0;
            try (BufferedSource source = Okio.buffer(Okio.source(inputStream))) {
                endOfVersionString = source.indexOf((byte) 0);
                String versionName = source.readUtf8(endOfVersionString);

                HprofVersion hprofVersion = supportedVersions.get(versionName);

                if (hprofVersion == null) {
                    throw new IllegalArgumentException(
                            "Unsupported Hprof version [" + versionName +
                                    "] not in supported list " + supportedVersions.keySet());
                }

                source.skip(1);
                int identifierByteSize = source.readInt();

                long heapDumpTimestamp = source.readLong();

                int byteReadCount = (int) (endOfVersionString + 1 + 4 + 8);

                HprofReader reader = new HprofReader(source, identifierByteSize, byteReadCount);

                return new Hprof(
                        channel, source, reader, heapDumpTimestamp, hprofVersion, fileLength);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}