package com.github.shadowsocks;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import com.github.shadowsocks.App.Companion.app;
import com.github.shadowsocks.preference.DataStore;

public class BootReceiver extends BroadcastReceiver {
    private static final ComponentName componentName = new ComponentName(app, BootReceiver.class);
    private static boolean enabled;

    public static boolean isEnabled() {
        return app.getPackageManager().getComponentEnabledSetting(componentName) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    public static void setEnabled(boolean value) {
        app.getPackageManager().setComponentEnabledSetting(componentName,
                value ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean locked;
        switch (intent.getAction()) {
            case Intent.ACTION_BOOT_COMPLETED:
                locked = false;
                break;
            case Intent.ACTION_LOCKED_BOOT_COMPLETED:
                locked = true;
                break;
            default:
                return;
        }
        if (DataStore.directBootAware == locked) {
            app.startService();
        }
    }
}