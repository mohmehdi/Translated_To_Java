
package leakcanary;

public interface DebuggerControl {

    boolean isDebuggerAttached();

    DebuggerControl NONE = new DebuggerControl() {
        @Override
        public boolean isDebuggerAttached() {
            return false;
        }
    };
}