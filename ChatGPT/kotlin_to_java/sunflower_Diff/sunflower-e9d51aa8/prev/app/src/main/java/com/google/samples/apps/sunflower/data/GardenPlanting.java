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
    private String gardenPlantingId;
    @ColumnInfo(name = "plant_id")
    private String plantId;
    private Calendar plantDate;
    private Calendar lastWateringDate;

    public GardenPlanting(String gardenPlantingId, String plantId) {
        this.gardenPlantingId = gardenPlantingId;
        this.plantId = plantId;
        this.plantDate = Calendar.getInstance();
        this.lastWateringDate = Calendar.getInstance();
    }

    public String getGardenPlantingId() {
        return gardenPlantingId;
    }

    public void setGardenPlantingId(String gardenPlantingId) {
        this.gardenPlantingId = gardenPlantingId;
    }

    public String getPlantId() {
        return plantId;
    }

    public void setPlantId(String plantId) {
        this.plantId = plantId;
    }

    public Calendar getPlantDate() {
        return plantDate;
    }

    public void setPlantDate(Calendar plantDate) {
        this.plantDate = plantDate;
    }

    public Calendar getLastWateringDate() {
        return lastWateringDate;
    }

    public void setLastWateringDate(Calendar lastWateringDate) {
        this.lastWateringDate = lastWateringDate;
    }
}