package com.github.shadowsocks.bg;

import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.support.annotation.RequiresApi;
import com.github.shadowsocks.App.Companion.app;
import com.github.shadowsocks.R;
import com.github.shadowsocks.ShadowsocksConnection;
import com.github.shadowsocks.aidl.IShadowsocksService;
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;
import android.service.quicksettings.TileService as BaseTileService;

@RequiresApi(24)
public class TileService extends BaseTileService implements ShadowsocksConnection.Interface {
    private final Icon iconIdle = Icon.createWithResource(this, R.drawable.ic_service_idle).setTint(0x79ffffff);
    private final Icon iconBusy = Icon.createWithResource(this, R.drawable.ic_service_busy);
    private final Icon iconConnected = Icon.createWithResource(this, R.drawable.ic_service_active);
    private final KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

    private final IShadowsocksServiceCallback.Stub serviceCallback = new IShadowsocksServiceCallback.Stub() {
        @Override
        public void stateChanged(int state, String profileName, String msg) {
            Tile tile = qsTile;
            if (tile == null) return;
            String label = null;
            switch (state) {
                case BaseService.STOPPED:
                    tile.setIcon(iconIdle);
                    tile.setState(Tile.STATE_INACTIVE);
                    break;
                case BaseService.CONNECTED:
                    tile.setIcon(iconConnected);
                    if (!keyguard.isDeviceLocked()) label = profileName;
                    tile.setState(Tile.STATE_ACTIVE);
                    break;
                default:
                    tile.setIcon(iconBusy);
                    tile.setState(Tile.STATE_UNAVAILABLE);
                    break;
            }
            tile.setLabel(label != null ? label : getString(R.string.app_name));
            tile.updateTile();
        }

        @Override
        public void trafficUpdated(int profileId, long txRate, long rxRate, long txTotal, long rxTotal) { }

        @Override
        public void trafficPersisted(int profileId) { }
    };

    @Override
    public void onServiceConnected(IShadowsocksService service) {
        serviceCallback.stateChanged(service.getState(), service.getProfileName(), null);
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
        if (isLocked()) unlockAndRun(this::toggle);
        else toggle();
    }

    private void toggle() {
        IShadowsocksService service = connection.getService();
        if (service == null) return;
        switch (service.getState()) {
            case BaseService.STOPPED:
                app.startService();
                break;
            case BaseService.CONNECTED:
                app.stopService();
                break;
        }
    }
}