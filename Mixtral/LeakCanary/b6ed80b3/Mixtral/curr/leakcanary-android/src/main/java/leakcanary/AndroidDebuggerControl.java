

package leakcanary;

import android.os.Debug;

public class AndroidDebuggerControl extends DebuggerControl {
    @Override
    public boolean isDebuggerAttached() {
        return Debug.isDebuggerConnected();
    }
}