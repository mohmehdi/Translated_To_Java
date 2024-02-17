

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
import java.util.Set;
import java.util.UUID;

public class PrivateDatabase extends OrmLiteSqliteOpenHelper {
    private App app;

    public PrivateDatabase(App app, String databaseName, ConnectionSource connectionSource, int databaseVersion) {
        super(app, databaseName, connectionSource, databaseVersion);
        this.app = app;
    }

    @Suppress("unchecked")
    private Dao<Profile, Integer> profileDao;

    @Suppress("unchecked")
    private Dao<KeyValuePair, String> kvPairDao;

    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            TableUtils.createTable(connectionSource, Profile.class);
            TableUtils.createTable(connectionSource, KeyValuePair.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void recreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            TableUtils.dropTable(connectionSource, Profile.class, true);
            TableUtils.dropTable(connectionSource, KeyValuePair.class, true);
            onCreate(database, connectionSource);
        } catch (SQLException e) {
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
                profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN udpdns SMALLINT;");
            }
            if (oldVersion < 9) {
                profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN route VARCHAR DEFAULT 'all';");
            } else if (oldVersion < 19) {
                profileDao.executeRawNoArgs("UPDATE `profile` SET route = 'all' WHERE route IS NULL;");
            }
            if (oldVersion < 11) {
                profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN ipv6 SMALLINT;");
            }
            if (oldVersion < 12) {
                profileDao.executeRawNoArgs("BEGIN TRANSACTION;");
                profileDao.executeRawNoArgs("ALTER TABLE `profile` RENAME TO `tmp`;");
                TableUtils.createTable(connectionSource, Profile.class);
                profileDao.executeRawNoArgs(
                        "INSERT INTO `profile`(id, name, host, localPort, remotePort, password, method, route, proxyApps, bypass," +
                                " udpdns, ipv6, individual) " +
                                "SELECT id, name, host, localPort, remotePort, password, method, route, 1 - global, bypass, udpdns, ipv6," +
                                " individual FROM `tmp`;");
                profileDao.executeRawNoArgs("DROP TABLE `tmp`;");
                profileDao.executeRawNoArgs("COMMIT;");
            } else if (oldVersion < 13) {
                profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN tx LONG;");
                profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN rx LONG;");
                profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN date VARCHAR;");
            }

            if (oldVersion < 15) {
                if (oldVersion >= 12) profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN userOrder LONG;");
                long i = 0;
                Set<ApplicationInfo> apps = app.getPackageManager().getInstalledApplications(0);
                for (Profile profile : profileDao.queryForAll()) {
                    if (oldVersion < 14) {
                        Set<Integer> uidSet = new HashSet<Integer>(Arrays.asList(profile.individual.split("\\|")).stream().filter(TextUtils::isDigitsOnly).map(Integer::parseInt).collect(Collectors.toList()));
                        profile.individual = apps.stream().filter(appInfo -> uidSet.contains(appInfo.uid)).map(ApplicationInfo::packageName).collect(Collectors.joining("\n"));
                    }
                    profile.userOrder = i;
                    profileDao.update(profile);
                    i += 1;
                }
            }

            if (oldVersion < 16) {
                profileDao.executeRawNoArgs(
                        "UPDATE `profile` SET route = 'bypass-lan-china' WHERE route = 'bypass-china'");
            }

            if (oldVersion < 21) {
                profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN remoteDns VARCHAR DEFAULT '8.8.8.8';");
            }

            if (oldVersion < 17) {
                profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN plugin VARCHAR;");
            } else if (oldVersion < 22) {
                profileDao.executeRawNoArgs("BEGIN TRANSACTION;");
                profileDao.executeRawNoArgs("ALTER TABLE `profile` RENAME TO `tmp`;");
                TableUtils.createTable(connectionSource, Profile.class);
                profileDao.executeRawNoArgs(
                        "INSERT INTO `profile`(id, name, host, localPort, remotePort, password, method, route, " +
                                "remoteDns, proxyApps, bypass, udpdns, ipv6, individual, tx, rx, date, userOrder, " +
                                "plugin) " +
                                "SELECT id, name, host, localPort, " +
                                "CASE WHEN kcp = 1 THEN kcpPort ELSE remotePort END, password, method, route, " +
                                "remoteDns, proxyApps, bypass, udpdns, ipv6, individual, tx, rx, date, userOrder, " +
                                "CASE WHEN kcp = 1 THEN 'kcptun ' || kcpcli ELSE NULL END FROM `tmp`;");
                profileDao.executeRawNoArgs("DROP TABLE `tmp`;");
                profileDao.executeRawNoArgs("COMMIT;");
            }

            if (oldVersion < 23) {
                profileDao.executeRawNoArgs("BEGIN TRANSACTION;");
                TableUtils.createTable(connectionSource, KeyValuePair.class);
                profileDao.executeRawNoArgs("COMMIT;");
                SharedPreferences old = PreferenceManager.getDefaultSharedPreferences(app);
                kvPairDao.createOrUpdate(new KeyValuePair(Key.id).put(old.getInt(Key.id, 0)));
                kvPairDao.createOrUpdate(new KeyValuePair(Key.tfo).put(old.getBoolean(Key.tfo, false)));
            }

            if (oldVersion < 25) {
                PublicDatabase.onUpgrade(database, 0, -1);
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