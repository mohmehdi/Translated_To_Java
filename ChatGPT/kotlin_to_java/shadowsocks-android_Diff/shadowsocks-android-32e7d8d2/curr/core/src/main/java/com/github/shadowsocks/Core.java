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

import com.crashlytics.android.Crashlytics;
import com.github.shadowsocks.acl.Acl;
import com.github.shadowsocks.aidl.ShadowsocksConnection;
import com.github.shadowsocks.core.R;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.github.shadowsocks.net.TcpFastOpen;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.DeviceStorageApp;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.File;
import java.io.IOException;
import kotlin.reflect.KClass;
import io.fabric.sdk.android.Fabric;

public class Core {
    public static final String TAG = "Core";

    public static Application app;
    public static (Context) -> PendingIntent configureIntent;
    public static ConnectivityManager connectivity;
    public static PackageInfo packageInfo;
    public static DeviceStorageApp deviceStorage;
    public static FirebaseAnalytics analytics;
    public static boolean directBootSupported;

    public static List<Long> getActiveProfileIds() {
        Profile profile = ProfileManager.getProfile(DataStore.profileId);
        if (profile == null) {
            return Collections.emptyList();
        } else {
            List<Long> activeProfileIds = new ArrayList<>();
            activeProfileIds.add(profile.id);
            if (profile.udpFallback != null) {
                activeProfileIds.add(profile.udpFallback);
            }
            return activeProfileIds;
        }
    }

    public static Pair<Profile, Profile> getCurrentProfile() {
        if (DataStore.directBootAware) {
            Profile deviceProfile = DirectBoot.getDeviceProfile();
            if (deviceProfile != null) {
                return new Pair<>(deviceProfile, null);
            }
        }
        Profile profile = ProfileManager.getProfile(DataStore.profileId);
        if (profile != null) {
            return ProfileManager.expand(profile);
        } else {
            return null;
        }
    }

    public static Profile switchProfile(long id) {
        Profile result = ProfileManager.getProfile(id);
        if (result == null) {
            result = ProfileManager.createProfile();
        }
        DataStore.profileId = result.id;
        return result;
    }

    public static void init(Application app, KClass configureClass) {
        Core.app = app;
        Core.configureIntent = (Context context) -> {
            return PendingIntent.getActivity(context, 0, new Intent(context, configureClass.getJavaClass())
                    .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT), 0);
        };

        if (Build.VERSION.SDK_INT >= 24) {
            deviceStorage.moveDatabaseFrom(app, Key.DB_PUBLIC);
            File old = Acl.getFile(Acl.CUSTOM_RULES, app);
            if (old.canRead()) {
                Acl.getFile(Acl.CUSTOM_RULES).writeText(old.readText());
                old.delete();
            }
        }

        System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON);
        Fabric.with(deviceStorage, Crashlytics());
        FirebaseApp.initializeApp(deviceStorage);

        if (Build.VERSION.SDK_INT >= 24 && DataStore.directBootAware &&
                app.getSystemService(UserManager.class).isUserUnlocked()) {
            DirectBoot.flushTrafficStats();
        }
        if (DataStore.tcpFastOpen && !TcpFastOpen.sendEnabled) {
            TcpFastOpen.enableTimeout();
        }
        if (DataStore.publicStore.getLong(Key.assetUpdateTime, -1) != packageInfo.lastUpdateTime) {
            AssetManager assetManager = app.getAssets();
            try {
                String[] files = assetManager.list("acl");
                for (String file : files) {
                    InputStream input = assetManager.open("acl/" + file);
                    OutputStream output = new FileOutputStream(new File(deviceStorage.noBackupFilesDir, file));
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = input.read(buffer)) > 0) {
                        output.write(buffer, 0, length);
                    }
                    output.close();
                    input.close();
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
            NotificationManager nm = app.getSystemService(NotificationManager.class);
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

    public static PackageInfo getPackageInfo(String packageName) {
        PackageManager packageManager = app.getPackageManager();
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                return packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            } else {
                return packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
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
        BroadcastReceiver receiver = new BroadcastReceiver() {
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
        app.registerReceiver(receiver, new IntentFilter(Intent.ACTION_PACKAGE_ADDED));
        app.registerReceiver(receiver, new IntentFilter(Intent.ACTION_PACKAGE_REMOVED));
        app.registerReceiver(receiver, new IntentFilter(Intent.ACTION_PACKAGE_REPLACED));
        return receiver;
    }
}