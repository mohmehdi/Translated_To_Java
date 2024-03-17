

package leakcanary;

import java.io.File;
import java.io.FilenameFilter;

public interface LeakDirectoryProvider {
  public List<File> listFiles(FilenameFilter filter);

  public File newHeapDumpFile();

  public boolean hasPendingHeapDump();

  public void clearLeakDirectory();
}