
package com.google.samples.apps.sunflower.utilities;

import android.app.Application;
import com.google.samples.apps.sunflower.data.AppDatabase;
import com.google.samples.apps.sunflower.data.PlantRepository;
import com.google.samples.apps.sunflower.viewmodels.PlantDetailViewModelFactory;
import com.google.samples.apps.sunflower.viewmodels.PlantListViewModelFactory;

public class InjectorUtils {

    private static PlantRepository provideRepository(Application application) {
        return PlantRepository.getInstance(AppDatabase.getInstance(application).plantDao());
    }

    public static PlantListViewModelFactory providePlantListViewModelFactory(
            Application application
    ) {
        PlantRepository repository = provideRepository(application);
        return new PlantListViewModelFactory(repository);
    }

    public static PlantDetailViewModelFactory providePlantDetailViewModelFactory(
            Application application,
            String plantId
    ) {
        PlantRepository repository = provideRepository(application);
        return new PlantDetailViewModelFactory(repository, plantId);
    }
}
