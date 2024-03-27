
package leakcanary;

import android.os.Debug;

public class AndroidDebuggerControl implements DebuggerControl {
  
  @Override
  public boolean isDebuggerAttached() {
    return Debug.isDebuggerConnected();
  }
}