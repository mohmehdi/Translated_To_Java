package com.github.shadowsocks.database;

import android.database.sqlite.SQLiteDatabase;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;
import com.github.shadowsocks.App;
import com.github.shadowsocks.utils.Key;
import com.j256.ormlite.android.AndroidDatabaseConnection;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PrivateDatabase extends OrmLiteSqliteOpenHelper {
private static final Class[] classes = {Profile.class, KeyValuePair.class};


private Dao<Profile, Integer> profileDao;
private Dao<KeyValuePair, String> kvPairDao;

public PrivateDatabase(App app, String databaseName, ConnectionSource connectionSource, int version) {
    super(app, databaseName, connectionSource, version);
}

public Dao<Profile, Integer> getProfileDao() throws SQLException {
    if (profileDao == null) {
        profileDao = getDao(Profile.class);
    }
    return profileDao;
}

public Dao<KeyValuePair, String> getKvPairDao() throws SQLException {
    if (kvPairDao == null) {
        kvPairDao = getDao(KeyValuePair.class);
    }
    return kvPairDao;
}

@Override
public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
    for (Class clazz : classes) {
        TableUtils.createTable(connectionSource, clazz);
    }
}

private void recreate(SQLiteDatabase database, ConnectionSource connectionSource) {
    for (Class clazz : classes) {
        TableUtils.dropTable(connectionSource, clazz, true);
    }
    onCreate(database, connectionSource);
}

@Override
public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
    if (oldVersion < 7) {
        recreate(database, connectionSource);
        return;
    }

    try {
        if (oldVersion < 8) {
            getProfileDao().executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN udpdns SMALLINT;");
        }
        if (oldVersion < 9) {
            getProfileDao().executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN route VARCHAR DEFAULT 'all';");
        } else if (oldVersion < 19) {
            getProfileDao().executeRawNoArgs("UPDATE `profile` SET route = 'all' WHERE route IS NULL;");
        }
        if (oldVersion < 11) {
            getProfileDao().executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN ipv6 SMALLINT;");
        }
        if (oldVersion < 12) {
            getProfileDao().executeRawNoArgs("BEGIN TRANSACTION;");
            getProfileDao().executeRawNoArgs("ALTER TABLE `profile` RENAME TO `tmp`;");
            for (Class clazz : classes) {
                TableUtils.createTable(connectionSource, clazz);
            }
            List<String> sql = Arrays.asList(
                    "INSERT INTO `profile`(id, name, host, localPort, remotePort, password, method, route, proxyApps, bypass," +
                            " udpdns, ipv6, individual) " +
                            "SELECT id, name, host, localPort, remotePort, password, method, route, 1 - global, bypass, udpdns, ipv6," +
                            " individual FROM `tmp`;",
                    "DROP TABLE `tmp`;");
            for (String s : sql) {
                getProfileDao().executeRawNoArgs(s);
            }
            getProfileDao().executeRawNoArgs("COMMIT;");
        } else if (oldVersion < 13) {
            getProfileDao().executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN tx LONG;");
            getProfileDao().executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN rx LONG;");
            getProfileDao().executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN date VARCHAR;");
        }

        if (oldVersion < 15) {
            if (oldVersion >= 12) getProfileDao().executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN userOrder LONG;");
            long i = 0;
            List<android.content.pm.ApplicationInfo> apps = App.getAppContext().getPackageManager().getInstalledApplications(0);
            List<Profile> profiles = getProfileDao().queryForAll();
            for (Profile profile : profiles) {
                if (oldVersion < 14) {
                    Set<Integer> uidSet = Arrays.stream(profile.individual.split("\\|")).filter(TextUtils::isDigitsOnly)
                            .map(Integer::parseInt).collect(Collectors.toSet());
                    profile.individual = apps.stream().filter(appInfo -> uidSet.contains(appInfo.uid))
                            .map(Object::toString).collect(Collectors.joining("\n"));
                }
                profile.userOrder = i;
                getProfileDao().update(profile);
                i += 1;
            }
        }

        if (oldVersion < 16) {
            getProfileDao().executeRawNoArgs(
                    "UPDATE `profile` SET route = 'bypass-lan-china' WHERE route = 'bypass-china'");
        }

        if (oldVersion < 21) {
            getProfileDao().executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN remoteDns VARCHAR DEFAULT '8.8.8.8';");
        }

        if (oldVersion < 17) {
            getProfileDao().executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN plugin VARCHAR;");
        } else if (oldVersion < 22) {
            // upgrade kcptun to SIP003 plugin
            getProfileDao().executeRawNoArgs("BEGIN TRANSACTION;");
            getProfileDao().executeRawNoArgs("ALTER TABLE `profile` RENAME TO `tmp`;");
            for (Class clazz : classes) {
                TableUtils.createTable(connectionSource, clazz);
            }
            List<String> sql = Arrays.asList(
                    "INSERT INTO `profile`(id, name, host, localPort, remotePort, password, method, route, " +
                            "remoteDns, proxyApps, bypass, udpdns, ipv6, individual, tx, rx, date, userOrder, " +
                            "plugin) " +
                            "SELECT id, name, host, localPort, " +
                            "CASE WHEN kcp = 1 THEN kcpPort ELSE remotePort END, password, method, route, " +
                            "remoteDns, proxyApps, bypass, udpdns, ipv6, individual, tx, rx, date, userOrder, " +
                            "CASE WHEN kcp = 1 THEN 'kcptun ' || kcpcli ELSE NULL END FROM `tmp`;",
                    "DROP TABLE `tmp`;");
            for (String s : sql) {
                getProfileDao().executeRawNoArgs(s);
            }
            getProfileDao().executeRawNoArgs("COMMIT;");
        }

        if (oldVersion < 23) {
            getProfileDao().executeRawNoArgs("BEGIN TRANSACTION;");
            for (Class clazz : classes) {
                TableUtils.createTable(connectionSource, clazz);
            }
            getProfileDao().executeRawNoArgs("COMMIT;");
            android.content.SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(App.getAppContext());
            KeyValuePair idKvPair = new KeyValuePair(Key.id).put(sharedPreferences.getInt(Key.id, 0));
            KeyValuePair tfoKvPair = new KeyValuePair(Key.tfo).put(sharedPreferences.getBoolean(Key.tfo, false));
            getKvPairDao().createOrUpdate(idKvPair);
            getKvPairDao().createOrUpdate(tfoKvPair);
        }

        if (oldVersion < 25) {
            PublicDatabase.onUpgrade(database, 0, -1);
        }
    } catch (Exception ex) {
        App.getAppContext().track(ex);
        recreate(database, connectionSource);
    }
}

@Override
public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    AndroidDatabaseConnection connection = new AndroidDatabaseConnection(db, true);
    connectionSource.saveSpecialConnection(connection);
    recreate(db, connectionSource);
    connectionSource.clearSpecialConnection(connection);
}
}