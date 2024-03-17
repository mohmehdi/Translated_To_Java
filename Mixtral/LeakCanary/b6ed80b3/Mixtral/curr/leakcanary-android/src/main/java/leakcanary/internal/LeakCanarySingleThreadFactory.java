

package leakcanary.internal;

import java.util.concurrent.ThreadFactory;

public class LeakCanarySingleThreadFactory implements ThreadFactory {
    private final String threadName;

    public LeakCanarySingleThreadFactory(String threadName) {
        this.threadName = "LeakCanary-" + threadName;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        return new Thread(runnable, threadName);
    }
}