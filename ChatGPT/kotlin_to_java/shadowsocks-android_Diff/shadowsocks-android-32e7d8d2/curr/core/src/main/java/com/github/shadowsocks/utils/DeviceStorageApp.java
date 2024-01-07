package com.github.shadowsocks.utils;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import androidx.work.Configuration;

@SuppressLint("Registered")
public class DeviceStorageApp extends Application implements Configuration.Provider {
    public DeviceStorageApp(Context context) {
        attachBaseContext(Build.VERSION.SDK_INT >= 24 ? context.createDeviceProtectedStorageContext() : context);
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }

    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder().build();
    }
}