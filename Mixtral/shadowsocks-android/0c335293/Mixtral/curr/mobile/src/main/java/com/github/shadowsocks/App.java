

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
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatDelegate;
import android.text.TextUtils;
import android.util.Log;

import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.support.SupportJobScheduler;
import com.github.shadowsocks.acl.Acl;
import com.github.shadowsocks.acl.AclSyncJob;
import com.github.shadowsocks.bg.BaseService;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.github.shadowsocks.preference.BottomSheetPreferenceDialogFragment;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.preference.IconListPreference;
import com.github.shadowsocks.preference.PreferenceFragmentCompat;
import com.github.shadowsocks.utils.DirectBoot;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.TcpFastOpen;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.StandardExceptionParser;
import com.google.android.gms.analytics.Tracker;
import com.google.firebase.FirebaseApp;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasAndroidInjector;

@Singleton
public class App extends Application implements HasAndroidInjector {
    private static final String TAG = "ShadowsocksApplication";
    private static App app;
    private Handler handler;
    private Context deviceContext;
    private FirebaseRemoteConfig remoteConfig;
    private Tracker tracker;
    private PackageInfo info;


    @Override
    public void onCreate() {
        super.onCreate();
        app = this;
        handler = new Handler(Looper.getMainLooper());
        if (!BuildConfig.DEBUG) System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "ERROR");
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        PreferenceFragmentCompat.registerPreferenceFragment(IconListPreference.class.getName(),
                BottomSheetPreferenceDialogFragment.class.getName());

        if (Build.VERSION.SDK_INT >= 24) {
            deviceContext = new DeviceContext(this);
            deviceContext.moveDatabaseFrom(this, Key.DB_PUBLIC);
            deviceContext.moveDatabaseFrom(this, JobConstants.DATABASE_NAME);
            deviceContext.moveSharedPreferencesFrom(this, JobConstants.PREF_FILE_NAME);
            File old = Acl.getFile(Acl.CUSTOM_RULES, this);
            if (old.canRead()) {
                Acl.getFile(Acl.CUSTOM_RULES, this).writeText(old.readText());
                old.delete();
            }
        }

        FirebaseApp.initializeApp(this);
        remoteConfig = FirebaseRemoteConfig.getInstance();
        remoteConfig.setDefaults(R.xml.default_configs);
        remoteConfig.fetch(TimeUnit.MINUTES.toSeconds(1)).addOnCompleteListener(task -> {
            if (task.isSuccessful()) remoteConfig.activateFetched();
            else Log.e(TAG, "Failed to fetch config");
        });
        JobManager.create(this).addJobCreator(new AclSyncJob());

        if (DataStore.directBootAware && UserManagerCompat.isUserUnlocked(this)) DirectBoot.update();
        TcpFastOpen.enabled(DataStore.publicStore.getBoolean(Key.tfo, TcpFastOpen.sendEnabled));
        if (DataStore.publicStore.getLong(Key.assetUpdateTime, -1) != info.lastUpdateTime) {
            for (String dir : new String[]{"acl", "overture"}) {
                try (InputStream in = getAssets().open(dir + '/');
                     FileOutputStream out = openFileOutput(dir + '/' + file, Context.MODE_PRIVATE)) {
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                    track(e);
                }
            }
            DataStore.publicStore.putLong(Key.assetUpdateTime, info.lastUpdateTime);
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
                            NotificationManager.IMPORTANCE_LOW)));
            nm.deleteNotificationChannel("service-nat");
        }
    }
    BroadcastReceiver listenForPackageChanges(final Callback callback) {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");

        final BroadcastReceiver result = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != Intent.ACTION_PACKAGE_REMOVED ||
                        !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    callback.onCallback();
                }
            }
        };

        app.registerReceiver(result, filter);
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

    public void startService() {
        Intent intent = new Intent(this, BaseService.serviceClass);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
        else startService(intent);
    }

    public void reloadService() {
        sendBroadcast(new Intent(Action.RELOAD));
    }

    public void stopService() {
        sendBroadcast(new Intent(Action.CLOSE));
    }

    public Profile currentProfile() {
        if (DataStore.directBootAware) return DirectBoot.getDeviceProfile();
        else return ProfileManager.getProfile(DataStore.profileId);
    }

    public Profile switchProfile(int id) {
        Profile result = ProfileManager.getProfile(id);
        if (result == null) result = ProfileManager.createProfile();
        DataStore.profileId = result.id;
        return result;
    }

   
}