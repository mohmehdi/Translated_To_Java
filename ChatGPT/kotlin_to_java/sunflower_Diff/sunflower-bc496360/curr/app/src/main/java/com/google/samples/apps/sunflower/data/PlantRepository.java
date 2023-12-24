
package com.google.samples.apps.sunflower.data;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PlantRepository {

    private PlantDao plantDao;

    @Inject
    public PlantRepository(PlantDao plantDao) {
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

    private static volatile PlantRepository instance;

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
