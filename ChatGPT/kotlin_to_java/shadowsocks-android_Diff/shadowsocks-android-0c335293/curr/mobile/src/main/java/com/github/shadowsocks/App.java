package com.github.shadowsocks;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.RequiresApi;
import android.support.v4.os.UserManagerCompat;
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
import com.github.shadowsocks.utils.DeviceContext;
import com.github.shadowsocks.utils.DirectBoot;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.TcpFastOpen;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.StandardExceptionParser;
import com.google.android.gms.analytics.Tracker;
import com.google.firebase.FirebaseApp;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.j256.ormlite.logger.LocalLog;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;

import java.io.File;
import java.io.IOException;

public class App extends Application {
    private static App app;
    private static final String TAG = "ShadowsocksApplication";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Context deviceContext = Build.VERSION.SDK_INT < 24 ? this : new DeviceContext(this);
    private final FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
    private final Tracker tracker = GoogleAnalytics.getInstance(deviceContext).newTracker(R.xml.tracker);
    private final PackageInfo info;

    public App() throws PackageManager.NameNotFoundException {
        info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
    }

    public static App getApp() {
        return app;
    }

    public Handler getHandler() {
        return handler;
    }

    public Context getDeviceContext() {
        return deviceContext;
    }

    public FirebaseRemoteConfig getRemoteConfig() {
        return remoteConfig;
    }

    public Tracker getTracker() {
        return tracker;
    }

    public PackageInfo getInfo() {
        return info;
    }

    public void startService() {
        Intent intent = new Intent(this, BaseService.getServiceClass());
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    public void reloadService() {
        sendBroadcast(new Intent(Action.RELOAD));
    }

    public void stopService() {
        sendBroadcast(new Intent(Action.CLOSE));
    }

    public Profile getCurrentProfile() {
        if (DataStore.directBootAware()) {
            return DirectBoot.getDeviceProfile();
        } else {
            return ProfileManager.getProfile(DataStore.getProfileId());
        }
    }

    public Profile switchProfile(int id) {
        Profile result = ProfileManager.getProfile(id);
        if (result == null) {
            result = ProfileManager.createProfile();
        }
        DataStore.setProfileId(result.getId());
        return result;
    }

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
        if (!BuildConfig.DEBUG) {
            System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "ERROR");
        }
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        PreferenceFragmentCompat.registerPreferenceFragment(IconListPreference.class,
                BottomSheetPreferenceDialogFragment.class);

        if (Build.VERSION.SDK_INT >= 24) {
            deviceContext.moveDatabaseFrom(this, Key.DB_PUBLIC);
            deviceContext.moveDatabaseFrom(this, JobConstants.DATABASE_NAME);
            deviceContext.moveSharedPreferencesFrom(this, JobConstants.PREF_FILE_NAME);
            File old = Acl.getFile(Acl.CUSTOM_RULES, this);
            if (old.canRead()) {
                Acl.getFile(Acl.CUSTOM_RULES).writeText(old.readText());
                old.delete();
            }
        }

        FirebaseApp.initializeApp(deviceContext);
        remoteConfig.setDefaults(R.xml.default_configs);
        remoteConfig.fetch().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                remoteConfig.activateFetched();
            } else {
                Log.e(TAG, "Failed to fetch config");
            }
        });
        JobManager.create(deviceContext).addJobCreator(AclSyncJob.INSTANCE);

        if (DataStore.directBootAware() && UserManagerCompat.isUserUnlocked(this)) {
            DirectBoot.update();
        }
        TcpFastOpen.enabled(DataStore.publicStore().getBoolean(Key.tfo, TcpFastOpen.sendEnabled()));
        if (DataStore.publicStore().getLong(Key.assetUpdateTime, -1) != info.lastUpdateTime) {
            try {
                String[] dirs = {"acl", "overture"};
                for (String dir : dirs) {
                    String[] files = getAssets().list(dir);
                    for (String file : files) {
                        try {
                            File outputFile = new File(deviceContext.getFilesDir(), file);
                            File inputFile = new File(dir + '/' + file);
                            FileUtil.copyFile(inputFile, outputFile);
                        } catch (IOException e) {
                            Log.e(TAG, e.getMessage());
                            app.track(e);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                app.track(e);
            }
            DataStore.publicStore().putLong(Key.assetUpdateTime, info.lastUpdateTime);
        }

        updateNotificationChannels();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateNotificationChannels();
    }

    private void updateNotificationChannels() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannels(Arrays.asList(
                    new NotificationChannel("service-vpn", getText(R.string.service_vpn),
                            NotificationManager.IMPORTANCE_LOW),
                    new NotificationChannel("service-proxy", getText(R.string.service_proxy),
                            NotificationManager.IMPORTANCE_LOW),
                    new NotificationChannel("service-transproxy", getText(R.string.service_transproxy),
                            NotificationManager.IMPORTANCE_LOW)
            ));
            nm.deleteNotificationChannel("service-nat");
        }
    }

    public BroadcastReceiver listenForPackageChanges(Runnable callback) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        BroadcastReceiver result = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != Intent.ACTION_PACKAGE_REMOVED ||
                        !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    callback.run();
                }
            }
        };
        app.registerReceiver(result, filter);
        return result;
    }
}