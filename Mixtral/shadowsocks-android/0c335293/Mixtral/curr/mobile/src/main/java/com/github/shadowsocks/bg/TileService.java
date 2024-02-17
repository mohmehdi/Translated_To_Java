

package com.github.shadowsocks.bg;

import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.support.annotation.RequiresApi;
import com.github.shadowsocks.App;
import com.github.shadowsocks.R;
import com.github.shadowsocks.ShadowsocksConnection;
import com.github.shadowsocks.aidl.IShadowsocksService;
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;

@RequiresApi(24)
public class TileService extends TileService implements ShadowsocksConnection.Interface {
    private Icon iconIdle;
    private Icon iconBusy;
    private Icon iconConnected;
    private KeyguardManager keyguard;

    {
        iconIdle = Icon.createWithResource(this, R.drawable.ic_service_idle).mutate();
        iconIdle.setTint(0x79ffffff);
        iconBusy = Icon.createWithResource(this, R.drawable.ic_service_busy);
        iconConnected = Icon.createWithResource(this, R.drawable.ic_service_active);
        keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
    }

    @Override
    public IShadowsocksServiceCallback.Stub serviceCallback = new IShadowsocksServiceCallback.Stub() {
        @Override
        public void stateChanged(int state, String profileName, String msg) {
            Tile tile = qsTile;
            if (tile == null) {
                return;
            }
            String label = null;
            switch (state) {
                case BaseService.STOPPED:
                    tile.setIcon(iconIdle);
                    tile.setState(Tile.STATE_INACTIVE);
                    break;
                case BaseService.CONNECTED:
                    tile.setIcon(iconConnected);
                    if (!keyguard.isDeviceLocked) {
                        label = profileName;
                    }
                    tile.setState(Tile.STATE_ACTIVE);
                    break;
                default:
                    tile.setIcon(iconBusy);
                    tile.setState(Tile.STATE_UNAVAILABLE);
            }
            tile.setLabel(label != null ? label : getString(R.string.app_name));
            tile.updateTile();
        }

        @Override
        public void trafficUpdated(int profileId, long txRate, long rxRate, long txTotal, long rxTotal) {
        }

        @Override
        public void trafficPersisted(int profileId) {
        }
    };

    @Override
    public void onServiceConnected(IShadowsocksService service) {
        serviceCallback.stateChanged(service.state, service.profileName, null);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        connection.connect();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        connection.disconnect();
    }

    @Override
    public void onClick() {
        if (isLocked()) {
            unlockAndRun(this::toggle);
        } else {
            toggle();
        }
    }

    private void toggle() {
        IShadowsocksService service = connection.service;
        if (service == null) {
            return;
        }
        switch (service.state) {
            case BaseService.STOPPED:
                App.getApp().startService();
                break;
            case BaseService.CONNECTED:
                App.getApp().stopService();
                break;
        }
    }
}