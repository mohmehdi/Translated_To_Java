

package com.github.shadowsocks.bg;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class ProxyService extends Service implements BaseService.Interface {

    public ProxyService() {
        BaseService.register(this);
    }

    @Override
    public String getTag() {
        return "ShadowsocksProxyService";
    }

    @Override
    public ServiceNotification createNotification() {
        return new ServiceNotification(this, data.getProfile().getFormattedName(), "service-proxy", true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.<BaseService.Interface>onStartCommand(intent, flags, startId);
    }
}