

package com.github.shadowsocks;


public class App extends Application {
    public static App app;
    public static final String TAG = "ShadowsocksApplication";

    public static Locale SIMPLIFIED_CHINESE;
    public static Locale TRADITIONAL_CHINESE;

    public Handler handler;
    public FirebaseRemoteConfig remoteConfig;
    public Tracker tracker;
    public PackageInfo info;

    static {
        if (Build.VERSION.SDK_INT >= 21) {
            SIMPLIFIED_CHINESE = Locale.forLanguageTag("zh-Hans-CN");
            TRADITIONAL_CHINESE = Locale.forLanguageTag("zh-Hant-TW");
        } else {
            SIMPLIFIED_CHINESE = Locale.SIMPLIFIED_CHINESE;
            TRADITIONAL_CHINESE = Locale.TRADITIONAL_CHINESE;
        }
    }

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
        remoteConfig.fetch().addOnCompleteListener(task -> {
            if (task.isSuccessful()) remoteConfig.activateFetched();
            else Log.e(TAG, "Failed to fetch config");
        });

        JobManager.create(this).addJobCreator(new AclSyncJob());
        PreferenceFragmentCompat.registerPreferenceFragment(IconListPreference.class, BottomSheetPreferenceDialogFragment.class);

        TcpFastOpen.enabled(DataStore.getBoolean(Key.tfo, TcpFastOpen.sendEnabled));

        long assetUpdateTime = DataStore.getLong(Key.assetUpdateTime, -1);
        if (assetUpdateTime != info.lastUpdateTime) {
            AssetManager assetManager = getAssets();
            for (String dir : new String[]{"acl", "overture"}) {
                try {
                    for (String file : assetManager.list(dir)) {
                        AssetFileDescriptor afd = assetManager.openFd(dir + '/' + file);
                        File outputFile = new File(getFilesDir(), file);
                        FileOutputStream outputStream = new FileOutputStream(outputFile);
                        byte[] buffer = new byte[1024];
                        int read;
                        while ((read = afd.getFileDescriptor().read(buffer)) != -1) {
                            outputStream.write(buffer, 0, read);
                        }
                        outputStream.close();
                        afd.getFileDescriptor().sync();
                        afd.getFileDescriptor().close();
                    }
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                    app.track(e);
                }
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
                            NotificationManager.IMPORTANCE_LOW)
            ));
            nm.deleteNotificationChannel("service-nat");
        }
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
        return ProfileManager.getProfile(DataStore.profileId);
    }

    public Profile switchProfile(int id) {
        Profile result = ProfileManager.getProfile(id);
        if (result == null) result = ProfileManager.createProfile();
        DataStore.profileId = result.id;
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
                .setDescription(StandardExceptionParser.getInstance().getDescription(thread.getName(), t))
                .setFatal(false)
                .build());
        t.printStackTrace();
    }

    public static void checkChineseLocale(Configuration config) {
            Locale check(Locale locale) {
                if (!"zh".equals(locale.getLanguage())) {
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
                    default:
                        Log.w(TAG, "Unknown zh locale: " + locale + ". Falling back to zh-Hans-CN...");
                }
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
                    resources.updateConfiguration(newConfig, resources.getDisplayMetrics());
                }
            } else {
                Locale newLocale = check(config.locale);
                if (newLocale != null) {
                    Configuration newConfig = new Configuration(config);
                    newConfig.locale = newLocale;
                    resources.updateConfiguration(newConfig, resources.getDisplayMetrics());
                }
            }
        }

    public PackageChangeListener(Context context, Runnable callback) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addDataScheme("package");

            receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction() != Intent.ACTION_PACKAGE_REMOVED ||
                            !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        callback.run();
                    }
                }
            };

            context.registerReceiver(receiver, filter);
        }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        checkChineseLocale(newConfig);
    }
}