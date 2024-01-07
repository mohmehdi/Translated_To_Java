package com.github.shadowsocks.preference;

import android.os.Binder;
import androidx.preference.PreferenceDataStore;
import com.github.shadowsocks.Core;
import com.github.shadowsocks.database.PrivateDatabase;
import com.github.shadowsocks.database.PublicDatabase;
import com.github.shadowsocks.utils.DirectBoot;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.net.TcpFastOpen;
import com.github.shadowsocks.utils.parsePort;
import java.net.NetworkInterface;
import java.net.SocketException;

public class DataStore implements OnPreferenceDataStoreChangeListener {
    private static final DataStore INSTANCE = new DataStore();
    public static DataStore getInstance() {
        return INSTANCE;
    }

    private RoomPreferenceDataStore publicStore;
    private RoomPreferenceDataStore privateStore;

    private DataStore() {
        publicStore = new RoomPreferenceDataStore(PublicDatabase.kvPairDao);
        privateStore = new RoomPreferenceDataStore(PrivateDatabase.kvPairDao);
        publicStore.registerChangeListener(this);
    }

    @Override
    public void onPreferenceDataStoreChanged(PreferenceDataStore store, String key) {
        switch (key) {
            case Key.id:
                if (DataStore.getInstance().directBootAware()) {
                    DirectBoot.update();
                }
                break;
        }
    }

    private int userIndex = Binder.getCallingUserHandle().hashCode();
    private int getLocalPort(String key, int defaultPort) {
        Integer value = publicStore.getInt(key);
        if (value != null) {
            publicStore.putString(key, value.toString());
            return value;
        } else {
            return parsePort(publicStore.getString(key), defaultPort + userIndex);
        }
    }

    public long getProfileId() {
        Long profileId = publicStore.getLong(Key.id);
        return profileId != null ? profileId : 0;
    }

    public void setProfileId(long value) {
        publicStore.putLong(Key.id, value);
    }

    public boolean canToggleLocked() {
        Boolean canToggleLocked = publicStore.getBoolean(Key.directBootAware);
        return canToggleLocked != null && canToggleLocked;
    }

    public boolean directBootAware() {
        return Core.directBootSupported() && canToggleLocked();
    }

    public boolean tcpFastOpen() {
        return TcpFastOpen.sendEnabled() && publicStore.getBoolean(Key.tfo, true);
    }

    public String getServiceMode() {
        String serviceMode = publicStore.getString(Key.serviceMode);
        return serviceMode != null ? serviceMode : Key.modeVpn;
    }

    private boolean hasArc0() {
        int retry = 0;
        while (retry < 5) {
            try {
                return NetworkInterface.getByName("arc0") != null;
            } catch (SocketException ignored) {
            }
            retry++;
            try {
                Thread.sleep(100L << retry);
            } catch (InterruptedException ignored) {
            }
        }
        return false;
    }

    public String getListenAddress() {
        boolean shareOverLan = publicStore.getBoolean(Key.shareOverLan, hasArc0());
        return shareOverLan ? "0.0.0.0" : "127.0.0.1";
    }

    public int getPortProxy() {
        return getLocalPort(Key.portProxy, 1080);
    }

    public void setPortProxy(int value) {
        publicStore.putString(Key.portProxy, Integer.toString(value));
    }

    public int getPortLocalDns() {
        return getLocalPort(Key.portLocalDns, 5450);
    }

    public void setPortLocalDns(int value) {
        publicStore.putString(Key.portLocalDns, Integer.toString(value));
    }

    public int getPortTransproxy() {
        return getLocalPort(Key.portTransproxy, 8200);
    }

    public void setPortTransproxy(int value) {
        publicStore.putString(Key.portTransproxy, Integer.toString(value));
    }

    public void initGlobal() {
        if (publicStore.getBoolean(Key.tfo) == null) {
            publicStore.putBoolean(Key.tfo, tcpFastOpen());
        }
        if (publicStore.getString(Key.portProxy) == null) {
            setPortProxy(getPortProxy());
        }
        if (publicStore.getString(Key.portLocalDns) == null) {
            setPortLocalDns(getPortLocalDns());
        }
        if (publicStore.getString(Key.portTransproxy) == null) {
            setPortTransproxy(getPortTransproxy());
        }
    }

    public Long getEditingId() {
        return privateStore.getLong(Key.id);
    }

    public void setEditingId(Long value) {
        privateStore.putLong(Key.id, value);
    }

    public boolean getProxyApps() {
        Boolean proxyApps = privateStore.getBoolean(Key.proxyApps);
        return proxyApps != null && proxyApps;
    }

    public void setProxyApps(boolean value) {
        privateStore.putBoolean(Key.proxyApps, value);
    }

    public boolean getBypass() {
        Boolean bypass = privateStore.getBoolean(Key.bypass);
        return bypass != null && bypass;
    }

    public void setBypass(boolean value) {
        privateStore.putBoolean(Key.bypass, value);
    }

    public String getIndividual() {
        String individual = privateStore.getString(Key.individual);
        return individual != null ? individual : "";
    }

    public void setIndividual(String value) {
        privateStore.putString(Key.individual, value);
    }

    public String getPlugin() {
        String plugin = privateStore.getString(Key.plugin);
        return plugin != null ? plugin : "";
    }

    public void setPlugin(String value) {
        privateStore.putString(Key.plugin, value);
    }

    public Long getUdpFallback() {
        return privateStore.getLong(Key.udpFallback);
    }

    public void setUdpFallback(Long value) {
        privateStore.putLong(Key.udpFallback, value);
    }

    public boolean getDirty() {
        Boolean dirty = privateStore.getBoolean(Key.dirty);
        return dirty != null && dirty;
    }

    public void setDirty(boolean value) {
        privateStore.putBoolean(Key.dirty, value);
    }
}