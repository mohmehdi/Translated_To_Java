

package com.github.shadowsocks.preference;

import android.support.v7.preference.PreferenceDataStore;
import com.github.shadowsocks.database.KeyValuePair;
import com.j256.ormlite.dao.Dao;
import java.util.HashSet;

public class OrmLitePreferenceDataStore implements PreferenceDataStore {
    private final Dao<KeyValuePair, String> kvPairDao;

    public OrmLitePreferenceDataStore(Dao<KeyValuePair, String> kvPairDao) {
        this.kvPairDao = kvPairDao;
    }

    public Boolean getBoolean(String key) {
        KeyValuePair pair = kvPairDao.queryForId(key);
        return pair != null ? pair.boolean : null;
    }

    public Float getFloat(String key) {
        KeyValuePair pair = kvPairDao.queryForId(key);
        return pair != null ? pair.float : null;
    }

    public Integer getInt(String key) {
        KeyValuePair pair = kvPairDao.queryForId(key);
        return pair != null ? pair.int : null;
    }

    public Long getLong(String key) {
        KeyValuePair pair = kvPairDao.queryForId(key);
        return pair != null ? pair.long : null;
    }

    public String getString(String key) {
        KeyValuePair pair = kvPairDao.queryForId(key);
        return pair != null ? pair.string : null;
    }

    public Set<String> getStringSet(String key) {
        KeyValuePair pair = kvPairDao.queryForId(key);
        return pair != null ? pair.stringSet : null;
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
        return value != null ? value : defValue;
    }

    public void putBoolean(String key, Boolean value) {
        if (value != null) {
            kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
            fireChangeListener(key);
        } else {
            remove(key);
        }
    }

    public void putFloat(String key, Float value) {
        if (value != null) {
            kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
            fireChangeListener(key);
        } else {
            remove(key);
        }
    }

    public void putInt(String key, Integer value) {
        if (value != null) {
            kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
            fireChangeListener(key);
        } else {
            remove(key);
        }
    }

    public void putLong(String key, Long value) {
        if (value != null) {
            kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
            fireChangeListener(key);
        } else {
            remove(key);
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
        if (value != null) {
            kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
            fireChangeListener(key);
        } else {
            remove(key);
        }
    }

    @Override
    public void putStringSet(String key, Set<String> values) {
        if (values != null) {
            kvPairDao.createOrUpdate(new KeyValuePair(key).put(values));
            fireChangeListener(key);
        } else {
            remove(key);
        }
    }

    public void remove(String key) {
        kvPairDao.deleteById(key);
        fireChangeListener(key);
    }

    private final Set<OnPreferenceDataStoreChangeListener> listeners = new HashSet<>();

    private void fireChangeListener(String key) {
        for (OnPreferenceDataStoreChangeListener listener : listeners) {
            listener.onPreferenceDataStoreChanged(this, key);
        }
    }

    default void registerChangeListener(OnPreferenceDataStoreChangeListener listener) {
        listeners.add(listener);
    }

    default void unregisterChangeListener(OnPreferenceDataStoreChangeListener listener) {
        listeners.remove(listener);
    }
}