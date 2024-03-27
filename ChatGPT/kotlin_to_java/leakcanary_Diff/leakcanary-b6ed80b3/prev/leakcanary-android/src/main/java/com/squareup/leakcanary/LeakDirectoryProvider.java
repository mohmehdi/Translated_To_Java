
package com.squareup.leakcanary;

import java.io.File;
import java.io.FilenameFilter;

public interface LeakDirectoryProvider {

  List<File> listFiles(FilenameFilter filter);

  
  File newHeapDumpFile();

  boolean hasPendingHeapDump();

  
  void clearLeakDirectory();
}