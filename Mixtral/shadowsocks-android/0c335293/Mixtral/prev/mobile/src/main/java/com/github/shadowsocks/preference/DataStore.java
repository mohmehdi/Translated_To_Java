package com.github.shadowsocks.preference;

import android.os.Binder;
import android.support.v7.preference.PreferenceDataStore;
import com.github.shadowsocks.database.DBHelper;
import com.github.shadowsocks.database.KeyValuePair;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.parsePort;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DataStore implements PreferenceDataStore {
public Boolean getBoolean(String key) {
KeyValuePair kvPair = DBHelper.kvPairDao.queryForId(key);
return kvPair != null ? kvPair.boolean : null;
}


public Float getFloat(String key) {
    KeyValuePair kvPair = DBHelper.kvPairDao.queryForId(key);
    return kvPair != null ? kvPair.float : null;
}

public Integer getInt(String key) {
    KeyValuePair kvPair = DBHelper.kvPairDao.queryForId(key);
    return kvPair != null ? kvPair.int : null;
}

public Long getLong(String key) {
    KeyValuePair kvPair = DBHelper.kvPairDao.queryForId(key);
    return kvPair != null ? kvPair.long : null;
}

public String getString(String key) {
    KeyValuePair kvPair = DBHelper.kvPairDao.queryForId(key);
    return kvPair != null ? kvPair.string : null;
}

public Set<String> getStringSet(String key) {
    KeyValuePair kvPair = DBHelper.kvPairDao.queryForId(key);
    return kvPair != null ? kvPair.stringSet : null;
}

@Override
public Boolean getBoolean(String key, Boolean defValue) {
    Boolean value = getBoolean(key);
    return value != null ? value : defValue;
}

@Override
public Float getFloat(String key, Float defValue) {
    Float value = getFloat(key);
    return value != null ? value : defValue;
}

@Override
public Integer getInt(String key, Integer defValue) {
    Integer value = getInt(key);
    return value != null ? value : defValue;
}

@Override
public Long getLong(String key, Long defValue) {
    Long value = getLong(key);
    return value != null ? value : defValue;
}

@Override
public String getString(String key, String defValue) {
    String value = getString(key);
    return value != null ? value : defValue;
}

@Override
public Set<String> getStringSet(String key, Set<String> defValue) {
    Set<String> value = getStringSet(key);
    return value != null ? value : defValue != null ? defValue : Collections.emptySet();
}

public void putBoolean(String key, Boolean value) {
    if (value == null) {
        remove(key);
    } else {
        DBHelper.kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
        fireChangeListener(key);
    }
}

public void putFloat(String key, Float value) {
    if (value == null) {
        remove(key);
    } else {
        DBHelper.kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
        fireChangeListener(key);
    }
}

public void putInt(String key, Integer value) {
    if (value == null) {
        remove(key);
    } else {
        DBHelper.kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
        fireChangeListener(key);
    }
}

public void putLong(String key, Long value) {
    if (value == null) {
        remove(key);
    } else {
        DBHelper.kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
        fireChangeListener(key);
    }
}

@Override
public void putBoolean(String key, boolean value) {
    putBoolean(key, value);
}

@Override
public void putFloat(String key, float value) {
    putFloat(key, value);
}

@Override
public void putInt(String key, int value) {
    putInt(key, value);
}

@Override
public void putLong(String key, long value) {
    putLong(key, value);
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
    return value != null ? value : parsePort.parsePort(getString(key), default + userIndex);
}

private int profileId = getInt(Key.id) != null ? getInt(Key.id) : 0;

private String serviceMode = getString(Key.serviceMode) != null ? getString(Key.serviceMode) : Key.modeVpn;

private int portProxy = getLocalPort(Key.portProxy, 1080);

private int portLocalDns = getLocalPort(Key.portLocalDns, 5450);

private int portTransproxy = getLocalPort(Key.portTransproxy, 8200);

private boolean proxyApps = getBoolean(Key.proxyApps) != null ? getBoolean(Key.proxyApps) : false;

private boolean bypass = getBoolean(Key.bypass) != null ? getBoolean(Key.bypass) : false;

private String individual = getString(Key.individual) != null ? getString(Key.individual) : "";

private String plugin = getString(Key.plugin) != null ? getString(Key.plugin) : "";

private boolean dirty = getBoolean(Key.dirty) != null ? getBoolean(Key.dirty) : false;

public void initGlobal() {
    // temporary workaround for support lib bug
    if (getString(Key.serviceMode) == null) {
        serviceMode = serviceMode;
    }
    if (getString(Key.portProxy) == null) {
        portProxy = portProxy;
    }
    if (getString(Key.portLocalDns) == null) {
        portLocalDns = portLocalDns;
    }
    if (getString(Key.portTransproxy) == null) {
        portTransproxy = portTransproxy;
    }
}
}