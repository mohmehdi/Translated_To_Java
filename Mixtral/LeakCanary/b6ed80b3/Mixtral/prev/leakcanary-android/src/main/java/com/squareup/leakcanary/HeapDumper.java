

package com.squareup.leakcanary;

import java.io.File;

public interface HeapDumper {
  
  File dumpHeap();
  
  HeapDumper NONE = new HeapDumper() {
    @Override
    public File dumpHeap() {
      return null;
    }
  };
  
  File RETRY_LATER = null;
}