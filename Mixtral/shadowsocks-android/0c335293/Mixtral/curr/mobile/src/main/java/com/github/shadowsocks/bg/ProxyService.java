package com.github.shadowsocks.bg;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import com.github.shadowsocks.database.Profile;

public class ProxyService extends Service implements BaseService.Interface {
    static {
        BaseService.register(new ProxyService());
    }

    @Override
    public String getTag() {
        return "ShadowsocksProxyService";
    }

    @Override
    public ServiceNotification createNotification(String profileName) {
        return new ServiceNotification(this, profileName, "service-proxy", true);
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