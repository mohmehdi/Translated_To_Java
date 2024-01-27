package com.github.shadowsocks;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ShortcutManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v4.content.pm.ShortcutInfoCompat;
import android.support.v4.content.pm.ShortcutManagerCompat;
import android.support.v4.graphics.drawable.IconCompat;
import com.github.shadowsocks.App;
import com.github.shadowsocks.aidl.IShadowsocksService;
import com.github.shadowsocks.bg.BaseService;

public class QuickToggleShortcut
  extends Activity
  implements ShadowsocksConnection.Interface {

  @Override
  public void onCreate(
    Bundle savedInstanceState,
    PersistableBundle persistentState
  ) {
    super.onCreate(savedInstanceState, persistentState);
    if (intent.getAction().equals(Intent.ACTION_CREATE_SHORTCUT)) {
      setResult(
        Activity.RESULT_OK,
        ShortcutManagerCompat.createShortcutResultIntent(
          this,
          ShortcutInfoCompat
            .Builder(this, "toggle")
            .setIntent(
              new Intent(this, QuickToggleShortcut.class)
                .setAction(Intent.ACTION_MAIN)
            )
            .setIcon(
              IconCompat.createWithResource(
                this,
                R.drawable.ic_qu_shadowsocks_launcher
              )
            )
            .setShortLabel(getString(R.string.quick_toggle))
            .build()
        )
      );
      finish();
    } else {
      connection.connect();
      if (Build.VERSION.SDK_INT >= 25) {
        ShortcutManager shortcutManager = (ShortcutManager) getSystemService(
          SHORTCUT_SERVICE
        );
        if (shortcutManager != null) {
          shortcutManager.reportShortcutUsed("toggle");
        }
      }
    }
  }

  @Override
  public void onServiceConnected(IShadowsocksService service) {
    switch (service.state) {
      case BaseService.STOPPED:
        App.app.startService();
        break;
      case BaseService.CONNECTED:
        App.app.stopService();
        break;
    }
    finish();
  }

  @Override
  protected void onDestroy() {
    connection.disconnect();
    super.onDestroy();
  }
}
