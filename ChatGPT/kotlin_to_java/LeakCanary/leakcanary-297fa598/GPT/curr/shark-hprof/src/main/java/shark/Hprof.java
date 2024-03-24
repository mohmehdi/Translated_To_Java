

package shark;

import java.io.Closeable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Deprecated("Replaced by HprofStreamingReader.readerFor or HprofRandomAccessReader.openReaderFor")
public class Hprof implements Closeable {

    private final File file;
    private final HprofHeader header;
    private final HprofReader reader = new HprofReader(this);

    public Hprof(File file, HprofHeader header) {
        this.file = file;
        this.header = header;
    }

    public long getHeapDumpTimestamp() {
        return header.getHeapDumpTimestamp();
    }

    public HprofVersion getHprofVersion() {
        return HprofVersion.valueOf(header.getVersion().name());
    }

    public long getFileLength() {
        return file.length();
    }

    private final List<Closeable> closeables = new ArrayList<>();

    public void attachClosable(Closeable closeable) {
        closeables.add(closeable);
    }

    @Override
    public void close() {
        closeables.forEach(Closeable::close);
    }

    @Deprecated(message = "Moved to top level class", replaceWith = ReplaceWith("shark.HprofVersion"))
    public enum HprofVersion {
        JDK1_2_BETA3,
        JDK1_2_BETA4,
        JDK_6,
        ANDROID;

        public String getVersionString() {
            return shark.HprofVersion.valueOf(name()).getVersionString();
        }
    }

    public static Hprof open(File hprofFile) {
        return new Hprof(hprofFile, HprofHeader.parseHeaderOf(hprofFile));
    }
}