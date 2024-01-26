package com.github.shadowsocks;

import android.app.Application;
import android.content.res.Configuration;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.work.Configuration;

public class App extends Application implements Configuration.Provider {
    @Override
    public void onCreate() {
        super.onCreate();
        Core.INSTANCE.init(this, MainActivity.class);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Core.INSTANCE.updateNotificationChannels();
    }
}