package com.github.shadowsocks;

import android.app.Application;
import android.content.res.Configuration;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.work.Configuration;

public class App extends Application implements Configuration.Provider {

  private Core core;

  @Override
  public void onCreate() {
    super.onCreate();
    core = new Core();
    core.init(this, MainActivity.class);
    AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    Core.updateNotificationChannels();
  }
}
