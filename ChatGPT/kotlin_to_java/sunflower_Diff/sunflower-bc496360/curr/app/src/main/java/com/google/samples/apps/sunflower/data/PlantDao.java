
package com.google.samples.apps.sunflower.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

import kotlinx.coroutines.flow.Flow;

@Dao
public interface PlantDao {
    @Query("SELECT * FROM plants ORDER BY name")
    Flow<List<Plant>> getPlants();

    @Query("SELECT * FROM plants WHERE growZoneNumber = :growZoneNumber ORDER BY name")
    Flow<List<Plant>> getPlantsWithGrowZoneNumber(int growZoneNumber);

    @Query("SELECT * FROM plants WHERE id = :plantId")
    Flow<Plant> getPlant(String plantId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Plant> plants);
}
