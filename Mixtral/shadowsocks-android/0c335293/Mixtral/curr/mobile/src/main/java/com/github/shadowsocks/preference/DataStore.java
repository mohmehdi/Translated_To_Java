

package com.github.shadowsocks.preference;

import android.os.Binder;
import com.github.shadowsocks.BootReceiver;
import com.github.shadowsocks.database.PrivateDatabase;
import com.github.shadowsocks.database.PublicDatabase;
import com.github.shadowsocks.utils.DirectBoot;
import com.github.shadowsocks.utils.Key;
import com.github.shadowsocks.utils.parsePort;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.GenericRawResults;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.DatabaseTable;

import java.sql.SQLException;
import java.util.List;

public class DataStore {
    public static OrmLitePreferenceDataStore publicStore = new OrmLitePreferenceDataStore(PublicDatabase.kvPairDao);
    public static OrmLitePreferenceDataStore privateStore = new OrmLitePreferenceDataStore(PrivateDatabase.kvPairDao);

    private static final int userIndex = Binder.getCallingUserHandle().hashCode();

    public static int getLocalPort(String key, int default) {
        int value = publicStore.getInt(key);
        if (value != 0) {
            publicStore.putString(key, String.valueOf(value));
            return value;
        } else {
            String result = publicStore.getString(key);
            if (result == null || result.isEmpty()) {
                return default + userIndex;
            } else {
                return parsePort(result, default + userIndex);
            }
        }
    }

    public static int profileId = publicStore.getInt(Key.id, 0);

    public static String serviceMode = publicStore.getString(Key.serviceMode, Key.modeVpn);

    public static int portProxy = getLocalPort(Key.portProxy, 1080);

    public static int portLocalDns = getLocalPort(Key.portLocalDns, 5450);

    public static int portTransproxy = getLocalPort(Key.portTransproxy, 8200);

    public static void initGlobal() {
        if (publicStore.getString(Key.serviceMode) == null) {
            serviceMode = serviceMode;
        }
        if (publicStore.getString(Key.portProxy) == null) {
            portProxy = portProxy;
        }
        if (publicStore.getString(Key.portLocalDns) == null) {
            portLocalDns = portLocalDns;
        }
        if (publicStore.getString(Key.portTransproxy) == null) {
            portTransproxy = portTransproxy;
        }
    }

    public static boolean proxyApps = privateStore.getBoolean(Key.proxyApps, false);

    public static boolean bypass = privateStore.getBoolean(Key.bypass, false);

    public static String individual = privateStore.getString(Key.individual, "");

    public static String plugin = privateStore.getString(Key.plugin, "");

    public static boolean dirty = privateStore.getBoolean(Key.dirty, false);

}