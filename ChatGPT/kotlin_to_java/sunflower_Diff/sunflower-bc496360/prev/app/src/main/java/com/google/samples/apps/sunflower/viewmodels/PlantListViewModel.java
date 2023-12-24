
package com.google.samples.apps.sunflower.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.switchMap;
import com.google.samples.apps.sunflower.PlantListFragment;
import com.google.samples.apps.sunflower.data.Plant;
import com.google.samples.apps.sunflower.data.PlantRepository;

public class PlantListViewModel extends ViewModel {
    private PlantRepository plantRepository;
    private SavedStateHandle savedStateHandle;
    private MutableLiveData<Integer> savedGrowZoneNumber;

    public PlantListViewModel(PlantRepository plantRepository, SavedStateHandle savedStateHandle) {
        this.plantRepository = plantRepository;
        this.savedStateHandle = savedStateHandle;
        this.savedGrowZoneNumber = getSavedGrowZoneNumber();
    }

    public LiveData<List<Plant>> getPlants() {
        return savedGrowZoneNumber.switchMap(num -> {
            if (num == NO_GROW_ZONE) {
                return plantRepository.getPlants();
            } else {
                return plantRepository.getPlantsWithGrowZoneNumber(num);
            }
        });
    }

    public void setGrowZoneNumber(int num) {
        savedStateHandle.set(GROW_ZONE_SAVED_STATE_KEY, num);
    }

    public void clearGrowZoneNumber() {
        savedStateHandle.set(GROW_ZONE_SAVED_STATE_KEY, NO_GROW_ZONE);
    }

    public boolean isFiltered() {
        return savedGrowZoneNumber.getValue() != NO_GROW_ZONE;
    }

    private MutableLiveData<Integer> getSavedGrowZoneNumber() {
        return savedStateHandle.getLiveData(GROW_ZONE_SAVED_STATE_KEY, NO_GROW_ZONE);
    }

    private static final int NO_GROW_ZONE = -1;
    private static final String GROW_ZONE_SAVED_STATE_KEY = "GROW_ZONE_SAVED_STATE_KEY";
}
