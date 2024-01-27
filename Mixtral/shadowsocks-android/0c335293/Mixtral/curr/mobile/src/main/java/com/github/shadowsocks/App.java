package com.github.shadowsocks;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatDelegate;
import android.util.Log;

import com.evernote.android.job.JobConstants;
import com.evernote.android.job.JobManager;
import com.github.shadowsocks.acl.Acl;
import com.github.shadowsocks.acl.AclSyncJob;
import com.github.shadowsocks.bg.BaseService;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.github.shadowsocks.preference.BottomSheetPreferenceDialogFragment;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.preference.IconListPreference;
import com.github.shadowsocks.preference.PreferenceFragmentCompat;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.TcpFastOpen;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.StandardExceptionParser;
import com.google.android.gms.analytics.Tracker;
import com.google.firebase.FirebaseApp;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.io.File;
import java.io.IOException;

public class App extends Application {
public static App app;
public static final String TAG = "ShadowsocksApplication";
public Handler handler;
public Context deviceContext;
public FirebaseRemoteConfig remoteConfig;
public Tracker tracker;
public PackageInfo info;


public void startService() {
    Intent intent = new Intent(this, BaseService.serviceClass);
    if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
    else startService(intent);
}

public void reloadService() {
    Intent intent = new Intent(Action.RELOAD);
    sendBroadcast(intent);
}

public void stopService() {
    Intent intent = new Intent(Action.CLOSE);
    sendBroadcast(intent);
}

public Profile currentProfile {
    get {
        if (DataStore.directBootAware) {
            return DirectBoot.getDeviceProfile();
        } else {
            return ProfileManager.getProfile(DataStore.profileId);
        }
    }
}

public Profile switchProfile(int id) {
    Profile result = ProfileManager.getProfile(id);
    if (result == null) {
        result = ProfileManager.createProfile();
    }
    DataStore.profileId = result.id;
    return result;
}

// send event
public void track(String category, String action) {
    tracker.send(new HitBuilders.EventBuilder()
            .setCategory(category)
            .setAction(action)
            .setLabel(BuildConfig.VERSION_NAME)
            .build());
}

public void track(Throwable t) {
    track(Thread.currentThread(), t);
}

public void track(Thread thread, Throwable t) {
    tracker.send(new HitBuilders.ExceptionBuilder()
            .setDescription(new StandardExceptionParser(this, null).getDescription(thread.getName(), t))
            .setFatal(false)
            .build());
    t.printStackTrace();
}

@Override
public void onCreate() {
    super.onCreate();
    app = this;
    if (!BuildConfig.DEBUG) System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "ERROR");
    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    PreferenceFragmentCompat.registerPreferenceFragment(IconListPreference.class.getName(),
            BottomSheetPreferenceDialogFragment.class.getName());

    if (Build.VERSION.SDK_INT >= 24) {  // migrate old files
        deviceContext = new DeviceContext(this);
        deviceContext.moveDatabaseFrom(this, Key.DB_PUBLIC);
        deviceContext.moveDatabaseFrom(this, JobConstants.DATABASE_NAME);
        deviceContext.moveSharedPreferencesFrom(this, JobConstants.PREF_FILE_NAME);
        File old = Acl.getFile(Acl.CUSTOM_RULES, this);
        if (old.canRead()) {
            File newFile = Acl.getFile(Acl.CUSTOM_RULES);
            newFile.writeText(old.readText());
            old.delete();
        }
    }

    FirebaseApp.initializeApp(deviceContext);
    remoteConfig = FirebaseRemoteConfig.getInstance();
    remoteConfig.setDefaults(R.xml.default_configs);
    remoteConfig.fetch().addOnCompleteListener(task -> {
        if (task.isSuccessful()) {
            remoteConfig.activateFetched();
        } else {
            Log.e(TAG, "Failed to fetch config");
        }
    });
    JobManager.create(deviceContext).addJobCreator(new AclSyncJob());

    // handle data restored
    if (DataStore.directBootAware && UserManagerCompat.isUserUnlocked(this)) DirectBoot.update();
    TcpFastOpen.enabled(DataStore.publicStore.getBoolean(Key.tfo, TcpFastOpen.sendEnabled));
    long assetUpdateTime = DataStore.publicStore.getLong(Key.assetUpdateTime, -1);
    if (assetUpdateTime != info.lastUpdateTime) {
        try {
            String[] dirs = {"acl", "overture"};
            for (String dir : dirs) {
                for (String file : getAssets().list(dir)) {
                    File newFile = new File(deviceContext.getFilesDir(), file);
                    getAssets().open(dir + '/' + file).use { input ->
                        newFile.getOutputStream().use { output -> input.copyTo(output) };
                    };
                }
            }
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            app.track(e);
        }
        DataStore.publicStore.putLong(Key.assetUpdateTime, info.lastUpdateTime);
    }

    updateNotificationChannels();
}

@RequiresApi(api = Build.VERSION_CODES.O)
private void updateNotificationChannels() {
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannels(new NotificationChannel[]{
            new NotificationChannel("service-vpn", getText(R.string.service_vpn),
                    NotificationManager.IMPORTANCE_LOW),
            new NotificationChannel("service-proxy", getText(R.string.service_proxy),
                    NotificationManager.IMPORTANCE_LOW),
            new NotificationChannel("service-transproxy", getText(R.string.service_transproxy),
                    NotificationManager.IMPORTANCE_LOW)
    });
    nm.deleteNotificationChannel("service-nat"); // NAT mode is gone for good
}

public BroadcastReceiver listenForPackageChanges(Callback callback) {
    IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
    filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
    filter.addDataScheme("package");
    BroadcastReceiver result = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != Intent.ACTION_PACKAGE_REMOVED ||
                    !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                callback.onCallback();
            }
        }
    };
    LocalBroadcastManager.getInstance(this).registerReceiver(result, filter);
    return result;
}


}