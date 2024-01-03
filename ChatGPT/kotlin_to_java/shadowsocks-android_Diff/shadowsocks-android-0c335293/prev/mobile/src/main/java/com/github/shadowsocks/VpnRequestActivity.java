package com.github.shadowsocks;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.github.shadowsocks.App.Companion.app;
import com.github.shadowsocks.aidl.IShadowsocksService;
import com.github.shadowsocks.bg.BaseService;
import com.github.shadowsocks.utils.broadcastReceiver;

public class VpnRequestActivity extends AppCompatActivity implements ShadowsocksConnection.Interface {
    private static final String TAG = "VpnRequestActivity";
    private static final int REQUEST_CONNECT = 1;

    private BroadcastReceiver receiver;

    @Override
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        if (!BaseService.usingVpnMode) {
            finish();
            return;
        }
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km.inKeyguardRestrictedInputMode()) {
            receiver = broadcastReceiver((context, intent) -> connection.connect());
            registerReceiver(receiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
        } else {
            connection.connect();
        }
    }

    @Override
    public void onServiceConnected(IShadowsocksService service) {
        app.handler.postDelayed(() -> {
            Intent intent = VpnService.prepare(this);
            if (intent == null) {
                onActivityResult(REQUEST_CONNECT, RESULT_OK, null);
            } else {
                startActivityForResult(intent, REQUEST_CONNECT);
            }
        }, 1000);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            app.startService();
        } else {
            Log.e(TAG, "Failed to start VpnService");
        }
        finish();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        connection.disconnect();
        if (receiver != null) {
            unregisterReceiver(receiver);
        }
    }
}