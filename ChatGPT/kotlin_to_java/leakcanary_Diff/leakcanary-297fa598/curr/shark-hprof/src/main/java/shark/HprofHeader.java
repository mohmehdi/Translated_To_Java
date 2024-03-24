

package shark;

import okio.BufferedSource;
import okio.Okio;

import java.io.File;
import java.io.IOException;

public class HprofHeader {

    private final long heapDumpTimestamp = System.currentTimeMillis();
    private final HprofVersion version = HprofVersion.ANDROID;
    private final int identifierByteSize = 4;
    private final int recordsPosition = version.versionString.getBytes(Charsets.UTF_8).length + 1 + 4 + 8;

    private static final Map<String, HprofVersion> supportedVersions = Arrays.stream(HprofVersion.values())
            .collect(Collectors.toMap(HprofVersion::getVersionString, Function.identity()));

    public static HprofHeader parseHeaderOf(File hprofFile) throws IOException {
        long fileLength = hprofFile.length();
        if (fileLength == 0L) {
            throw new IllegalArgumentException("Hprof file is 0 byte length");
        }
        try (BufferedSource source = Okio.buffer(Okio.source(hprofFile))) {
            return parseHeaderOf(source);
        }
    }

    public static HprofHeader parseHeaderOf(BufferedSource source) throws IOException {
        if (source.exhausted()) {
            throw new IllegalArgumentException("Source has no available bytes");
        }
        int endOfVersionString = source.indexOf((byte) 0);
        String versionName = source.readUtf8(endOfVersionString);

        HprofVersion version = supportedVersions.get(versionName);
        if (version == null) {
            throw new IllegalArgumentException("Unsupported Hprof version [" + versionName + "] not in supported list " + supportedVersions.keySet());
        }

        source.skip(1);
        int identifierByteSize = source.readInt();
        long heapDumpTimestamp = source.readLong();
        return new HprofHeader(heapDumpTimestamp, version, identifierByteSize);
    }
}