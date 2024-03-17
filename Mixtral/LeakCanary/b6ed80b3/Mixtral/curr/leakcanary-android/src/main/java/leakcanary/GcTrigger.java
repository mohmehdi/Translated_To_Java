

package leakcanary;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

public interface GcTrigger {
    void runGc();

    public static final GcTrigger DEFAULT = new GcTrigger() {
        @Override
        public void runGc() {
            Runtime.getRuntime()
                    .gc();
            enqueueReferences();
            System.runFinalization();
        }

        private void enqueueReferences() {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                throw new AssertionError();
            }
        }
    };
}