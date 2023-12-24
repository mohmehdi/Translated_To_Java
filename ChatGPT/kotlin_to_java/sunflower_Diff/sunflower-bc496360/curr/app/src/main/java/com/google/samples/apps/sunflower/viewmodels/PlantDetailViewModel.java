
package com.google.samples.apps.sunflower.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.viewModelScope;

import com.google.samples.apps.sunflower.BuildConfig;
import com.google.samples.apps.sunflower.data.GardenPlantingRepository;
import com.google.samples.apps.sunflower.data.PlantRepository;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import kotlinx.coroutines.launch;

@HiltViewModel
public class PlantDetailViewModel extends ViewModel {

    private SavedStateHandle savedStateHandle;
    private PlantRepository plantRepository;
    private GardenPlantingRepository gardenPlantingRepository;

    private String plantId;

    private MutableLiveData<Boolean> _showSnackbar = new MutableLiveData<>(false);
    public LiveData<Boolean> showSnackbar = _showSnackbar;

    @Inject
    public PlantDetailViewModel(SavedStateHandle savedStateHandle, PlantRepository plantRepository, GardenPlantingRepository gardenPlantingRepository) {
        this.savedStateHandle = savedStateHandle;
        this.plantRepository = plantRepository;
        this.gardenPlantingRepository = gardenPlantingRepository;

        plantId = savedStateHandle.get("plantId");

        isPlanted = gardenPlantingRepository.isPlanted(plantId).asLiveData();
        plant = plantRepository.getPlant(plantId).asLiveData();
    }

    public void addPlantToGarden() {
        viewModelScope.launch {
            gardenPlantingRepository.createGardenPlanting(plantId);
            _showSnackbar.setValue(true);
        }
    }

    public void dismissSnackbar() {
        _showSnackbar.setValue(false);
    }

    public boolean hasValidUnsplashKey() {
        return !BuildConfig.UNSPLASH_ACCESS_KEY.equals("null");
    }

    private static final String PLANT_ID_SAVED_STATE_KEY = "plantId";
}
