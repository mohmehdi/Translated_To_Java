
package com.google.samples.apps.sunflower.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.asLiveData;
import androidx.lifecycle.viewModelScope;
import com.google.samples.apps.sunflower.data.Plant;
import com.google.samples.apps.sunflower.data.PlantRepository;
import dagger.hilt.android.lifecycle.HiltViewModel;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.collect;
import kotlinx.coroutines.flow.flatMapLatest;
import kotlinx.coroutines.launch;
import javax.inject.Inject;

@HiltViewModel
public class PlantListViewModel extends ViewModel {

    private MutableStateFlow<Integer> growZone = new MutableStateFlow<>(
            savedStateHandle.get(GROW_ZONE_SAVED_STATE_KEY) != null ? savedStateHandle.get(GROW_ZONE_SAVED_STATE_KEY) : NO_GROW_ZONE
    );

    private LiveData<List<Plant>> plants = growZone.flatMapLatest(zone -> {
        if (zone == NO_GROW_ZONE) {
            return plantRepository.getPlants();
        } else {
            return plantRepository.getPlantsWithGrowZoneNumber(zone);
        }
    }).asLiveData();

    @Inject
    public PlantListViewModel(PlantRepository plantRepository, SavedStateHandle savedStateHandle) {
        this.plantRepository = plantRepository;
        this.savedStateHandle = savedStateHandle;
    }

    private void init() {
        viewModelScope.launch(() -> {
            growZone.collect(newGrowZone -> {
                savedStateHandle.set(GROW_ZONE_SAVED_STATE_KEY, newGrowZone);
            });
        });
    }

    public void setGrowZoneNumber(int num) {
        growZone.setValue(num);
    }

    public void clearGrowZoneNumber() {
        growZone.setValue(NO_GROW_ZONE);
    }

    public boolean isFiltered() {
        return growZone.getValue() != NO_GROW_ZONE;
    }

    private static final int NO_GROW_ZONE = -1;
    private static final String GROW_ZONE_SAVED_STATE_KEY = "GROW_ZONE_SAVED_STATE_KEY";
}
