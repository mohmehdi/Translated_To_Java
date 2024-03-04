

package shark;

import okio.BufferedSource;
import okio.Okio;
import okio.Source;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charsets;
import java.util.HashMap;
import java.util.Map;

enum class HprofVersion {
  ANDROID("android"), JAVA("java");

  private final String versionString;

  HprofVersion(String versionString) {
    this.versionString = versionString;
  }

}

record HprofHeader(
  long heapDumpTimestamp,
  HprofVersion version,
  int identifierByteSize
) {


  public static HprofHeader parseHeaderOf(File hprofFile) {
    long fileLength = hprofFile.length();
    if (fileLength == 0) {
      throw new IllegalArgumentException("Hprof file is 0 byte length");
    }
    try (Source source = Okio.source(new FileInputStream(hprofFile))) {
      return parseHeaderOf(source);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static HprofHeader parseHeaderOf(BufferedSource source) {
    if (!source.exhausted()) {
      int endOfVersionString = source.indexOf((byte) 0);
      String versionName = source.readUtf8(endOfVersionString);

      Map<String, HprofVersion> supportedVersions = new HashMap<>();
      for (HprofVersion version : HprofVersion.values()) {
        supportedVersions.put(version.getVersionString(), version);
      }

      HprofVersion version = supportedVersions.get(versionName);
      if (version == null) {
        throw new IllegalArgumentException(
            "Unsupported Hprof version [" + versionName + "] not in supported list " + supportedVersions.keySet());
      }

      source.skip(1);
      int identifierByteSize = source.readInt();
      long heapDumpTimestamp = source.readLong();

      return new HprofHeader(heapDumpTimestamp, version, identifierByteSize);
    } else {
      throw new IllegalArgumentException("Source has no available bytes");
    }
  }
}