package com.google.samples.apps.sunflower.utilities;

import android.content.Context;
import com.google.samples.apps.sunflower.data.AppDatabase;
import com.google.samples.apps.sunflower.data.GardenPlantingRepository;
import com.google.samples.apps.sunflower.data.PlantRepository;
import com.google.samples.apps.sunflower.viewmodels.PlantDetailViewModelFactory;
import com.google.samples.apps.sunflower.viewmodels.PlantListViewModelFactory;

public class InjectorUtils {

  private static PlantRepository getPlantRepository(Context context) {
    return PlantRepository.getInstance(
      AppDatabase.getInstance(context).plantDao()
    );
  }

  private static GardenPlantingRepository getGardenPlantingRepository(
    Context context
  ) {
    return GardenPlantingRepository.getInstance(
      AppDatabase.getInstance(context).gardenPlantingDao()
    );
  }

  public static PlantListViewModelFactory providePlantListViewModelFactory(
    Context context
  ) {
    PlantRepository repository = getPlantRepository(context);
    return new PlantListViewModelFactory(repository);
  }

  public static PlantDetailViewModelFactory providePlantDetailViewModelFactory(
    Context context,
    String plantId
  ) {
    return new PlantDetailViewModelFactory(
      getPlantRepository(context),
      getGardenPlantingRepository(context),
      plantId
    );
  }
}
