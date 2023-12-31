package com.google.samples.apps.sunflower.data;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.PrimaryKey;

import java.util.Calendar;

@Entity(tableName = "garden_plantings", foreignKeys = {@ForeignKey(entity = Plant.class,
        parentColumns = {"id"}, childColumns = {"plant_id"})})
public class GardenPlanting {

    @PrimaryKey
    @ColumnInfo(name = "id")
    private final String gardenPlantingId;

    @ColumnInfo(name = "plant_id")
    private final String plantId;

    @ColumnInfo(name = "plant_date")
    private final Calendar plantDate;

    @ColumnInfo(name = "last_watering_date")
    private final Calendar lastWateringDate;

    public GardenPlanting(String gardenPlantingId, String plantId, Calendar plantDate, Calendar lastWateringDate) {
        this.gardenPlantingId = gardenPlantingId;
        this.plantId = plantId;
        this.plantDate = plantDate;
        this.lastWateringDate = lastWateringDate;
    }

    public String getGardenPlantingId() {
        return gardenPlantingId;
    }

    public String getPlantId() {
        return plantId;
    }

    public Calendar getPlantDate() {
        return plantDate;
    }

    public Calendar getLastWateringDate() {
        return lastWateringDate;
    }
}