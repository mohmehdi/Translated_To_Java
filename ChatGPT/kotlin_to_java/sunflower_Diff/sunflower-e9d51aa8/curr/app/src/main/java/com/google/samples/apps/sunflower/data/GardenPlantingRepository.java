package com.google.samples.apps.sunflower.data;

import com.google.samples.apps.sunflower.utilities.runOnIoThread;
import java.util.Calendar;

public class GardenPlantingRepository {
    private GardenPlantingDao gardenPlantingDao;

    private GardenPlantingRepository(GardenPlantingDao gardenPlantingDao) {
        this.gardenPlantingDao = gardenPlantingDao;
    }

    public void createGardenPlanting(String plantId) {
        runOnIoThread(new Runnable() {
            @Override
            public void run() {
                GardenPlanting gardenPlanting = new GardenPlanting("gp" + plantId, plantId);
                gardenPlantingDao.insertGardenPlanting(gardenPlanting);
            }
        });
    }

    public GardenPlanting getGardenPlantingForPlant(String plantId) {
        return gardenPlantingDao.getGardenPlantingForPlant(plantId);
    }

    private static volatile GardenPlantingRepository instance;

    public static GardenPlantingRepository getInstance(GardenPlantingDao gardenPlantingDao) {
        if (instance == null) {
            synchronized (GardenPlantingRepository.class) {
                if (instance == null) {
                    instance = new GardenPlantingRepository(gardenPlantingDao);
                }
            }
        }
        return instance;
    }
}