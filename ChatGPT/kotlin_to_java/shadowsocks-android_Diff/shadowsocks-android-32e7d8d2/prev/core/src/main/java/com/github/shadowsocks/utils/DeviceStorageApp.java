package com.github.shadowsocks.utils;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;

@SuppressLint("Registered")
@TargetApi(24)
public class DeviceStorageApp extends Application {
    public DeviceStorageApp(Context context) {
        attachBaseContext(context.createDeviceProtectedStorageContext());
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }
}