package com.github.shadowsocks;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.UserManager;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.work.Configuration;

import com.crashlytics.android.Crashlytics;
import com.github.shadowsocks.acl.Acl;
import com.github.shadowsocks.aidl.ShadowsocksConnection;
import com.github.shadowsocks.core.R;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.github.shadowsocks.net.TcpFastOpen;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.DirectBoot;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import io.fabric.sdk.android.Fabric;

import java.io.File;
import java.io.IOException;
import kotlin.reflect.KClass;

public class Core implements Configuration.Provider {
    public static final String TAG = "Core";

    public static Application app;
    public static PendingIntent configureIntent;
    public static final ConnectivityManager connectivity = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
    public static final PackageInfo packageInfo = getPackageInfo(app.getPackageName());
    public static final Object deviceStorage = (Build.VERSION.SDK_INT < 24) ? app : new DeviceStorageApp(app);
    public static final FirebaseAnalytics analytics = FirebaseAnalytics.getInstance((Context) deviceStorage);
    public static final boolean directBootSupported = (Build.VERSION.SDK_INT >= 24 &&
            ((DevicePolicyManager) app.getSystemService(Context.DEVICE_POLICY_SERVICE)).getStorageEncryptionStatus()
                    == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER);

    public static final List<Long> activeProfileIds = ProfileManager.getProfile(DataStore.profileId) != null ?
            Arrays.asList(ProfileManager.getProfile(DataStore.profileId).id,
                    ProfileManager.getProfile(DataStore.profileId).udpFallback) : Collections.emptyList();
    public static final Pair<Profile, Profile> currentProfile = DataStore.directBootAware ? DirectBoot.getDeviceProfile() : ProfileManager.expand(ProfileManager.getProfile(DataStore.profileId));

    public static Profile switchProfile(long id) {
        Profile result = ProfileManager.getProfile(id);
        if (result == null) {
            result = ProfileManager.createProfile();
        }
        DataStore.profileId = result.id;
        return result;
    }

    public static <T> void init(T app, KClass<? extends Object> configureClass) {
        Core.app = (Application) app;
        configureIntent = (context) -> {
            Intent intent = new Intent(context, configureClass.getClass());
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            return PendingIntent.getActivity(context, 0, intent, 0);
        };

        if (Build.VERSION.SDK_INT >= 24) {
            ((DeviceStorageApp) deviceStorage).moveDatabaseFrom((Context) app, Key.DB_PUBLIC);
            File old = Acl.getFile(Acl.CUSTOM_RULES, (Context) app);
            if (old.canRead()) {
                Acl.getFile(Acl.CUSTOM_RULES).writeText(old.readText());
                old.delete();
            }
        }

        System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON);
        Fabric.with((Context) deviceStorage, new Crashlytics());
        FirebaseApp.initializeApp((Context) deviceStorage);

        if (Build.VERSION.SDK_INT >= 24 && DataStore.directBootAware &&
                ((UserManager) app.getSystemService(Context.USER_SERVICE)).isUserUnlocked()) {
            DirectBoot.flushTrafficStats();
        }
        if (DataStore.tcpFastOpen && !TcpFastOpen.sendEnabled) {
            TcpFastOpen.enableTimeout();
        }
        if (DataStore.publicStore.getLong(Key.assetUpdateTime, -1) != packageInfo.lastUpdateTime) {
            try {
                AssetManager assetManager = app.getAssets();
                for (String file : assetManager.list("acl")) {
                    try (InputStream input = assetManager.open("acl/" + file)) {
                        try (OutputStream output = new FileOutputStream(new File(((Context) deviceStorage).getNoBackupFilesDir(), file))) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = input.read(buffer, 0, 8192)) >= 0) {
                                output.write(buffer, 0, bytesRead);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                printLog(e);
            }
            DataStore.publicStore.putLong(Key.assetUpdateTime, packageInfo.lastUpdateTime);
        }
        updateNotificationChannels();
    }

    public static void updateNotificationChannels() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannels(Arrays.asList(
                    new NotificationChannel("service-vpn", app.getText(R.string.service_vpn),
                            NotificationManager.IMPORTANCE_LOW),
                    new NotificationChannel("service-proxy", app.getText(R.string.service_proxy),
                            NotificationManager.IMPORTANCE_LOW),
                    new NotificationChannel("service-transproxy", app.getText(R.string.service_transproxy),
                            NotificationManager.IMPORTANCE_LOW)));
            nm.deleteNotificationChannel("service-nat");
        }
    }

    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder().build();
    }

    public static PackageInfo getPackageInfo(String packageName) throws PackageManager.NameNotFoundException {
        return app.getPackageManager().getPackageInfo(packageName,
                (Build.VERSION.SDK_INT >= 28) ? PackageManager.GET_SIGNING_CERTIFICATES :
                        PackageManager.GET_SIGNATURES);
    }

    public static void startService() {
        ContextCompat.startForegroundService(app, new Intent(app, ShadowsocksConnection.serviceClass));
    }

    public static void reloadService() {
        app.sendBroadcast(new Intent(Action.RELOAD));
    }

    public static void stopService() {
        app.sendBroadcast(new Intent(Action.CLOSE));
    }

    public static BroadcastReceiver listenForPackageChanges(boolean onetime, Runnable callback) {
        return new BroadcastReceiver() {
            {
                app.registerReceiver(this, new IntentFilter() {{
                    addAction(Intent.ACTION_PACKAGE_ADDED);
                    addAction(Intent.ACTION_PACKAGE_REMOVED);
                    addDataScheme("package");
                }});
            }

            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    return;
                }
                callback.run();
                if (onetime) {
                    app.unregisterReceiver(this);
                }
            }
        };
    }
}