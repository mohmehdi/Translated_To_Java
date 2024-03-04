package com.github.shadowsocks;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.LocaleList;
import android.os.Looper;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatDelegate;
import android.util.Log;

import com.evernote.android.job.JobManager;
import com.github.shadowsocks.acl.AclSyncJob;
import com.github.shadowsocks.bg.BaseService;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.github.shadowsocks.preference.BottomSheetPreferenceDialogFragment;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.preference.IconListPreference;
import com.github.shadowsocks.utils.Action;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.TcpFastOpen;
import com.github.shadowsocks.utils.broadcastReceiver;
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
import java.util.Locale;

public class App extends Application {

    private static App app;
    private static final String TAG = "ShadowsocksApplication";

    private static final Locale SIMPLIFIED_CHINESE = Build.VERSION.SDK_INT >= 21 ?
            Locale.forLanguageTag("zh-Hans-CN") : Locale.SIMPLIFIED_CHINESE;

    private static final Locale TRADITIONAL_CHINESE = Build.VERSION.SDK_INT >= 21 ?
            Locale.forLanguageTag("zh-Hant-TW") : Locale.TRADITIONAL_CHINESE;

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
    private static final Tracker tracker = GoogleAnalytics.getInstance(app).newTracker(R.xml.tracker);
    private static final PackageInfo info = app.getPackageManager().getPackageInfo(app.getPackageName(), PackageManager.GET_SIGNATURES);

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
        return ProfileManager.getProfile(DataStore.getProfileId());
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

    private void checkChineseLocale(Configuration config) {
        Locale check(Locale locale) {
            if (!locale.getLanguage().equals("zh")) {
                return null;
            }
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
                        Log.w(TAG, "Unknown zh locale script: " + locale.getScript() + ". Falling back to trying countries...");
                }
            }
            switch (locale.getCountry()) {
                case "SG":
                    return SIMPLIFIED_CHINESE;
                case "HK":
                case "MO":
                    return TRADITIONAL_CHINESE;
            }
            Log.w(TAG, "Unknown zh locale: " + locale + ". Falling back to zh-Hans-CN...");
            return SIMPLIFIED_CHINESE;
        }
        if (Build.VERSION.SDK_INT >= 24) {
            LocaleList localeList = config.getLocales();
            boolean changed = false;
            Locale[] newList = new Locale[localeList.size()];
            for (int i = 0; i < localeList.size(); i++) {
                Locale locale = localeList.get(i);
                Locale newLocale = check(locale);
                if (newLocale == null) {
                    newList[i] = locale;
                } else {
                    changed = true;
                    newList[i] = newLocale;
                }
            }
            if (changed) {
                Configuration newConfig = new Configuration(config);
                newConfig.setLocales(new LocaleList(newList));
                getResources().updateConfiguration(newConfig, getResources().getDisplayMetrics());
            }
        } else {
            Locale newLocale = check(config.locale);
            if (newLocale != null) {
                Configuration newConfig = new Configuration(config);
                newConfig.locale = newLocale;
                getResources().updateConfiguration(newConfig, getResources().getDisplayMetrics());
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;
        if (!BuildConfig.DEBUG) {
            System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "ERROR");
        }
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        checkChineseLocale(getResources().getConfiguration());

        FirebaseApp.initializeApp(this);
        remoteConfig = FirebaseRemoteConfig.getInstance();
        remoteConfig.setDefaults(R.xml.default_configs);
        remoteConfig.fetch().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                remoteConfig.activateFetched();
            } else {
                Log.e(TAG, "Failed to fetch config");
            }
        });

        JobManager.create(this).addJobCreator(AclSyncJob.INSTANCE);
        PreferenceFragmentCompat.registerPreferenceFragment(IconListPreference.class,
                BottomSheetPreferenceDialogFragment.class);

        TcpFastOpen.enabled(DataStore.getBoolean(Key.tfo, TcpFastOpen.sendEnabled));

        if (DataStore.getLong(Key.assetUpdateTime, -1) != info.lastUpdateTime) {
            try {
                for (String dir : new String[]{"acl", "overture"}) {
                    String[] files = getAssets().list(dir);
                    for (String file : files) {
                        try {
                            File outputFile = new File(getFilesDir(), file);
                            File inputFile = new File(dir + '/' + file);
                            InputStream input = getAssets().open(inputFile);
                            OutputStream output = new FileOutputStream(outputFile);
                            byte[] buffer = new byte[1024];
                            int length;
                            while ((length = input.read(buffer)) > 0) {
                                output.write(buffer, 0, length);
                            }
                            output.flush();
                            output.close();
                            input.close();
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
            DataStore.putLong(Key.assetUpdateTime, info.lastUpdateTime);
        }

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

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        checkChineseLocale(newConfig);
    }

    public BroadcastReceiver listenForPackageChanges(Runnable callback) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        BroadcastReceiver result = broadcastReceiver((context, intent) -> {
            if (intent.getAction() != Intent.ACTION_PACKAGE_REMOVED ||
                    !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                callback.run();
            }
        });
        app.registerReceiver(result, filter);
        return result;
    }
}