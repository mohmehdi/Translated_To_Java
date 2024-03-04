package com.github.shadowsocks.preference;

import android.os.Binder;
import com.github.shadowsocks.BootReceiver;
import com.github.shadowsocks.database.PrivateDatabase;
import com.github.shadowsocks.database.PublicDatabase;
import com.github.shadowsocks.utils.DirectBoot;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.parsePort;

public class DataStore {
    public static OrmLitePreferenceDataStore publicStore = new OrmLitePreferenceDataStore(PublicDatabase.kvPairDao);
    
    public static OrmLitePreferenceDataStore privateStore = new OrmLitePreferenceDataStore(PrivateDatabase.kvPairDao);

    
    private static int userIndex = Binder.getCallingUserHandle().hashCode();
    private static int getLocalPort(String key, int defaultVal) {
        Integer value = publicStore.getInt(key);
        if (value != null) {
            publicStore.putString(key, value.toString());
            return value;
        } else {
            return parsePort(publicStore.getString(key), defaultVal + userIndex);
        }
    }

    public static int getProfileId() {
        Integer value = publicStore.getInt(Key.id);
        return value != null ? value : 0;
    }

    public static void setProfileId(int value) {
        publicStore.putInt(Key.id, value);
        if (DataStore.directBootAware()) {
            DirectBoot.update();
        }
    }
    
    public static boolean directBootAware() {
        return BootReceiver.enabled && (publicStore.getBoolean(Key.directBootAware) != null ? publicStore.getBoolean(Key.directBootAware) : false);
    }

    public static String getServiceMode() {
        return publicStore.getString(Key.serviceMode) != null ? publicStore.getString(Key.serviceMode) : Key.modeVpn;
    }

    public static void setServiceMode(String value) {
        publicStore.putString(Key.serviceMode, value);
    }

    public static int getPortProxy() {
        return getLocalPort(Key.portProxy, 1080);
    }

    public static void setPortProxy(int value) {
        publicStore.putString(Key.portProxy, Integer.toString(value));
    }

    public static int getPortLocalDns() {
        return getLocalPort(Key.portLocalDns, 5450);
    }

    public static void setPortLocalDns(int value) {
        publicStore.putString(Key.portLocalDns, Integer.toString(value));
    }

    public static int getPortTransproxy() {
        return getLocalPort(Key.portTransproxy, 8200);
    }

    public static void setPortTransproxy(int value) {
        publicStore.putString(Key.portTransproxy, Integer.toString(value));
    }

    public static void initGlobal() {
        if (publicStore.getString(Key.serviceMode) == null) {
            setServiceMode(getServiceMode());
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

    public static boolean getProxyApps() {
        return privateStore.getBoolean(Key.proxyApps) != null ? privateStore.getBoolean(Key.proxyApps) : false;
    }

    public static void setProxyApps(boolean value) {
        privateStore.putBoolean(Key.proxyApps, value);
    }

    public static boolean getBypass() {
        return privateStore.getBoolean(Key.bypass) != null ? privateStore.getBoolean(Key.bypass) : false;
    }

    public static void setBypass(boolean value) {
        privateStore.putBoolean(Key.bypass, value);
    }

    public static String getIndividual() {
        return privateStore.getString(Key.individual) != null ? privateStore.getString(Key.individual) : "";
    }

    public static void setIndividual(String value) {
        privateStore.putString(Key.individual, value);
    }

    public static String getPlugin() {
        return privateStore.getString(Key.plugin) != null ? privateStore.getString(Key.plugin) : "";
    }

    public static void setPlugin(String value) {
        privateStore.putString(Key.plugin, value);
    }

    public static boolean getDirty() {
        return privateStore.getBoolean(Key.dirty) != null ? privateStore.getBoolean(Key.dirty) : false;
    }

    public static void setDirty(boolean value) {
        privateStore.putBoolean(Key.dirty, value);
    }
}