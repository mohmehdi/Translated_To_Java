
package com.google.samples.apps.sunflower.viewmodels;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.google.samples.apps.sunflower.data.GardenPlantingRepository;

public class GardenPlantingListViewModelFactory implements ViewModelProvider.Factory {

    private GardenPlantingRepository repository;

    public GardenPlantingListViewModelFactory(GardenPlantingRepository repository) {
        this.repository = repository;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends ViewModel> T create(Class<T> modelClass) {
        return (T) new GardenPlantingListViewModel(repository);
    }
}
