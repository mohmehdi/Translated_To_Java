

package leaksentry;

import android.app.Application;
import java.util.concurrent.TimeUnit;
import leaksentry.internal.InternalLeakSentry;

public class LeakSentry {

    public static class Config {
        public boolean watchActivities = true;
        public boolean watchFragments = true;
        public boolean watchFragmentViews = true;
        public long watchDurationMillis = TimeUnit.SECONDS.toMillis(5);
    }

    public static volatile Config config = new Config();

    public static RefWatcher refWatcher() {
        return InternalLeakSentry.refWatcher;
    }

    public static void manualInstall(Application application) {
        InternalLeakSentry.install(application);
    }

}

Note that in Java, you need to use the `new` keyword to create an instance of a class, and you need to use parentheses `()` to call a function or constructor. In Kotlin, you can omit the parentheses in many cases. Also, in Java, you need to use curly braces `{}