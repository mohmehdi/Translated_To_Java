

package leaksentry;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import leaksentry.internal.InternalLeakSentry;

public abstract class AbstractLeakSentryReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            String action = intent.getAction();
            if (LEAK_SENTRY_INSTALLED_INTENT_ACTION.equals(action)) {
                onLeakSentryInstalled(InternalLeakSentry.getApplication());
            } else if (REFERENCE_RETAINED_INTENT_ACTION.equals(action)) {
                onReferenceRetained();
            }
        }
    }

    public abstract void onLeakSentryInstalled(Application application);

    public abstract void onReferenceRetained();


    public static final String REFERENCE_RETAINED_INTENT_ACTION =
            "leaksentry.AbstractLeakSentryReceiver.referenceRetained";
    public static final String LEAK_SENTRY_INSTALLED_INTENT_ACTION =
            "leaksentry.AbstractLeakSentryReceiver.leakSentryInstalled";

    public static void sendLeakSentryInstalled() {
        sendPrivateBroadcast(LEAK_SENTRY_INSTALLED_INTENT_ACTION);
    }

    public static void sendReferenceRetained() {
        sendPrivateBroadcast(REFERENCE_RETAINED_INTENT_ACTION);
    }

    private static void sendPrivateBroadcast(String action) {
        Intent intent = new Intent(action);
        intent.setPackage(InternalLeakSentry.getApplication().getPackageName());
        InternalLeakSentry.getApplication().sendBroadcast(intent);
    }
}