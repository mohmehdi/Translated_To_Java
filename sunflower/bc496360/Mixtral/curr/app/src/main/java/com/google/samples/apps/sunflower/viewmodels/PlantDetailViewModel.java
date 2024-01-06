package com.google.samples.apps.sunflower.viewmodels;

import android.content.res.Resources;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.asLiveData;
import androidx.lifecycle.viewModelScope;
import com.google.samples.apps.sunflower.BuildConfig;
import com.google.samples.apps.sunflower.data.GardenPlantingRepository;
import com.google.samples.apps.sunflower.data.PlantRepository;
import java.util.concurrent.Executor;

@dagger.hilt.android.lifecycle.HiltViewModel
public class PlantDetailViewModel extends ViewModel {

  private static final String PLANT_ID_SAVED_STATE_KEY = "plantId";

  private final PlantRepository plantRepository;
  private final GardenPlantingRepository gardenPlantingRepository;
  private final SavedStateHandle savedStateHandle;
  private final MutableLiveData<Boolean> _showSnackbar = new MutableLiveData<>(
    false
  );
  public final LiveData<Boolean> showSnackbar;

  @Inject
  public PlantDetailViewModel(
    SavedStateHandle savedStateHandle,
    PlantRepository plantRepository,
    GardenPlantingRepository gardenPlantingRepository
  ) {
    this.plantRepository = plantRepository;
    this.gardenPlantingRepository = gardenPlantingRepository;
    this.savedStateHandle = savedStateHandle;
    this.showSnackbar = _showSnackbar;
  }

  public String getPlantId() {
    return savedStateHandle.get(PLANT_ID_SAVED_STATE_KEY);
  }

  public LiveData<Boolean> isPlanted() {
    return gardenPlantingRepository.isPlanted(getPlantId()).asLiveData();
  }

  public LiveData<com.google.samples.apps.sunflower.data.Plant> getPlant() {
    return plantRepository.getPlant(getPlantId()).asLiveData();
  }

  public void addPlantToGarden() {
    viewModelScope.launch(
      new Executor() {
        @Override
        public void execute(@NonNull Runnable command) {
          command.run();
        }
      }
    );
    gardenPlantingRepository.createGardenPlanting(getPlantId());
    _showSnackbar.postValue(true);
  }

  public void dismissSnackbar() {
    _showSnackbar.postValue(false);
  }

  public boolean hasValidUnsplashKey() {
    return (
      BuildConfig.UNSPLASH_ACCESS_KEY != null &&
      !BuildConfig.UNSPLASH_ACCESS_KEY.equals("null")
    );
  }
}
