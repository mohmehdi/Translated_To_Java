
package com.google.samples.apps.sunflower.data;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GardenPlantingRepository {

    private GardenPlantingDao gardenPlantingDao;

    @Inject
    public GardenPlantingRepository(GardenPlantingDao gardenPlantingDao) {
        this.gardenPlantingDao = gardenPlantingDao;
    }

    public void createGardenPlanting(String plantId) {
        GardenPlanting gardenPlanting = new GardenPlanting(plantId);
        gardenPlantingDao.insertGardenPlanting(gardenPlanting);
    }

    public void removeGardenPlanting(GardenPlanting gardenPlanting) {
        gardenPlantingDao.deleteGardenPlanting(gardenPlanting);
    }

    public boolean isPlanted(String plantId) {
        return gardenPlantingDao.isPlanted(plantId);
    }

    public List<GardenPlanting> getPlantedGardens() {
        return gardenPlantingDao.getPlantedGardens();
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
