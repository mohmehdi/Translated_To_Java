

package com.github.shadowsocks.database;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Pair;

import com.github.shadowsocks.App;
import com.github.shadowsocks.utils.Key;
import com.j256.ormlite.android.AndroidDatabaseConnection;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DBHelper extends OrmLiteSqliteOpenHelper {
    private static final String DATABASE_NAME = Key.PROFILE;
    private static final int DATABASE_VERSION = 24;

    private Dao<Profile, Integer> profileDao;
    private Dao<KeyValuePair, String> kvPairDao;

    public DBHelper(App app) {
        super(app, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @SuppressWarnings("unchecked")
    public Dao<Profile, Integer> getProfileDao() throws SQLException {
        if (profileDao == null) {
            profileDao = getDao(Profile.class);
        }
        return (Dao<Profile, Integer>) profileDao;
    }

    @SuppressWarnings("unchecked")
    public Dao<KeyValuePair, String> getKvPairDao() throws SQLException {
        if (kvPairDao == null) {
            kvPairDao = getDao(KeyValuePair.class);
        }
        return (Dao<KeyValuePair, String>) kvPairDao;
    }

    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            TableUtils.createTable(connectionSource, Profile.class);
            TableUtils.createTable(connectionSource, KeyValuePair.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void recreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            TableUtils.dropTable(connectionSource, Profile.class, true);
            TableUtils.dropTable(connectionSource, KeyValuePair.class, true);
            onCreate(database, connectionSource);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                TableUtils.createTable(connectionSource, Profile.class);
                getProfileDao().executeRawNoArgs(
                        "INSERT INTO `profile`(id, name, host, localPort, remotePort, password, method, route, proxyApps, bypass," +
                                " udpdns, ipv6, individual) " +
                                "SELECT id, name, host, localPort, remotePort, password, method, route, 1 - global, bypass, udpdns, ipv6," +
                                " individual FROM `tmp`;");
                getProfileDao().executeRawNoArgs("DROP TABLE `tmp`;");
                getProfileDao().executeRawNoArgs("COMMIT;");
            } else if (oldVersion < 13) {
                getProfileDao().executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN tx LONG;");
                getProfileDao().executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN rx LONG;");
                getProfileDao().executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN date VARCHAR;");
            }

            if (oldVersion < 15) {
                if (oldVersion >= 12) {
                    getProfileDao().executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN userOrder LONG;");
                }
                long i = 0;
                List<ApplicationInfo> apps = Arrays.asList(app.getPackageManager().getInstalledApplications(0));
                List<Profile> profiles = getProfileDao().queryForAll();
                for (Profile profile : profiles) {
                    if (oldVersion < 14) {
                        Set<Integer> uidSet = new HashSet<>(Arrays.stream(profile.individual.split("\\|"))
                                .filter(TextUtils::isDigitsOnly)
                                .map(Integer::parseInt)
                                .collect(Collectors.toList()));
                        List<ApplicationInfo> filteredApps = apps.stream()
                                .filter(appInfo -> uidSet.contains(appInfo.uid))
                                .collect(Collectors.toList());
                        profile.individual = String.join("\n", filteredApps.stream()
                                .map(ApplicationInfo::packageName)
                                .collect(Collectors.toList()));
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

                getProfileDao().executeRawNoArgs("BEGIN TRANSACTION;");
                getProfileDao().executeRawNoArgs("ALTER TABLE `profile` RENAME TO `tmp`;");
                TableUtils.createTable(connectionSource, Profile.class);
                getProfileDao().executeRawNoArgs(
                        "INSERT INTO `profile`(id, name, host, localPort, remotePort, password, method, route, " +
                                "remoteDns, proxyApps, bypass, udpdns, ipv6, individual, tx, rx, date, userOrder, " +
                                "plugin) " +
                                "SELECT id, name, host, localPort, " +
                                "CASE WHEN kcp = 1 THEN kcpPort ELSE remotePort END, password, method, route, " +
                                "remoteDns, proxyApps, bypass, udpdns, ipv6, individual, tx, rx, date, userOrder, " +
                                "CASE WHEN kcp = 1 THEN 'kcptun ' || kcpcli ELSE NULL END FROM `tmp`;");
                getProfileDao().executeRawNoArgs("DROP TABLE `tmp`;");
                getProfileDao().executeRawNoArgs("COMMIT;");
            }

            if (oldVersion < 23) {
                getProfileDao().executeRawNoArgs("BEGIN TRANSACTION;");
                TableUtils.createTable(connectionSource, KeyValuePair.class);
                getProfileDao().executeRawNoArgs("COMMIT;");
                SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(app);
                getKvPairDao().createOrUpdate(new KeyValuePair(Key.id, defaultSharedPreferences.getInt(Key.id, 0)));
                getKvPairDao().createOrUpdate(new KeyValuePair(Key.tfo, defaultSharedPreferences.getBoolean(Key.tfo, false)));
            }
        } catch (Exception e) {
            e.printStackTrace();
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