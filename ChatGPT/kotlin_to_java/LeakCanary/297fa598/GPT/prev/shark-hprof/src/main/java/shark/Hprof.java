

package shark;

import okio.BufferedSource;
import okio.Okio;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class Hprof implements Closeable {

    private final FileChannel channel;
    private final BufferedSource source;
    private final HprofReader reader;
    private final long heapDumpTimestamp;
    private final HprofVersion hprofVersion;
    private final long fileLength;

    private Hprof(FileChannel channel, BufferedSource source, HprofReader reader, long heapDumpTimestamp, HprofVersion hprofVersion, long fileLength) {
        this.channel = channel;
        this.source = source;
        this.reader = reader;
        this.heapDumpTimestamp = heapDumpTimestamp;
        this.hprofVersion = hprofVersion;
        this.fileLength = fileLength;
    }

    @Override
    public void close() throws IOException {
        source.close();
    }

    public void moveReaderTo(long newPosition) throws IOException {
        long currentPosition = reader.position;

        if (currentPosition == newPosition) {
            return;
        }
        source.buffer().clear();
        channel.position(newPosition);
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
    }

    private static final java.util.Map<String, HprofVersion> supportedVersions = java.util.Arrays.stream(HprofVersion.values())
            .collect(java.util.stream.Collectors.toMap(version -> version.versionString, version -> version));

    public static Hprof open(File hprofFile) throws IOException {
        long fileLength = hprofFile.length();
        if (fileLength == 0L) {
            throw new IllegalArgumentException("Hprof file is 0 byte length");
        }
        java.io.InputStream inputStream = new java.io.FileInputStream(hprofFile);
        FileChannel channel = ((java.io.FileInputStream) inputStream).getChannel();
        BufferedSource source = Okio.buffer(Okio.source(inputStream));

        int endOfVersionString = source.indexOf((byte) 0);
        String versionName = source.readUtf8(endOfVersionString);

        HprofVersion hprofVersion = supportedVersions.get(versionName);

        if (hprofVersion == null) {
            throw new IllegalArgumentException("Unsupported Hprof version [" + versionName + "] not in supported list " + supportedVersions.keySet());
        }

        source.skip(1);
        int identifierByteSize = source.readInt();

        long heapDumpTimestamp = source.readLong();

        int byteReadCount = endOfVersionString + 1 + 4 + 8;

        HprofReader reader = new HprofReader(source, identifierByteSize, byteReadCount);

        return new Hprof(channel, source, reader, heapDumpTimestamp, hprofVersion, fileLength);
    }
}