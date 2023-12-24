
package com.google.samples.apps.sunflower.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

import kotlinx.coroutines.flow.Flow;

@Dao
public interface GardenPlantingDao {
    @Query("SELECT * FROM garden_plantings")
    Flow<List<GardenPlanting>> getGardenPlantings();

    @Query("SELECT EXISTS(SELECT 1 FROM garden_plantings WHERE plant_id = :plantId LIMIT 1)")
    Flow<Boolean> isPlanted(String plantId);

    @Transaction
    @Query("SELECT * FROM plants WHERE id IN (SELECT DISTINCT(plant_id) FROM garden_plantings)")
    Flow<List<PlantAndGardenPlantings>> getPlantedGardens();

    @Insert
    Long insertGardenPlanting(GardenPlanting gardenPlanting);

    @Delete
    void deleteGardenPlanting(GardenPlanting gardenPlanting);
}
