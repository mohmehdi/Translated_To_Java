
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

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.List;

import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.withContext;

public class SeedDatabaseWorker extends CoroutineWorker {
    private static final String TAG = "SeedDatabaseWorker";
    private static final String KEY_FILENAME = "PLANT_DATA_FILENAME";

    public SeedDatabaseWorker(Context context, WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @Override
    public Result doWork() {
        try {
            String filename = getInputData().getString(KEY_FILENAME);
            if (filename != null) {
                Context applicationContext = getApplicationContext();
                InputStreamReader inputStreamReader = new InputStreamReader(applicationContext.getAssets().open(filename));
                JsonReader jsonReader = new JsonReader(inputStreamReader);

                Type plantType = new TypeToken<List<Plant>>() {}.getType();
                List<Plant> plantList = new Gson().fromJson(jsonReader, plantType);

                AppDatabase database = AppDatabase.getInstance(applicationContext);
                database.plantDao().insertAll(plantList);

                return Result.success();
            } else {
                Log.e(TAG, "Error seeding database - no valid filename");
                return Result.failure();
            }
        } catch (IOException ex) {
            Log.e(TAG, "Error seeding database", ex);
            return Result.failure();
        }
    }
}
