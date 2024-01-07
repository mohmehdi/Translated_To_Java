package com.github.shadowsocks.tv;

import android.app.Application;
import androidx.work.Configuration;
import com.github.shadowsocks.Core;

public class App extends Application implements Configuration.Provider {
    @Override
    public void onCreate() {
        super.onCreate();
        Core.init(this, MainActivity.class);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Core.updateNotificationChannels();
    }
}