

package com.squareup.leakcanary;

import java.lang.reflect.Method;

public interface DebuggerControl {
  boolean isDebuggerAttached();

  DebuggerControl NONE = new DebuggerControl() {
    @Override
    public boolean isDebuggerAttached() {
      return false;
    }
  };
}