

package shark;

import java.io.Closeable;
import java.io.File;

@Deprecated("Replaced by HprofStreamingReader.readerFor or HprofRandomAccessReader.openReaderFor")
class Hprof implements Closeable {

    private final File file;
    private final HprofHeader header;
    private final HprofReader reader;
    private final long heapDumpTimestamp;
    private final HprofVersion hprofVersion;
    private final long fileLength;
    private final java.util.List<Closeable> closeables;

    Hprof(File file, HprofHeader header) {
        this.file = file;
        this.header = header;
        this.reader = new HprofReader(this);
        this.heapDumpTimestamp = header.heapDumpTimestamp;
        HprofVersion version = HprofVersion.valueOf(header.version.name);
        this.hprofVersion = version == null ? HprofVersion.JDK1_2_BETA3 : version;
        this.fileLength = file.length();
        this.closeables = new java.util.ArrayList<>();
    }

    void attachClosable(Closeable closeable) {
        closeables.add(closeable);
    }

    @Override
    public void close() {
        for (Closeable closeable : closeables) {
            try {
                closeable.close();
            } catch (Exception e) {
                // Handle exceptions if necessary
            }
        }
    }

    @Deprecated(message = "Moved to top level class", replaceWith = "shark.HprofVersion")
    enum class HprofVersion {
        JDK1_2_BETA3,
        JDK1_2_BETA4,
        JDK_6,
        ANDROID;

        private String versionString;

        HprofVersion(String versionString) {
            this.versionString = versionString;
        }

        HprofVersion() {
            this.versionString = shark.HprofVersion.valueOf(name).versionString;
        }

        String getVersionString() {
            return versionString;
        }

    }

    @Deprecated(message = "Replaced by HprofStreamingReader.readerFor or HprofRandomAccessReader.openReaderFor")
    public static Hprof open(File hprofFile) {
        return new Hprof(hprofFile, HprofHeader.parseHeaderOf(hprofFile));
    }
}