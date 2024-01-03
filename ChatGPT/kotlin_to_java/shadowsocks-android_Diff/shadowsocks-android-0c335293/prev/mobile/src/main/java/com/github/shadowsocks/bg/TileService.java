package com.github.shadowsocks.bg;

import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.support.annotation.RequiresApi;
import com.github.shadowsocks.App;
import com.github.shadowsocks.R;
import com.github.shadowsocks.ShadowsocksConnection;
import com.github.shadowsocks.aidl.IShadowsocksService;
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;

@RequiresApi(api = Build.VERSION_CODES.N)
public class TileService extends TileService implements ShadowsocksConnection.Interface {
    private Icon iconIdle = Icon.createWithResource(this, R.drawable.ic_start_idle).setTint(0x79ffffff);
    private Icon iconBusy = Icon.createWithResource(this, R.drawable.ic_start_busy);
    private Icon iconConnected = Icon.createWithResource(this, R.drawable.ic_start_connected);

    @Override
    public IShadowsocksServiceCallback.Stub getServiceCallback() {
        return new IShadowsocksServiceCallback.Stub() {
            @Override
            public void stateChanged(int state, String profileName, String msg) {
                Tile tile = getQsTile();
                if (tile == null) {
                    return;
                }
                switch (state) {
                    case BaseService.STOPPED:
                        tile.setIcon(iconIdle);
                        tile.setLabel(getString(R.string.app_name));
                        tile.setState(Tile.STATE_INACTIVE);
                        break;
                    case BaseService.CONNECTED:
                        tile.setIcon(iconConnected);
                        tile.setLabel(profileName != null ? profileName : getString(R.string.app_name));
                        tile.setState(Tile.STATE_ACTIVE);
                        break;
                    default:
                        tile.setIcon(iconBusy);
                        tile.setLabel(getString(R.string.app_name));
                        tile.setState(Tile.STATE_UNAVAILABLE);
                        break;
                }
                tile.updateTile();
            }

            @Override
            public void trafficUpdated(int profileId, long txRate, long rxRate, long txTotal, long rxTotal) {
            }

            @Override
            public void trafficPersisted(int profileId) {
            }
        };
    }

    @Override
    public void onServiceConnected(IShadowsocksService service) {
        getServiceCallback().stateChanged(service.getState(), service.getProfileName(), null);
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
        IShadowsocksService service = connection.getService();
        if (service == null) {
            return;
        }
        switch (service.getState()) {
            case BaseService.STOPPED:
                App.Companion.getApp().startService();
                break;
            case BaseService.CONNECTED:
                App.Companion.getApp().stopService();
                break;
        }
    }
}