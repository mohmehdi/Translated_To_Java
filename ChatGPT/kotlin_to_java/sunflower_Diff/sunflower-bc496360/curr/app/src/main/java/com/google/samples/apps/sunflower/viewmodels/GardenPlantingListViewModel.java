
package com.google.samples.apps.sunflower.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.asLiveData;
import com.google.samples.apps.sunflower.data.GardenPlantingRepository;
import com.google.samples.apps.sunflower.data.PlantAndGardenPlantings;
import dagger.hilt.android.lifecycle.HiltViewModel;
import javax.inject.Inject;

@HiltViewModel
public class GardenPlantingListViewModel extends ViewModel {
    private LiveData<List<PlantAndGardenPlantings>> plantAndGardenPlantings;

    @Inject
    public GardenPlantingListViewModel(GardenPlantingRepository gardenPlantingRepository) {
        plantAndGardenPlantings = gardenPlantingRepository.getPlantedGardens().asLiveData();
    }

    public LiveData<List<PlantAndGardenPlantings>> getPlantAndGardenPlantings() {
        return plantAndGardenPlantings;
    }
}
