
package com.google.samples.apps.sunflower.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.google.samples.apps.sunflower.data.GardenPlantingRepository;
import com.google.samples.apps.sunflower.data.PlantAndGardenPlantings;

public class GardenPlantingListViewModel extends ViewModel {
    private LiveData<List<PlantAndGardenPlantings>> plantAndGardenPlantings;

    public GardenPlantingListViewModel(GardenPlantingRepository gardenPlantingRepository) {
        plantAndGardenPlantings = gardenPlantingRepository.getPlantedGardens();
    }

    public LiveData<List<PlantAndGardenPlantings>> getPlantAndGardenPlantings() {
        return plantAndGardenPlantings;
    }
}
