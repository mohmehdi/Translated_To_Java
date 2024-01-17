package com.github.shadowsocks;

import android.app.ActivityManager;
import android.app.Application;
import android.app.BroadcastReceiver;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.UserManager;
import android.text.TextUtils;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.work.Configuration;
import com.crashlytics.android.Crashlytics;
import com.github.shadowsocks.acl.Acl;
import com.github.shadowsocks.aidl.ShadowsocksConnection;
import com.github.shadowsocks.core.R;
import com.github.shadowsocks.database.Profile;
import com.github.shadowsocks.database.ProfileManager;
import com.github.shadowsocks.net.TcpFastOpen;
import com.github.shadowsocks.preference.DataStore;
import com.github.shadowsocks.utils.DeviceStorageApp;
import com.github.shadowsocks.utils.Key;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import io.fabric.sdk.android.Fabric;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Core implements Configuration.Provider {

  public static final String TAG = "Core";

  public static Application app;
  public static Intent configureIntent;
  public static ConnectivityManager connectivity;
  public static PackageInfo packageInfo;
  public static DeviceStorageApp deviceStorage;
  public static FirebaseAnalytics analytics;
  public static boolean directBootSupported;

  public static List<Long> activeProfileIds;
  public static Pair<Profile, Profile> currentProfile;

  public static void init(
    Application app,
    Class<? extends Any> configureClass
  ) {
    Core.app = app;
    configureIntent =
      PendingIntent.getActivity(
        app,
        0,
        new Intent(app, configureClass)
          .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        0
      );

    if (Build.VERSION.SDK_INT >= 24) {
      deviceStorage = new DeviceStorageApp(app);
      deviceStorage.moveDatabaseFrom(app, Key.DB_PUBLIC);
      File old = Acl.getFile(Acl.CUSTOM_RULES, app);
      if (old.canRead()) {
        try (
          InputStream input = app.getAssets().open("acl/" + old.getName());
          FileOutputStream output = new FileOutputStream(
            Acl.getFile(Acl.CUSTOM_RULES)
          )
        ) {
          byte[] buffer = new byte[4096];
          int bytesRead;
          while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
          }
          old.delete();
        } catch (IOException e) {
          printLog(e);
        }
      }
    }

    System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON);
    Fabric.with(app, new Crashlytics());
    FirebaseApp.initializeApp(app);

    if (
      Build.VERSION.SDK_INT >= 24 &&
      DataStore.directBootAware &&
      app.getSystemService(Context.USER_SERVICE) != null &&
      (
        (UserManager) Objects.requireNonNull(
          app.getSystemService(Context.USER_SERVICE)
        )
      ).isUserUnlocked()
    ) {
      DirectBoot.flushTrafficStats();
    }
    if (
      DataStore.tcpFastOpen && !TcpFastOpen.sendEnabled
    ) TcpFastOpen.enableTimeout();
    if (
      DataStore.publicStore.getLong(Key.assetUpdateTime, -1) !=
      packageInfo.lastUpdateTime
    ) {
      try (
        InputStream input = app.getAssets().open("acl");
        File dir = new File(deviceStorage.getNoBackupFilesDir(), "acl")
      ) {
        if (!dir.exists()) dir.mkdir();
        List<String> files = Arrays.asList(
          Objects.requireNonNull(input.list())
        );
        for (String file : files) {
          try (
            InputStream assetInput = app.getAssets().open("acl/" + file);
            FileOutputStream assetOutput = new FileOutputStream(
              new File(dir, file)
            )
          ) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = assetInput.read(buffer)) != -1) {
              assetOutput.write(buffer, 0, bytesRead);
            }
          }
        }
      } catch (IOException e) {
        printLog(e);
      }
      DataStore.publicStore.putLong(
        Key.assetUpdateTime,
        packageInfo.lastUpdateTime
      );
    }
    updateNotificationChannels();
  }

  public static void updateNotificationChannels() {
    if (Build.VERSION.SDK_INT >= 26) {
      @RequiresApi(26)
      NotificationManager nm = app.getSystemService(
        Context.NOTIFICATION_SERVICE
      );
      nm.createNotificationChannels(
        Arrays.asList(
          new NotificationChannel(
            "service-vpn",
            app.getString(R.string.service_vpn),
            NotificationManager.IMPORTANCE_LOW
          ),
          new NotificationChannel(
            "service-proxy",
            app.getString(R.string.service_proxy),
            NotificationManager.IMPORTANCE_LOW
          ),
          new NotificationChannel(
            "service-transproxy",
            app.getString(R.string.service_transproxy),
            NotificationManager.IMPORTANCE_LOW
          )
        )
      );
      nm.deleteNotificationChannel("service-nat");
    }
  }

  @Override
  public Configuration getWorkManagerConfiguration() {
    return new Configuration.Builder().build();
  }

  public static PackageInfo getPackageInfo(String packageName) {
    return app
      .getPackageManager()
      .getPackageInfo(
        packageName,
        Build.VERSION.SDK_INT >= 28
          ? PackageManager.GET_SIGNING_CERTIFICATES
          : PackageManager.GET_SIGNATURES
      );
  }

  public static void startService() {
    ContextCompat.startForegroundService(
      app,
      new Intent(app, ShadowsocksConnection.serviceClass)
    );
  }

  public static void reloadService() {
    app.sendBroadcast(new Intent(Action.RELOAD));
  }

  public static void stopService() {
    app.sendBroadcast(new Intent(Action.CLOSE));
  }

  public static BroadcastReceiver listenForPackageChanges(
    boolean onetime,
    Callback callback
  ) {
    BroadcastReceiver receiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
        callback.callback();
        if (onetime) {
          context.unregisterReceiver(this);
        }
      }
    };
    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_PACKAGE_ADDED);
    filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
    filter.addDataScheme("package");
    app.registerReceiver(receiver, filter);
    return receiver;
  }
}
