package com.github.shadowsocks;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import com.github.shadowsocks.aidl.IShadowsocksService;
import com.github.shadowsocks.aidl.IShadowsocksServiceCallback;
import com.github.shadowsocks.bg.BaseService;
import com.github.shadowsocks.utils.Action;
import java.util.*;

public class ShadowsocksConnection implements ServiceConnection {
    private static WeakHashMap<Interface, ShadowsocksConnection> connections = new WeakHashMap<>();

    private Interface instance;
    private boolean connectionActive = false;
    private boolean callbackRegistered = false;
    private IBinder binder;
    private boolean listeningForBandwidth = false;
    private IShadowsocksService service;

    public ShadowsocksConnection(Interface instance) {
        this.instance = instance;
    }

    public interface Interface extends IBinder.DeathRecipient {
        default IShadowsocksServiceCallback getServiceCallback() {
            return null;
        }

        default ShadowsocksConnection getConnection() {
            return connections.computeIfAbsent(this, k -> new ShadowsocksConnection(this));
        }

        default void onServiceConnected(IShadowsocksService service) { }

        default void onServiceDisconnected() { }

        @Override
        default void binderDied() { }
    }

    public void setListeningForBandwidth(boolean value) {
        IShadowsocksService service = getService();
        if (listeningForBandwidth != value && service != null && instance.getServiceCallback() != null) {
            if (value) {
                try {
                    service.startListeningForBandwidth(instance.getServiceCallback());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    service.stopListeningForBandwidth(instance.getServiceCallback());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        listeningForBandwidth = value;
    }

    public IShadowsocksService getService() {
        return service;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        this.binder = binder;
        try {
            binder.linkToDeath(instance, 0);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        IShadowsocksService service = IShadowsocksService.Stub.asInterface(binder);
        this.service = service;
        if (instance.getServiceCallback() != null && !callbackRegistered) {
            try {
                service.registerCallback(instance.getServiceCallback());
                callbackRegistered = true;
                if (listeningForBandwidth) {
                    service.startListeningForBandwidth(instance.getServiceCallback());
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        instance.onServiceConnected(service);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        unregisterCallback();
        instance.onServiceDisconnected();
        service = null;
        binder = null;
    }

    private void unregisterCallback() {
        IShadowsocksService service = getService();
        if (service != null && instance.getServiceCallback() != null && callbackRegistered) {
            try {
                service.unregisterCallback(instance.getServiceCallback());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        callbackRegistered = false;
    }

    public void connect() {
        if (connectionActive) {
            return;
        }
        connectionActive = true;
        Intent intent = new Intent((Context) instance, BaseService.getServiceClass().getClass()).setAction(Action.SERVICE);
        ((Context) instance).bindService(intent, this, Context.BIND_AUTO_CREATE);
    }

    public void disconnect() {
        unregisterCallback();
        instance.onServiceDisconnected();
        if (connectionActive) {
            try {
                ((Context) instance).unbindService(this);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
        connectionActive = false;
        if (binder != null) {
            binder.unlinkToDeath(instance, 0);
        }
        binder = null;
        service = null;
    }
}