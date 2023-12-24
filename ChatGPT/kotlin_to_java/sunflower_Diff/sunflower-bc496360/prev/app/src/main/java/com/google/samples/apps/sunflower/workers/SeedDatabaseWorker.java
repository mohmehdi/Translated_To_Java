
package com.google.samples.apps.sunflower.workers;

import android.content.Context;
import android.util.Log;

import androidx.work.CoroutineWorker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.samples.apps.sunflower.data.AppDatabase;
import com.google.samples.apps.sunflower.data.Plant;
import com.google.samples.apps.sunflower.utilities.PLANT_DATA_FILENAME;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.List;

import kotlin.jvm.Throws;
import kotlinx.coroutines.coroutineScope;

public class SeedDatabaseWorker extends CoroutineWorker {
    private static final String TAG = "SeedDatabaseWorker";

    public SeedDatabaseWorker(Context context, WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @Override
    @Throws(Exception.class)
    public Result doWork() {
        try {
            InputStream inputStream = getApplicationContext().getAssets().open(PLANT_DATA_FILENAME);
            JsonReader jsonReader = new JsonReader(new InputStreamReader(inputStream));
            Type plantType = new TypeToken<List<Plant>>() {}.getType();
            List<Plant> plantList = new Gson().fromJson(jsonReader, plantType);

            AppDatabase database = AppDatabase.getInstance(getApplicationContext());
            database.plantDao().insertAll(plantList);

            return Result.success();
        } catch (IOException ex) {
            Log.e(TAG, "Error seeding database", ex);
            return Result.failure();
        }
    }
}
