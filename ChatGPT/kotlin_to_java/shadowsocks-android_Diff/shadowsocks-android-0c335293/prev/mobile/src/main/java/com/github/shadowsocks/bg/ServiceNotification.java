package com.github.shadowsocks.bg;

import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import com.github.shadowsocks.MainActivity;
import com.github.shadowsocks.R;
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;
import com.github.shadowsocks.utils.Action;
import com.github.shadowsocks.utils.TrafficMonitor;

import java.util.Locale;

public class ServiceNotification {
    private BaseService.Interface service;
    private String profileName;
    private String channel;
    private boolean visible;
    private KeyguardManager keyGuard;
    private NotificationManager nm;
    private IShadowsocksServiceCallback callback;
    private BroadcastReceiver lockReceiver;
    private boolean callbackRegistered;
    private NotificationCompat.Builder builder;
    private NotificationCompat.BigTextStyle style;
    private boolean isVisible;

    public ServiceNotification(BaseService.Interface service, String profileName, String channel, boolean visible) {
        this.service = service;
        this.profileName = profileName;
        this.channel = channel;
        this.visible = visible;
        this.keyGuard = (KeyguardManager) ((Context) service).getSystemService(Context.KEYGUARD_SERVICE);
        this.nm = (NotificationManager) ((Context) service).getSystemService(Context.NOTIFICATION_SERVICE);
        this.callback = new IShadowsocksServiceCallback.Stub() {
            @Override
            public void stateChanged(int state, String profileName, String msg) {
            }

            @Override
            public void trafficUpdated(int profileId, long txRate, long rxRate, long txTotal, long rxTotal) {
                service = (Context) service;
                String txr = service.getString(R.string.speed, TrafficMonitor.formatTraffic(txRate));
                String rxr = service.getString(R.string.speed, TrafficMonitor.formatTraffic(rxRate));
                builder.setContentText(txr + "↑\t" + rxr + "↓");
                style.bigText(service.getString(R.string.stat_summary).format(Locale.ENGLISH, txr, rxr,
                        TrafficMonitor.formatTraffic(txTotal), TrafficMonitor.formatTraffic(rxTotal)));
                show();
            }

            @Override
            public void trafficPersisted(int profileId) {
            }
        };
        this.lockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                update(intent.getAction());
            }
        };
        this.callbackRegistered = false;
        this.builder = new NotificationCompat.Builder((Context) service, channel)
                .setWhen(0)
                .setColor(ContextCompat.getColor((Context) service, R.color.material_primary_500))
                .setTicker(((Context) service).getString(R.string.forward_success))
                .setContentTitle(profileName)
                .setContentIntent(PendingIntent.getActivity((Context) service, 0, new Intent((Context) service, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT), 0))
                .setSmallIcon(R.drawable.ic_start_connected);
        this.style = new NotificationCompat.BigTextStyle(builder);
        this.isVisible = true;

        service = (Context) service;
        if (Build.VERSION.SDK_INT < 24) {
            builder.addAction(R.drawable.ic_navigation_close,
                    service.getString(R.string.stop), PendingIntent.getBroadcast((Context) service, 0, new Intent(Action.CLOSE), 0));
        }
        PowerManager power = (PowerManager) service.getSystemService(Context.POWER_SERVICE);
        update((Build.VERSION.SDK_INT >= 20) ? (power.isInteractive() ? Intent.ACTION_SCREEN_ON : Intent.ACTION_SCREEN_OFF) : Intent.ACTION_SCREEN_OFF, true);
        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        if (visible && Build.VERSION.SDK_INT >= 21 && Build.VERSION.SDK_INT < 26) {
            screenFilter.addAction(Intent.ACTION_USER_PRESENT);
        }
        service.registerReceiver(lockReceiver, screenFilter);
    }

    private void update(String action) {
        update(action, false);
    }

    private void update(String action, boolean forceShow) {
        if (forceShow || service.data.state == BaseService.CONNECTED) {
            switch (action) {
                case Intent.ACTION_SCREEN_OFF:
                    setVisible(visible && Build.VERSION.SDK_INT < 21, forceShow);
                    unregisterCallback();
                    break;
                case Intent.ACTION_SCREEN_ON:
                    setVisible(visible && (Build.VERSION.SDK_INT < 21 || !keyGuard.inKeyguardRestrictedInputMode()),
                            forceShow);
                    service.data.binder.registerCallback(callback);
                    service.data.binder.startListeningForBandwidth(callback);
                    callbackRegistered = true;
                    break;
                case Intent.ACTION_USER_PRESENT:
                    setVisible(true, forceShow);
                    break;
            }
        }
    }

    private void unregisterCallback() {
        if (callbackRegistered) {
            service.data.binder.unregisterCallback(callback);
            callbackRegistered = false;
        }
    }

    private void setVisible(boolean visible) {
        setVisible(visible, false);
    }

    private void setVisible(boolean visible, boolean forceShow) {
        if (isVisible != visible) {
            isVisible = visible;
            builder.setPriority(visible ? NotificationCompat.PRIORITY_LOW : NotificationCompat.PRIORITY_MIN);
            show();
        } else if (forceShow) {
            show();
        }
    }

    private void show() {
        ((Service) service).startForeground(1, builder.build());
    }

    public void destroy() {
        ((Service) service).unregisterReceiver(lockReceiver);
        unregisterCallback();
        ((Service) service).stopForeground(true);
        nm.cancel(1);
    }
}