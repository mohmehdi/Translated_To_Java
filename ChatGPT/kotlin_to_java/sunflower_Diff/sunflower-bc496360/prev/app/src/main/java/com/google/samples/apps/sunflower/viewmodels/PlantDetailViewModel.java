
package com.google.samples.apps.sunflower.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewModelScope;

import com.google.samples.apps.sunflower.data.GardenPlantingRepository;
import com.google.samples.apps.sunflower.data.PlantRepository;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PlantDetailViewModel extends ViewModel {
    private GardenPlantingRepository gardenPlantingRepository;
    private String plantId;

    private MutableLiveData<Boolean> _showSnackbar = new MutableLiveData<>(false);
    public LiveData<Boolean> showSnackbar = _showSnackbar;

    public PlantDetailViewModel(PlantRepository plantRepository, GardenPlantingRepository gardenPlantingRepository, String plantId) {
        this.gardenPlantingRepository = gardenPlantingRepository;
        this.plantId = plantId;
    }

    public LiveData<Boolean> isPlanted() {
        return gardenPlantingRepository.isPlanted(plantId);
    }

    public LiveData<Plant> getPlant() {
        return plantRepository.getPlant(plantId);
    }

    public void addPlantToGarden() {
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            gardenPlantingRepository.createGardenPlanting(plantId);
            _showSnackbar.postValue(true);
        });
    }

    public void dismissSnackbar() {
        _showSnackbar.setValue(false);
    }
}
