package com.github.shadowsocks.preference;

import android.support.v7.preference.PreferenceDataStore;
import com.github.shadowsocks.database.KeyValuePair;
import com.j256.ormlite.dao.Dao;
import java.util.HashSet;

@SuppressWarnings({"MemberVisibilityCanPrivate", "unused"})
public class OrmLitePreferenceDataStore extends PreferenceDataStore {
    private Dao<KeyValuePair, String> kvPairDao;

    public OrmLitePreferenceDataStore(Dao<KeyValuePair, String> kvPairDao) {
        this.kvPairDao = kvPairDao;
    }

    public Boolean getBoolean(String key) {
        return kvPairDao.queryForId(key).getBoolean();
    }

    public Float getFloat(String key) {
        return kvPairDao.queryForId(key).getFloat();
    }

    public Integer getInt(String key) {
        return kvPairDao.queryForId(key).getInt();
    }

    public Long getLong(String key) {
        return kvPairDao.queryForId(key).getLong();
    }

    public String getString(String key) {
        return kvPairDao.queryForId(key).getString();
    }

    public Set<String> getStringSet(String key) {
        return kvPairDao.queryForId(key).getStringSet();
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
        if (value == null) {
            remove(key);
        } else {
            kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
            fireChangeListener(key);
        }
    }

    public void putFloat(String key, Float value) {
        if (value == null) {
            remove(key);
        } else {
            kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
            fireChangeListener(key);
        }
    }

    public void putInt(String key, Integer value) {
        if (value == null) {
            remove(key);
        } else {
            kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
            fireChangeListener(key);
        }
    }

    public void putLong(String key, Long value) {
        if (value == null) {
            remove(key);
        } else {
            kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
            fireChangeListener(key);
        }
    }

    @Override
    public void putString(String key, String value) {
        if (value == null) {
            remove(key);
        } else {
            kvPairDao.createOrUpdate(new KeyValuePair(key).put(value));
            fireChangeListener(key);
        }
    }

    @Override
    public void putStringSet(String key, Set<String> values) {
        if (values == null) {
            remove(key);
        } else {
            kvPairDao.createOrUpdate(new KeyValuePair(key).put(values));
            fireChangeListener(key);
        }
    }

    public void remove(String key) {
        kvPairDao.deleteById(key);
        fireChangeListener(key);
    }

    private HashSet<OnPreferenceDataStoreChangeListener> listeners = new HashSet<>();

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
}