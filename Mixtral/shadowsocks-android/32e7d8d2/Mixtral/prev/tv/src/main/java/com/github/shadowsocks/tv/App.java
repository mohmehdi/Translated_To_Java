package com.github.shadowsocks.tv;

import android.app.Application;
import android.content.res.Configuration;
import androidx.work.Configuration as WorkConfiguration;
import com.github.shadowsocks.Core;

public class App extends Application implements WorkConfiguration.Provider {

    @Override
    public void onCreate() {
        super.onCreate();
        Core.init(this, MainActivity.class);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Core.updateNotificationChannels();
    }
}