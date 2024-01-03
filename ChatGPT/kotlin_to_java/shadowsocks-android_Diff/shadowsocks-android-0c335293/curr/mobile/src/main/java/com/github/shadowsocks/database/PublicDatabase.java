package com.github.shadowsocks.database;

import android.database.sqlite.SQLiteDatabase;
import com.github.shadowsocks.App;
import com.github.shadowsocks.acl.Acl;
import com.github.shadowsocks.utils.Key;
import com.j256.ormlite.android.AndroidDatabaseConnection;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

public class PublicDatabase extends OrmLiteSqliteOpenHelper {
    private static final String DB_NAME = Key.DB_PUBLIC;
    private static final int DB_VERSION = 2;

    private Dao<KeyValuePair, String> kvPairDao;

    public PublicDatabase() {
        super(App.Companion.getDeviceContext(), DB_NAME, null, DB_VERSION);
        kvPairDao = null;
    }

    public Dao<KeyValuePair, String> getKvPairDao() {
        if (kvPairDao == null) {
            try {
                kvPairDao = getDao(KeyValuePair.class);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return kvPairDao;
    }

    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            TableUtils.createTable(connectionSource, KeyValuePair.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        if (oldVersion < 1) {
            PrivateDatabase.getKvPairDao().queryBuilder().where().in("key",
                    Key.id, Key.tfo, Key.serviceMode, Key.portProxy, Key.portLocalDns, Key.portTransproxy).query()
                    .forEach(kvPair -> {
                        try {
                            getKvPairDao().createOrUpdate(kvPair);
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    });
        }

        if (oldVersion < 2) {
            try {
                getKvPairDao().createOrUpdate(new KeyValuePair(Acl.CUSTOM_RULES).put(Acl.fromId(Acl.CUSTOM_RULES).toString()));
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        AndroidDatabaseConnection connection = new AndroidDatabaseConnection(db, true);
        try {
            getConnectionSource().saveSpecialConnection(connection);
            TableUtils.dropTable(connectionSource, KeyValuePair.class, true);
            onCreate(db, connectionSource);
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            getConnectionSource().clearSpecialConnection(connection);
        }
    }
}