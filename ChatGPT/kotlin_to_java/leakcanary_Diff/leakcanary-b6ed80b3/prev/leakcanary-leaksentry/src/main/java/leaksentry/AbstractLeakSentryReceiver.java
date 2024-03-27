

package leaksentry;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import leaksentry.internal.InternalLeakSentry;

public abstract class AbstractLeakSentryReceiver extends BroadcastReceiver {

    @Override
    public final void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null) {
            switch (intent.getAction()) {
                case LEAK_SENTRY_INSTALLED_INTENT_ACTION:
                    onLeakSentryInstalled(InternalLeakSentry.application);
                    break;
                case REFERENCE_RETAINED_INTENT_ACTION:
                    onReferenceRetained();
                    break;
            }
        }
    }

    public abstract void onLeakSentryInstalled(Application application);

    public abstract void onReferenceRetained();

    public static final String REFERENCE_RETAINED_INTENT_ACTION =
            "leaksentry.AbstractLeakSentryReceiver.referenceRetained";
    public static final String LEAK_SENTRY_INSTALLED_INTENT_ACTION =
            "leaksentry.AbstractLeakSentryReceiver.leakSentryInstalled";

    static void sendLeakSentryInstalled() {
        sendPrivateBroadcast(LEAK_SENTRY_INSTALLED_INTENT_ACTION);
    }

    static void sendReferenceRetained() {
        sendPrivateBroadcast(REFERENCE_RETAINED_INTENT_ACTION);
    }

    private static void sendPrivateBroadcast(String action) {
        Intent intent = new Intent(action);
        intent.setPackage(InternalLeakSentry.application.getPackageName());
        InternalLeakSentry.application.sendBroadcast(intent);
    }
}