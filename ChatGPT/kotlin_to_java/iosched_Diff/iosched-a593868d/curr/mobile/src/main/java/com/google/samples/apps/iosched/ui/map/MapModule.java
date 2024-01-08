package com.google.samples.apps.iosched.ui.map;

import android.arch.lifecycle.ViewModel;
import com.google.samples.apps.iosched.shared.di.FragmentScoped;
import com.google.samples.apps.iosched.di.ViewModelKey;
import dagger.Binds;
import dagger.Module;
import dagger.android.AndroidInjector;
import dagger.android.ContributesAndroidInjector;
import dagger.multibindings.IntoMap;

@Module
public abstract class MapModule {

    @FragmentScoped
    @ContributesAndroidInjector
    public abstract MapFragment contributeMapFragment();

    @Binds
    @IntoMap
    @ViewModelKey(MapViewModel.class)
    public abstract ViewModel bindMapFragmentViewModel(MapViewModel viewModel);
}