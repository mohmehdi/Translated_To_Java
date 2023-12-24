
package com.google.samples.apps.sunflower.viewmodels;

import android.os.Bundle;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.savedstate.SavedStateRegistryOwner;
import com.google.samples.apps.sunflower.data.PlantRepository;

public class PlantListViewModelFactory extends AbstractSavedStateViewModelFactory {

    private PlantRepository repository;

    public PlantListViewModelFactory(
            PlantRepository repository,
            SavedStateRegistryOwner owner,
            Bundle defaultArgs
    ) {
        super(owner, defaultArgs);
        this.repository = repository;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends ViewModel> T create(String key, Class<T> modelClass, SavedStateHandle handle) {
        return (T) new PlantListViewModel(repository, handle);
    }
}
