package com.github.shadowsocks;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.StandardExceptionParser;
import com.google.android.gms.analytics.Tracker;
import com.google.firebase.FirebaseApp;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.j256.ormlite.logger.LocalLog;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class App extends Application {
    public static App app;
    public static final String TAG = "ShadowsocksApplication";

    // The ones in Locale doesn't have script included
    private static final Locale SIMPLIFIED_CHINESE = (Build.VERSION.SDK_INT >= 21) ? Locale.forLanguageTag("zh-Hans-CN") : Locale.SIMPLIFIED_CHINESE;
    private static final Locale TRADITIONAL_CHINESE = (Build.VERSION.SDK_INT >= 21) ? Locale.forLanguageTag("zh-Hant-TW") : Locale.TRADITIONAL_CHINESE;

    public Handler handler;
    public FirebaseRemoteConfig remoteConfig;
    public Tracker tracker;
    public PackageInfo info;

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;
        if (!BuildConfig.DEBUG) System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "ERROR");
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        checkChineseLocale(getResources().getConfiguration());

        FirebaseApp.initializeApp(this);
        remoteConfig = FirebaseRemoteConfig.getInstance();
        remoteConfig.setDefaults(R.xml.default_configs);
        remoteConfig.fetch(TimeUnit.MINUTES.toMillis(1)).addOnCompleteListener(task -> {
            if (task.isSuccessful()) remoteConfig.activateFetched();
            else Log.e(TAG, "Failed to fetch config");
        });

        JobManager.create(this).addJobCreator(new AclSyncJob());
        PreferenceFragmentCompat.registerPreferenceFragment(IconListPreference.class, BottomSheetPreferenceDialogFragment.class);

        TcpFastOpen.enabled(DataStore.getBoolean(Key.tfo, TcpFastOpen.sendEnabled));

        long assetUpdateTime = DataStore.getLong(Key.assetUpdateTime, -1);
        if (assetUpdateTime != info.lastUpdateTime) {
            try {
                String[] dirs = {"acl", "overture"};
                for (String dir : dirs) {
                    String[] files = getAssets().list(dir);
                    for (String file : files) {
                        try (InputStream in = getAssets().open(dir + "/" + file);
                             FileOutputStream out = openFileOutput(file, Context.MODE_PRIVATE)) {
                            byte[] buffer = new byte[1024];
                            int read;
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                            }
                            out.flush();
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                app.track(e);
            }
            DataStore.putLong(Key.assetUpdateTime, info.lastUpdateTime);
        }

        if (Build.VERSION.SDK_INT >= 26) {
            @RequiresApi(26)
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannels(Arrays.asList(
                    new NotificationChannel("service-vpn", getText(R.string.service_vpn).toString(), NotificationManager.IMPORTANCE_LOW),
                    new NotificationChannel("service-proxy", getText(R.string.service_proxy).toString(), NotificationManager.IMPORTANCE_LOW),
                    new NotificationChannel("service-transproxy", getText(R.string.service_transproxy).toString(), NotificationManager.IMPORTANCE_LOW)
            ));
            nm.deleteNotificationChannel("service-nat"); // NAT mode is gone for good
        }
    }

    public void startService() {
        Intent intent = new Intent(this, BaseService.serviceClass);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
        else startService(intent);
    }

    public void reloadService() {
        Intent intent = new Intent(Action.RELOAD);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void stopService() {
        Intent intent = new Intent(Action.CLOSE);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public Profile currentProfile() {
        return ProfileManager.getProfile(DataStore.profileId);
    }

    public Profile switchProfile(int id) {
        Profile result = ProfileManager.getProfile(id);
        if (result == null) result = ProfileManager.createProfile();
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

    private void checkChineseLocale(Configuration config) {
        Runnable check = locale -> {
            if (!"zh".equals(locale.getLanguage())) return null;
            switch (locale.getCountry()) {
                case "CN":
                case "TW":
                    return null;
            }
            if (Build.VERSION.SDK_INT >= 21) {
                switch (locale.getScript()) {
                    case "Hans":
                        return SIMPLIFIED_CHINESE;
                    case "Hant":
                        return TRADITIONAL_CHINESE;
                    default:
                        Log.w(TAG, String.format(Locale.ENGLISH, "Unknown zh locale script: %s. Falling back to trying countries...", locale.getScript()));
                }
            }
            switch (locale.getCountry()) {
                case "SG":
                    return SIMPLIFIED_CHINESE;
                case "HK":
                case "MO":
                    return TRADITIONAL_CHINESE;
            }
            Log.w(TAG, String.format(Locale.ENGLISH, "Unknown zh locale: %s. Falling back to zh-Hans-CN...", locale));
            return SIMPLIFIED_CHINESE;
        };

        if (Build.VERSION.SDK_INT >= 24) {
            @RequiresApi(24)
            LocaleList localeList = config.getLocales();
            boolean changed = false;
            Locale[] newList = new Locale[localeList.size()];
            int i = 0;
            for (Locale locale : localeList) {
                Locale newLocale = check.run(locale);
                if (newLocale == null) newList[i] = locale;
                else {
                    changed = true;
                    newList[i] = newLocale;
                }
                i++;
            }
            if (changed) {
                Configuration newConfig = new Configuration(config);
                newConfig.setLocales(new LocaleList(newList));
                getResources().updateConfiguration(newConfig, getResources().getDisplayMetrics());
            }
        } else {
            @SuppressWarnings("deprecation")
            Locale newLocale = check.run(config.locale);
            if (newLocale != null) {
                Configuration newConfig = new Configuration(config);
                @SuppressWarnings("deprecation")
                newConfig.locale = newLocale;
                getResources().updateConfiguration(newConfig, getResources().getDisplayMetrics());
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        checkChineseLocale(newConfig);
    }

    public BroadcastReceiver listenForPackageChanges(Runnable callback) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        BroadcastReceiver result = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()) &&
                        intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) callback.run();
                else callback.run();
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(result, filter);
        return result;
    }
}