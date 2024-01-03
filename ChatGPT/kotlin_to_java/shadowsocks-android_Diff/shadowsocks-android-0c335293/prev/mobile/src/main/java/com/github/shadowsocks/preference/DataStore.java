package com.github.shadowsocks.preference;

import android.os.Binder;
import android.support.v7.preference.PreferenceDataStore;
import com.github.shadowsocks.database.DBHelper;
import com.github.shadowsocks.database.KeyValuePair;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.parsePort;
import java.util.*;

@SuppressWarnings("MemberVisibilityCanPrivate", "unused")
public class DataStore extends PreferenceDataStore {
    public Boolean getBoolean(String key) {
        return DBHelper.kvPairDao.queryForId(key)?.getBoolean();
    }
    public Float getFloat(String key) {
        return DBHelper.kvPairDao.queryForId(key)?.getFloat();
    }
    public Integer getInt(String key) {
        return DBHelper.kvPairDao.queryForId(key)?.getInt();
    }
    public Long getLong(String key) {
        return DBHelper.kvPairDao.queryForId(key)?.getLong();
    }
    public String getString(String key) {
        return DBHelper.kvPairDao.queryForId(key)?.getString();
    }
    public Set<String> getStringSet(String key) {
        return DBHelper.kvPairDao.queryForId(key)?.getStringSet();
    }

    @Override
    public Boolean getBoolean(String key, Boolean defValue) {
        return getBoolean(key) != null ? getBoolean(key) : defValue;
    }
    @Override
    public Float getFloat(String key, Float defValue) {
        return getFloat(key) != null ? getFloat(key) : defValue;
    }
    @Override
    public Integer getInt(String key, Integer defValue) {
        return getInt(key) != null ? getInt(key) : defValue;
    }
    @Override
    public Long getLong(String key, Long defValue) {
        return getLong(key) != null ? getLong(key) : defValue;
    }
    @Override
    public String getString(String key, String defValue) {
        return getString(key) != null ? getString(key) : defValue;
    }
    @Override
    public Set<String> getStringSet(String key, Set<String> defValue) {
        return getStringSet(key) != null ? getStringSet(key) : defValue;
    }

    public void putBoolean(String key, Boolean value) {
        if (value == null) {
            remove(key);
        } else {
            putBoolean(key, value);
        }
    }
    public void putFloat(String key, Float value) {
        if (value == null) {
            remove(key);
        } else {
            putFloat(key, value);
        }
    }
    public void putInt(String key, Integer value) {
        if (value == null) {
            remove(key);
        } else {
            putInt(key, value);
        }
    }
    public void putLong(String key, Long value) {
        if (value == null) {
            remove(key);
        } else {
            putLong(key, value);
        }
    }
    @Override
    public void putBoolean(String key, Boolean value) {
        DBHelper.kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
        fireChangeListener(key);
    }
    @Override
    public void putFloat(String key, Float value) {
        DBHelper.kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
        fireChangeListener(key);
    }
    @Override
    public void putInt(String key, Integer value) {
        DBHelper.kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
        fireChangeListener(key);
    }
    @Override
    public void putLong(String key, Long value) {
        DBHelper.kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
        fireChangeListener(key);
    }
    @Override
    public void putString(String key, String value) {
        if (value == null) {
            remove(key);
        } else {
            DBHelper.kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
            fireChangeListener(key);
        }
    }
    @Override
    public void putStringSet(String key, Set<String> values) {
        if (values == null) {
            remove(key);
        } else {
            DBHelper.kvPairDao.createOrUpdate(new KeyValuePair(key).put(values));
            fireChangeListener(key);
        }
    }

    public void remove(String key) {
        DBHelper.kvPairDao.deleteById(key);
        fireChangeListener(key);
    }

    private Set<OnPreferenceDataStoreChangeListener> listeners = new HashSet<>();
    private void fireChangeListener(String key) {
        for (OnPreferenceDataStoreChangeListener listener : listeners) {
            listener.onPreferenceDataStoreChanged(this, key);
        }
    }
    public void registerChangeListener(OnPreferenceDataStoreChangeListener listener) {
        listeners.add(listener);
    }
    public void unregisterChangeListener(OnPreferenceDataStoreChangeListener listener) {
        listeners.remove(listener);
    }


    private int userIndex = Binder.getCallingUserHandle().hashCode();
    private int getLocalPort(String key, int default) {
        Integer value = getInt(key);
        if (value != null) {
            putString(key, value.toString());
            return value;
        } else {
            return parsePort(getString(key), default + userIndex);
        }
    }

    public int getProfileId() {
        return getInt(Key.id) != null ? getInt(Key.id) : 0;
    }
    public void setProfileId(int value) {
        putInt(Key.id, value);
    }
    public String getServiceMode() {
        return getString(Key.serviceMode) != null ? getString(Key.serviceMode) : Key.modeVpn;
    }
    public void setServiceMode(String value) {
        putString(Key.serviceMode, value);
    }
    public int getPortProxy() {
        return getLocalPort(Key.portProxy, 1080);
    }
    public void setPortProxy(int value) {
        putString(Key.portProxy, Integer.toString(value));
    }
    public int getPortLocalDns() {
        return getLocalPort(Key.portLocalDns, 5450);
    }
    public void setPortLocalDns(int value) {
        putString(Key.portLocalDns, Integer.toString(value));
    }
    public int getPortTransproxy() {
        return getLocalPort(Key.portTransproxy, 8200);
    }
    public void setPortTransproxy(int value) {
        putString(Key.portTransproxy, Integer.toString(value));
    }

    public boolean getProxyApps() {
        return getBoolean(Key.proxyApps) != null ? getBoolean(Key.proxyApps) : false;
    }
    public void setProxyApps(boolean value) {
        putBoolean(Key.proxyApps, value);
    }
    public boolean getBypass() {
        return getBoolean(Key.bypass) != null ? getBoolean(Key.bypass) : false;
    }
    public void setBypass(boolean value) {
        putBoolean(Key.bypass, value);
    }
    public String getIndividual() {
        return getString(Key.individual) != null ? getString(Key.individual) : "";
    }
    public void setIndividual(String value) {
        putString(Key.individual, value);
    }
    public String getPlugin() {
        return getString(Key.plugin) != null ? getString(Key.plugin) : "";
    }
    public void setPlugin(String value) {
        putString(Key.plugin, value);
    }
    public boolean getDirty() {
        return getBoolean(Key.dirty) != null ? getBoolean(Key.dirty) : false;
    }
    public void setDirty(boolean value) {
        putBoolean(Key.dirty, value);
    }

    public void initGlobal() {
        if (getString(Key.serviceMode) == null) {
            setServiceMode(getServiceMode());
        }
        if (getString(Key.portProxy) == null) {
            setPortProxy(getPortProxy());
        }
        if (getString(Key.portLocalDns) == null) {
            setPortLocalDns(getPortLocalDns());
        }
        if (getString(Key.portTransproxy) == null) {
            setPortTransproxy(getPortTransproxy());
        }
    }
}