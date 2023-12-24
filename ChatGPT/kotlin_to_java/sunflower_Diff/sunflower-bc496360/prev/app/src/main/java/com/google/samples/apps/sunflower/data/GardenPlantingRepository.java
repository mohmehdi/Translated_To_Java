
package com.google.samples.apps.sunflower.data;

public class GardenPlantingRepository {
    private GardenPlantingDao gardenPlantingDao;
    private static volatile GardenPlantingRepository instance;

    private GardenPlantingRepository(GardenPlantingDao gardenPlantingDao) {
        this.gardenPlantingDao = gardenPlantingDao;
    }

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
}
