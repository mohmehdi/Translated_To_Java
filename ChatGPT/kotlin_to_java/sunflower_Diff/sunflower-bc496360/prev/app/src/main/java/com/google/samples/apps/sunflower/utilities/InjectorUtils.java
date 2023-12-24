
package com.google.samples.apps.sunflower.utilities;

import android.content.Context;
import androidx.fragment.app.Fragment;
import com.google.samples.apps.sunflower.data.AppDatabase;
import com.google.samples.apps.sunflower.data.GardenPlantingRepository;
import com.google.samples.apps.sunflower.data.PlantRepository;
import com.google.samples.apps.sunflower.viewmodels.GardenPlantingListViewModelFactory;
import com.google.samples.apps.sunflower.viewmodels.PlantDetailViewModelFactory;
import com.google.samples.apps.sunflower.viewmodels.PlantListViewModelFactory;

public class InjectorUtils {

    private static PlantRepository getPlantRepository(Context context) {
        return PlantRepository.getInstance(
            AppDatabase.getInstance(context.getApplicationContext()).plantDao()
        );
    }

    private static GardenPlantingRepository getGardenPlantingRepository(Context context) {
        return GardenPlantingRepository.getInstance(
            AppDatabase.getInstance(context.getApplicationContext()).gardenPlantingDao()
        );
    }

    public static GardenPlantingListViewModelFactory provideGardenPlantingListViewModelFactory(
        Context context
    ) {
        return new GardenPlantingListViewModelFactory(getGardenPlantingRepository(context));
    }

    public static PlantListViewModelFactory providePlantListViewModelFactory(Fragment fragment) {
        return new PlantListViewModelFactory(getPlantRepository(fragment.requireContext()), fragment);
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
