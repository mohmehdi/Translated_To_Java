package com.github.shadowsocks.preference;

import com.github.shadowsocks.BootReceiver;
import com.github.shadowsocks.database.PrivateDatabase;
import com.github.shadowsocks.database.PublicDatabase;
import com.github.shadowsocks.database.KvPair;
import com.github.shadowsocks.utils.DirectBoot;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.parsePort;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.GenericRawResults;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.support.ConnectionSource;

import java.sql.SQLException;
import java.util.List;

public class DataStore {
    private static Dao<KvPair, Integer> publicStore;
    private static Dao<KvPair, Integer> privateStore;

    private static final int userIndex = Binder.getCallingUserHandle().hashCode();

    static {
        try {
            publicStore = PublicDatabase.getHelper().getKvPairDao();
            privateStore = PrivateDatabase.getHelper().getKvPairDao();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static int getLocalPort(String key, int defaultValue) {
        int value = getInt(publicStore, key);
        return value != 0 ? value : parsePort(getString(publicStore, key), defaultValue + userIndex);
    }

    public static int getInt(Dao<KvPair, Integer> dao, String key) {
        int value = 0;
        try {
            QueryBuilder<KvPair, Integer> queryBuilder = dao.queryBuilder();
            queryBuilder.where().eq(KvPair.KEY_FIELD_NAME, key);
            List<KvPair> result = queryBuilder.query();
            if (!result.isEmpty()) {
                value = Integer.parseInt(result.get(0).getValue());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return value;
    }

    public static String getString(Dao<KvPair, Integer> dao, String key) {
        String value = "";
        try {
            QueryBuilder<KvPair, Integer> queryBuilder = dao.queryBuilder();
            queryBuilder.where().eq(KvPair.KEY_FIELD_NAME, key);
            List<KvPair> result = queryBuilder.query();
            if (!result.isEmpty()) {
                value = result.get(0).getValue();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return value;
    }

    public static int profileId() {
        return getInt(publicStore, Key.id);
    }

    public static void setProfileId(int value) {
        putInt(publicStore, Key.id, value);
        if (DataStore.directBootAware()) DirectBoot.update();
    }

    public static boolean directBootAware() {
        return BootReceiver.enabled() && getBoolean(publicStore, Key.directBootAware, false);
    }

    public static String serviceMode() {
        return getString(publicStore, Key.serviceMode);
    }

    public static void setServiceMode(String value) {
        putString(publicStore, Key.serviceMode, value);
    }

    public static int portProxy() {
        return getLocalPort(Key.portProxy, 1080);
    }

    public static void setPortProxy(int value) {
        putString(publicStore, Key.portProxy, Integer.toString(value));
    }

    public static int portLocalDns() {
        return getLocalPort(Key.portLocalDns, 5450);
    }

    public static void setPortLocalDns(int value) {
        putString(publicStore, Key.portLocalDns, Integer.toString(value));
    }

    public static int portTransproxy() {
        return getLocalPort(Key.portTransproxy, 8200);
    }

    public static void setPortTransproxy(int value) {
        putString(publicStore, Key.portTransproxy, Integer.toString(value));
    }

    public static void initGlobal() {
        if (getString(publicStore, Key.serviceMode) == null) setServiceMode(serviceMode());
        if (getString(publicStore, Key.portProxy) == null) setPortProxy(portProxy());
        if (getString(publicStore, Key.portLocalDns) == null) setPortLocalDns(portLocalDns());
        if (getString(publicStore, Key.portTransproxy) == null) setPortTransproxy(portTransproxy());
    }

    public static boolean getBoolean(Dao<KvPair, Integer> dao, String key, boolean defaultValue) {
        boolean value = defaultValue;
        try {
            QueryBuilder<KvPair, Integer> queryBuilder = dao.queryBuilder();
            queryBuilder.where().eq(KvPair.KEY_FIELD_NAME, key);
            List<KvPair> result = queryBuilder.query();
            if (!result.isEmpty()) {
                value = Boolean.parseBoolean(result.get(0).getValue());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return value;
    }

    public static void putInt(Dao<KvPair, Integer> dao, String key, int value) {
        KvPair kvPair = new KvPair();
        kvPair.setKey(key);
        kvPair.setValue(Integer.toString(value));
        try {
            dao.create(kvPair);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void putString(Dao<KvPair, Integer> dao, String key, String value) {
        KvPair kvPair = new KvPair();
        kvPair.setKey(key);
        kvPair.setValue(value);
        try {
            dao.create(kvPair);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean proxyApps() {
        try {
            QueryBuilder<KvPair, Integer> queryBuilder = privateStore.queryBuilder();
            queryBuilder.where().eq(KvPair.KEY_FIELD_NAME, Key.proxyApps);
            List<KvPair> result = queryBuilder.query();
            if (!result.isEmpty()) {
                return result.get(0).getValue().equals("true");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void proxyApps(boolean value) {
        KvPair kvPair = new KvPair();
        kvPair.setKey(Key.proxyApps);
        kvPair.setValue(Boolean.toString(value));
        try {
            privateStore.create(kvPair);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // bypass
    public boolean bypass() {
        try {
            QueryBuilder<KvPair, Integer> queryBuilder = privateStore.queryBuilder();
            queryBuilder.where().eq(KvPair.KEY_FIELD_NAME, Key.bypass);
            List<KvPair> result = queryBuilder.query();
            if (!result.isEmpty()) {
                return result.get(0).getValue().equals("true");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void bypass(boolean value) {
        KvPair kvPair = new KvPair();
        kvPair.setKey(Key.bypass);
        kvPair.setValue(Boolean.toString(value));
        try {
            privateStore.create(kvPair);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // individual
    public String individual() {
        try {
            QueryBuilder<KvPair, Integer> queryBuilder = privateStore.queryBuilder();
            queryBuilder.where().eq(KvPair.KEY_FIELD_NAME, Key.individual);
            List<KvPair> result = queryBuilder.query();
            if (!result.isEmpty()) {
                return result.get(0).getValue();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
    }

    public void individual(String value) {
        KvPair kvPair = new KvPair();
        kvPair.setKey(Key.individual);
        kvPair.setValue(value);
        try {
            privateStore.create(kvPair);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
String plugin() {
    return privateStore.containsKey(Key.plugin) ? privateStore.getString(Key.plugin) : "";
}

void plugin(String value) {
    privateStore.put(Key.plugin, value);
}

// dirty
boolean dirty() {
    return privateStore.containsKey(Key.dirty) ? privateStore.getBoolean(Key.dirty) : false;
}

void dirty(boolean value) {
    privateStore.put(Key.dirty, value);
}
}