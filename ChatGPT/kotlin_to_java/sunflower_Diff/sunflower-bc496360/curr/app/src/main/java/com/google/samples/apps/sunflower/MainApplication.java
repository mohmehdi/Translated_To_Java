
package com.google.samples.apps.sunflower;

import android.app.Application;
import androidx.work.Configuration;
import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class MainApplication extends Application implements Configuration.Provider {
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setMinimumLoggingLevel(BuildConfig.DEBUG ? android.util.Log.DEBUG : android.util.Log.ERROR)
                .build();
    }
}
