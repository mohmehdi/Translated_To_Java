package leakcanary.internal;

import java.io.File;
import java.io.FilenameFilter;

internal interface LeakDirectoryProvider {

  List<File> listFiles(FilenameFilter filter);

  File newHeapDumpFile();

  boolean hasPendingHeapDump();

  void clearLeakDirectory();
}