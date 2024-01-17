package com.github.shadowsocks.utils;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.work.Configuration;

@SuppressLint("Registered")
public class DeviceStorageApp
  extends Application
  implements Configuration.Provider {

  public DeviceStorageApp(Context context) {
    super(
      context.createDeviceProtectedStorageContext() != null
        ? context.createDeviceProtectedStorageContext()
        : context
    );
  }

  @Override
  protected Context attachBaseContext(Context newBase) {
    return super.attachBaseContext(newBase);
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
