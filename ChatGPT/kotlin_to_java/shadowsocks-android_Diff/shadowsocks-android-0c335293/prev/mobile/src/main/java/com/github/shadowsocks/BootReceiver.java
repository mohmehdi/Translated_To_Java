package com.github.shadowsocks;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

public class BootReceiver extends BroadcastReceiver {
    private static final ComponentName componentName = new ComponentName(App.app, BootReceiver.class);

    public static boolean isEnabled() {
        return App.app.getPackageManager().getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    public static void setEnabled(boolean value) {
        int state = value ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        App.app.getPackageManager().setComponentEnabledSetting(componentName, state, PackageManager.DONT_KILL_APP);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            App.app.startService();
        }
    }
}