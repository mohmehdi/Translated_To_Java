
package com.google.samples.apps.sunflower.di;

import android.content.Context;
import com.google.samples.apps.sunflower.data.AppDatabase;
import com.google.samples.apps.sunflower.data.GardenPlantingDao;
import com.google.samples.apps.sunflower.data.PlantDao;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Singleton;

@InstallIn(SingletonComponent.class)
@Module
public class DatabaseModule {

    @Singleton
    @Provides
    public AppDatabase provideAppDatabase(@ApplicationContext Context context) {
        return AppDatabase.getInstance(context);
    }

    @Provides
    public PlantDao providePlantDao(AppDatabase appDatabase) {
        return appDatabase.plantDao();
    }

    @Provides
    public GardenPlantingDao provideGardenPlantingDao(AppDatabase appDatabase) {
        return appDatabase.gardenPlantingDao();
    }
}
