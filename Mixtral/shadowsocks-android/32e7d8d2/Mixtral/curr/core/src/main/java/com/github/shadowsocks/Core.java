package com.github.shadowsocks;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
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
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Core {

  public static final String TAG = "Core";

  public static Application app;
  public static PendingIntent configureIntent;
  public static ConnectivityManager connectivity;
  public static PackageInfo packageInfo;
  public static DeviceStorageApp deviceStorage;
  public static FirebaseAnalytics analytics;
  public static boolean directBootSupported;

  public static List<Long> activeProfileIds;
  public static Pair<Profile, Profile> currentProfile;

  public static void switchProfile(long id) {
    Profile result = ProfileManager.getProfile(id);
    if (result == null) {
      result = ProfileManager.createProfile();
    }
    DataStore.profileId = result.getId();
  }

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

    if (Build.VERSION.SDK_INT >= 24) { // migrate old files
      deviceStorage = new DeviceStorageApp(app);
      deviceStorage.moveDatabaseFrom(app, Key.DB_PUBLIC);
      File old = Acl.getFile(Acl.CUSTOM_RULES, app);
      if (old.canRead()) {
        Acl
          .getFile(Acl.CUSTOM_RULES, app)
          .writeText(new String(Objects.requireNonNull(old.readBytes())));
        old.delete();
      }
    }

    // overhead of debug mode is minimal: https://github.com/Kotlin/kotlinx.coroutines/blob/f528898/docs/debugging.md#debug-mode
    System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON);
    Fabric.with(deviceStorage, Crashlytics.getInstance()); // multiple processes needs manual set-up
    FirebaseApp.initializeApp(deviceStorage);

    // handle data restored/crash
    if (
      Build.VERSION.SDK_INT >= 24 &&
      DataStore.directBootAware &&
      Objects
        .requireNonNull(app.getSystemService(Context.USER_SERVICE))
        .isUserUnlocked()
    ) {
      DirectBoot.flushTrafficStats();
    }
    if (DataStore.tcpFastOpen && !TcpFastOpen.sendEnabled) {
      TcpFastOpen.enableTimeout();
    }
    long assetUpdateTime = DataStore.publicStore.getLong(
      Key.assetUpdateTime,
      -1
    );
    if (assetUpdateTime != packageInfo.lastUpdateTime) {
      try {
        for (String file : Objects.requireNonNull(
          app.getAssets().list("acl")
        )) {
          File newFile = new File(deviceStorage.getNoBackupFilesDir(), file);
          newFile.getParentFile().mkdirs();
          newFile.createNewFile();
          FileProvider.getUriForFile(
            app,
            app.getPackageName() + ".provider",
            newFile
          );
          newFile
            .outputStream()
            .write(
              Objects
                .requireNonNull(app.getAssets().open("acl/" + file))
                .readBytes()
            );
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

  @RequiresApi(api = 26)
  public static void updateNotificationChannels() {
    NotificationManager nm = app.getSystemService(Context.NOTIFICATION_SERVICE);
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
    nm.deleteNotificationChannel("service-nat"); // NAT mode is gone for good
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
    LocalBroadcastManager
      .getInstance(app)
      .sendBroadcast(new Intent(Action.RELOAD));
  }

  public static void stopService() {
    app.sendBroadcast(new Intent(Action.CLOSE));
  }

  public static BroadcastReceiver listenForPackageChanges(
    boolean onetime,
    final Callback callback
  ) {
    BroadcastReceiver receiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
          return;
        }
        callback.onCallback();
        if (onetime) {
          LocalBroadcastManager.getInstance(app).unregisterReceiver(this);
        }
      }
    };
    LocalBroadcastManager
      .getInstance(app)
      .registerReceiver(
        receiver,
        new IntentFilter() {
          {
            addAction(Intent.ACTION_PACKAGE_ADDED);
            addAction(Intent.ACTION_PACKAGE_REMOVED);
            addDataScheme("package");
          }
        }
      );
    return receiver;
  }
}
