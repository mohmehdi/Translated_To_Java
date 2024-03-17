

package leakcanary;

import java.lang.reflect.Method;

public interface DebuggerControl {
  boolean isDebuggerAttached();

  public static final DebuggerControl NONE = new DebuggerControl() {
    @Override
    public boolean isDebuggerAttached() {
      return false;
    }
  };
}