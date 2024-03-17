

package com.squareup.leakcanary;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

public interface GcTrigger {
    void runGc();

    public static final GcTrigger DEFAULT = new GcTrigger() {
        @Override
        public void runGc() {
            Runtime runtime = Runtime.getRuntime();
            runtime.gc();
            enqueueReferences();
            try {
                Method method = runtime.getClass().getDeclaredMethod("runFinalization", new Class[0]);
                method.setAccessible(true);
                method.invoke(runtime, new Object[0]);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        private void enqueueReferences() {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    };
}