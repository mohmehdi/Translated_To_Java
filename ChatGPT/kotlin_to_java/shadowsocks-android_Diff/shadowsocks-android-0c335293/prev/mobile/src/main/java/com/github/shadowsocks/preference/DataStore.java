package com.github.shadowsocks.preference;

import android.os.Binder;
import android.support.v7.preference.PreferenceDataStore;
import com.github.shadowsocks.database.DBHelper;
import com.github.shadowsocks.database.KeyValuePair;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.UtilsKt;

import java.util.HashSet;
import java.util.Set;

@SuppressWarnings({"unused", "unchecked"})
public class DataStore extends PreferenceDataStore {

    public static Boolean getBoolean(String key) {
        KeyValuePair pair = DBHelper.kvPairDao.queryForId(key);
        return pair != null ? pair.getBoolean() : null;
    }

    public static Float getFloat(String key) {
        KeyValuePair pair = DBHelper.kvPairDao.queryForId(key);
        return pair != null ? pair.getFloat() : null;
    }

    public static Integer getInt(String key) {
        KeyValuePair pair = DBHelper.kvPairDao.queryForId(key);
        return pair != null ? pair.getInt() : null;
    }

    public static Long getLong(String key) {
        KeyValuePair pair = DBHelper.kvPairDao.queryForId(key);
        return pair != null ? pair.getLong() : null;
    }

    public static String getString(String key) {
        KeyValuePair pair = DBHelper.kvPairDao.queryForId(key);
        return pair != null ? pair.getString() : null;
    }

    public static Set<String> getStringSet(String key) {
        KeyValuePair pair = DBHelper.kvPairDao.queryForId(key);
        return pair != null ? pair.getStringSet() : null;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        Boolean value = getBoolean(key);
        return value != null ? value : defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        Float value = getFloat(key);
        return value != null ? value : defValue;
    }

    @Override
    public int getInt(String key, int defValue) {
        Integer value = getInt(key);
        return value != null ? value : defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
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
        return value != null ? value : defValue;
    }

    public static void putBoolean(String key, Boolean value) {
        if (value == null) {
            remove(key);
        } else {
            putBoolean(key, value.booleanValue());
        }
    }

    public static void putFloat(String key, Float value) {
        if (value == null) {
            remove(key);
        } else {
            putFloat(key, value.floatValue());
        }
    }

    public static void putInt(String key, Integer value) {
        if (value == null) {
            remove(key);
        } else {
            putInt(key, value.intValue());
        }
    }

    public static void putLong(String key, Long value) {
        if (value == null) {
            remove(key);
        } else {
            putLong(key, value.longValue());
        }
    }

    @Override
    public void putBoolean(String key, boolean value) {
        DBHelper.kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
        fireChangeListener(key);
    }

    @Override
    public void putFloat(String key, float value) {
        DBHelper.kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
        fireChangeListener(key);
    }

    @Override
    public void putInt(String key, int value) {
        DBHelper.kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
        fireChangeListener(key);
    }

    @Override
    public void putLong(String key, long value) {
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

    public static void remove(String key) {
        DBHelper.kvPairDao.deleteById(key);
        fireChangeListener(key);
    }

    private static final Set<OnPreferenceDataStoreChangeListener> listeners = new HashSet<>();

    private static void fireChangeListener(String key) {
        for (OnPreferenceDataStoreChangeListener listener : listeners) {
            listener.onPreferenceDataStoreChanged(DataStore.INSTANCE, key);
        }
    }

    public static void registerChangeListener(OnPreferenceDataStoreChangeListener listener) {
        listeners.add(listener);
    }

    public static void unregisterChangeListener(OnPreferenceDataStoreChangeListener listener) {
        listeners.remove(listener);
    }

    private static final int userIndex = UtilsKt.getCallingUserHandle().hashCode();

    private static int getLocalPort(String key, int defaultPort) {
        Integer value = getInt(key);
        if (value != null) {
            putString(key, value.toString());
            return value;
        } else {
            return UtilsKt.parsePort(getString(key), defaultPort + userIndex);
        }
    }

    public static int getProfileId() {
        Integer value = getInt(Key.INSTANCE.getId());
        return value != null ? value : 0;
    }

    public static void setProfileId(int value) {
        putInt(Key.INSTANCE.getId(), value);
    }

    public static String getServiceMode() {
        String value = getString(Key.INSTANCE.getServiceMode());
        return value != null ? value : Key.INSTANCE.getModeVpn();
    }

    public static void setServiceMode(String value) {
        putString(Key.INSTANCE.getServiceMode(), value);
    }

    public static int getPortProxy() {
        return getLocalPort(Key.INSTANCE.getPortProxy(), 1080);
    }

    public static void setPortProxy(int value) {
        putString(Key.INSTANCE.getPortProxy(), Integer.toString(value));
    }

    public static int getPortLocalDns() {
        return getLocalPort(Key.INSTANCE.getPortLocalDns(), 5450);
    }

    public static void setPortLocalDns(int value) {
        putString(Key.INSTANCE.getPortLocalDns(), Integer.toString(value));
    }

    public static int getPortTransproxy() {
        return getLocalPort(Key.INSTANCE.getPortTransproxy(), 8200);
    }

    public static void setPortTransproxy(int value) {
        putString(Key.INSTANCE.getPortTransproxy(), Integer.toString(value));
    }

    public static boolean getProxyApps() {
        Boolean value = getBoolean(Key.INSTANCE.getProxyApps());
        return value != null ? value : false;
    }

    public static void setProxyApps(boolean value) {
        putBoolean(Key.INSTANCE.getProxyApps(), value);
    }

    public static boolean getBypass() {
        Boolean value = getBoolean(Key.INSTANCE.getBypass());
        return value != null ? value : false;
    }

    public static void setBypass(boolean value) {
        putBoolean(Key.INSTANCE.getBypass(), value);
    }

    public static String getIndividual() {
        String value = getString(Key.INSTANCE.getIndividual());
        return value != null ? value : "";
    }

    public static void setIndividual(String value) {
        putString(Key.INSTANCE.getIndividual(), value);
    }

    public static String getPlugin() {
        String value = getString(Key.INSTANCE.getPlugin());
        return value != null ? value : "";
    }

    public static void setPlugin(String value) {
        putString(Key.INSTANCE.getPlugin(), value);
    }

    public static boolean getDirty() {
        Boolean value = getBoolean(Key.INSTANCE.getDirty());
        return value != null ? value : false;
    }

    public static void setDirty(boolean value) {
        putBoolean(Key.INSTANCE.getDirty(), value);
    }

    public static void initGlobal() {
        if (getString(Key.INSTANCE.getServiceMode()) == null) {
            setServiceMode(getServiceMode());
        }
        if (getString(Key.INSTANCE.getPortProxy()) == null) {
            setPortProxy(getPortProxy());
        }
        if (getString(Key.INSTANCE.getPortLocalDns()) == null) {
            setPortLocalDns(getPortLocalDns());
        }
        if (getString(Key.INSTANCE.getPortTransproxy()) == null) {
            setPortTransproxy(getPortTransproxy());
        }
    }
}