
package com.google.samples.apps.sunflower.data;

public class PlantRepository {
    private PlantDao plantDao;
    private static volatile PlantRepository instance;

    private PlantRepository(PlantDao plantDao) {
        this.plantDao = plantDao;
    }

    public List<Plant> getPlants() {
        return plantDao.getPlants();
    }

    public Plant getPlant(String plantId) {
        return plantDao.getPlant(plantId);
    }

    public List<Plant> getPlantsWithGrowZoneNumber(int growZoneNumber) {
        return plantDao.getPlantsWithGrowZoneNumber(growZoneNumber);
    }

    public static PlantRepository getInstance(PlantDao plantDao) {
        if (instance == null) {
            synchronized (PlantRepository.class) {
                if (instance == null) {
                    instance = new PlantRepository(plantDao);
                }
            }
        }
        return instance;
    }
}
