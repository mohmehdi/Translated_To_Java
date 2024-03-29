
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
import java.util.HashMap;
import java.util.Map;

public class Hprof implements Closeable {

  private final FileChannel channel;
  private final BufferedSource source;
  public final HprofReader reader;
  public final long heapDumpTimestamp;
  public final HprofVersion hprofVersion;
  private int lastReaderByteReadCount = reader.byteReadCount;
  private int lastKnownPosition = reader.byteReadCount;

  private Hprof(FileChannel channel, BufferedSource source, HprofReader reader, long heapDumpTimestamp, HprofVersion hprofVersion) {
    this.channel = channel;
    this.source = source;
    this.reader = reader;
    this.heapDumpTimestamp = heapDumpTimestamp;
    this.hprofVersion = hprofVersion;
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

    public final String versionString;

    HprofVersion(String versionString) {
      this.versionString = versionString;
    }
  }

  private static final Map<String, HprofVersion> supportedVersions = new HashMap<>();

  static {
    for (HprofVersion version : HprofVersion.values()) {
      supportedVersions.put(version.versionString, version);
    }
  }

  public static Hprof open(File hprofFile) throws IOException {
    if (hprofFile.length() == 0L) {
      throw new IllegalArgumentException("Hprof file is 0 byte length");
    }
    FileInputStream inputStream = new FileInputStream(hprofFile);
    FileChannel channel = inputStream.getChannel();
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

    return new Hprof(channel, source, reader, heapDumpTimestamp, hprofVersion);
  }
}