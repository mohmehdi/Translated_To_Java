
package com.google.samples.apps.sunflower.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.work.OneTimeWorkRequestBuilder;
import androidx.work.WorkManager;

import com.google.samples.apps.sunflower.utilities.DATABASE_NAME;
import com.google.samples.apps.sunflower.workers.SeedDatabaseWorker;

@Database(entities = {GardenPlanting.class, Plant.class}, version = 1, exportSchema = false)
@TypeConverters(Converters.class)
public abstract class AppDatabase extends RoomDatabase {
    public abstract GardenPlantingDao gardenPlantingDao();
    public abstract PlantDao plantDao();

    private static volatile AppDatabase instance;

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = buildDatabase(context);
        }
        return instance;
    }

    private static AppDatabase buildDatabase(final Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, DATABASE_NAME)
                .addCallback(new RoomDatabase.Callback() {
                    @Override
                    public void onCreate(@NonNull SupportSQLiteDatabase db) {
                        super.onCreate(db);
                        OneTimeWorkRequestBuilder<SeedDatabaseWorker> request =
                                new OneTimeWorkRequestBuilder<SeedDatabaseWorker>().build();
                        WorkManager.getInstance(context).enqueue(request);
                    }
                })
                .build();
    }
}
