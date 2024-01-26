package com.github.shadowsocks;

import android.app.Application;
import android.content.res.Configuration;
import androidx.appcompat.app.AppCompatDelegate;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Core.init(this, MainActivity.class);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Core.updateNotificationChannels();
    }
}