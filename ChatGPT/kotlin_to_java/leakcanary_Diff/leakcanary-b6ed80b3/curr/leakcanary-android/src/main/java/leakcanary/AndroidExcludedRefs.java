package leakcanary;

import android.os.Build;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import java.lang.ref.PhantomReference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.EnumSet;

public enum AndroidExcludedRefs {

    ACTIVITY_CLIENT_RECORD__NEXT_IDLE(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
        @Override
        public void add(ExcludedRefs.Builder excluded) {
            excluded.instanceField("android.app.ActivityThread$ActivityClientRecord", "nextIdle")
                    .reason("Android AOSP sometimes keeps a reference to a destroyed activity as a"
                            + " nextIdle client record in the android.app.ActivityThread.mActivities map."
                            + " Not sure what's going on there, input welcome.");
        }
    },

    SPAN_CONTROLLER(Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
        @Override
        public void add(ExcludedRefs.Builder excluded) {
            String reason = ("Editor inserts a special span, which has a reference to the EditText. That span is a"
                    + " NoCopySpan, which makes sure it gets dropped when creating a new"
                    + " SpannableStringBuilder from a given CharSequence."
                    + " TextView.onSaveInstanceState() does a copy of its mText before saving it in the"
                    + " bundle. Prior to KitKat, that copy was done using the SpannableString"
                    + " constructor, instead of SpannableStringBuilder. The SpannableString constructor"
                    + " does not drop NoCopySpan spans. So we end up with a saved state that holds a"
                    + " reference to the textview and therefore the entire view hierarchy & activity"
                    + " context. Fix: https:"
                    + "/af7dcdf35a37d7a7dbaad7d9869c1c91bce2272b ."
                    + " To fix this, you could override TextView.onSaveInstanceState(), and then use"
                    + " reflection to access TextView.SavedState.mText and clear the NoCopySpan spans.");
            excluded.instanceField("android.widget.Editor$EasyEditSpanController", "this$0")
                    .reason(reason);
            excluded.instanceField("android.widget.Editor$SpanController", "this$0")
                    .reason(reason);
        }
    },

    MEDIA_SESSION_LEGACY_HELPER__SINSTANCE(Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
        @Override
        public void add(ExcludedRefs.Builder excluded) {
            excluded.staticField("android.media.session.MediaSessionLegacyHelper", "sInstance")
                    .reason("MediaSessionLegacyHelper is a static singleton that is lazily instantiated and"
                            + " keeps a reference to the context it's given the first time"
                            + " MediaSessionLegacyHelper.getHelper() is called."
                            + " This leak was introduced in android-5.0.1_r1 and fixed in Android 5.1.0_r1 by"
                            + " calling context.getApplicationContext()."
                            + " Fix: https:"
                            + "/9b5257c9c99c4cb541d8e8e78fb04f008b1a9091"
                            + " To fix this, you could call MediaSessionLegacyHelper.getHelper() early"
                            + " in Application.onCreate() and pass it the application context.");
        }
    },

    // Add more enum cases as needed

    SOFT_REFERENCES {
        @Override
        public void add(ExcludedRefs.Builder excluded) {
            excluded.clazz(WeakReference.class.getName())
                    .alwaysExclude();
            excluded.clazz(SoftReference.class.getName())
                    .alwaysExclude();
            excluded.clazz(PhantomReference.class.getName())
                    .alwaysExclude();
            excluded.clazz("java.lang.ref.Finalizer")
                    .alwaysExclude();
            excluded.clazz("java.lang.ref.FinalizerReference")
                    .alwaysExclude();
        }
    },

    FINALIZER_WATCHDOG_DAEMON {
        @Override
        public void add(ExcludedRefs.Builder excluded) {
            excluded.thread("FinalizerWatchdogDaemon")
                    .alwaysExclude();
        }
    },

    MAIN {
        @Override
        public void add(ExcludedRefs.Builder excluded) {
            excluded.thread("main")
                    .alwaysExclude();
        }
    },

    LEAK_CANARY_THREAD {
        @Override
        public void add(ExcludedRefs.Builder excluded) {
            excluded.thread(HeapDumpTrigger.LEAK_CANARY_THREAD_NAME)
                    .alwaysExclude();
        }
    },

    EVENT_RECEIVER__MMESSAGE_QUEUE {
        @Override
        public void add(ExcludedRefs.Builder excluded) {
            excluded.instanceField(
                    "android.view.Choreographer$FrameDisplayEventReceiver",
                    "mMessageQueue"
            )
                    .alwaysExclude();
        }
    };

    private final boolean applies;

    AndroidExcludedRefs(boolean applies) {
        this.applies = applies;
    }

    public abstract void add(ExcludedRefs.Builder excluded);

    public static ExcludedRefs.Builder createAndroidDefaults() {
        return createBuilder(
                EnumSet.of(
                        SOFT_REFERENCES,
                        FINALIZER_WATCHDOG_DAEMON,
                        MAIN,
                        LEAK_CANARY_THREAD,
                        EVENT_RECEIVER__MMESSAGE_QUEUE
                )
        );
    }

    public static ExcludedRefs.Builder createAppDefaults() {
        return createBuilder(
                EnumSet.allOf(AndroidExcludedRefs.class)
        );
    }

    public static ExcludedRefs.Builder createBuilder(EnumSet<AndroidExcludedRefs> refs) {
        ExcludedRefs.Builder excluded = ExcludedRefs.builder();
        for (AndroidExcludedRefs ref : refs) {
            if (ref.applies) {
                ref.add(excluded);
                ((ExcludedRefs.BuilderWithParams) excluded).named(ref.name());
            }
        }
        return excluded;
    }
}