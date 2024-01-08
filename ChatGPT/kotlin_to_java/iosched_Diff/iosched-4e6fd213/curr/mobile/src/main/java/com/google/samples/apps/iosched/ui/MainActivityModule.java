package com.google.samples.apps.iosched.ui;

import androidx.lifecycle.ViewModel;
import com.google.samples.apps.iosched.shared.di.ViewModelKey;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

@Module
public abstract class MainActivityModule {

    @Binds
    @IntoMap
    @ViewModelKey(MainActivityViewModel.class)
    public abstract ViewModel bindViewModel(MainActivityViewModel viewModel);
}