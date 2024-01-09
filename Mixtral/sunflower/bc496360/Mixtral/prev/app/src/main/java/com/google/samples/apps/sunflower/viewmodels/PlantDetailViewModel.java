package com.google.samples.apps.sunflower.viewmodels;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelScope;
import com.google.samples.apps.sunflower.data.GardenPlantingRepository;
import com.google.samples.apps.sunflower.data.PlantRepository;

public class PlantDetailViewModel extends ViewModel {

  private final PlantRepository plantRepository;
  private final GardenPlantingRepository gardenPlantingRepository;
  private final String plantId;

  public final LiveData isPlanted;
  public final LiveData<com.google.samples.apps.sunflower.models.Plant> plant;
  public final LiveData showSnackbar;

  public PlantDetailViewModel(
    @NonNull PlantRepository plantRepository,
    @NonNull GardenPlantingRepository gardenPlantingRepository,
    @NonNull String plantId
  ) {
    this.plantRepository = plantRepository;
    this.gardenPlantingRepository = gardenPlantingRepository;
    this.plantId = plantId;
    this.isPlanted = this.gardenPlantingRepository.isPlanted(plantId);
    this.plant = this.plantRepository.getPlant(plantId);
    this.showSnackbar = new MutableLiveData(false);
  }

  public void addPlantToGarden() {
    ViewModelScope.launch(new ViewModelCoroutineScope() {});
    gardenPlantingRepository.createGardenPlanting(plantId);
    showSnackbar.postValue(true);
  }

  public void dismissSnackbar() {
    showSnackbar.postValue(false);
  }

  public static class PlantDetailViewModelFactory
    implements ViewModelProvider.Factory {

    private final PlantRepository plantRepository;
    private final GardenPlantingRepository gardenPlantingRepository;

    public PlantDetailViewModelFactory(
      PlantRepository plantRepository,
      GardenPlantingRepository gardenPlantingRepository
    ) {
      this.plantRepository = plantRepository;
      this.gardenPlantingRepository = gardenPlantingRepository;
    }

    @NonNull
    @Override
    public T create(@NonNull Class modelClass) {
      if (modelClass.isAssignableFrom(PlantDetailViewModel.class)) {
        return (T) new PlantDetailViewModel(
          plantRepository,
          gardenPlantingRepository,
          null
        );
      }
      throw new IllegalArgumentException("Unknown ViewModel class");
    }
  }
}
