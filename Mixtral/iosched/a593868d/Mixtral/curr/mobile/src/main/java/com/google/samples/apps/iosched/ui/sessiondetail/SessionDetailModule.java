package com.google.samples.apps.iosched.ui.sessiondetail;

import android.arch.lifecycle.ViewModel;
import com.google.samples.apps.iosched.shared.di.FragmentScoped;
import com.google.samples.apps.iosched.di.ViewModelKey;
import dagger.Binds;
import dagger.MapKey;
import dagger.Module;
import dagger.android.ContributesAndroidInjector;

import javax.inject.Inject;
import javax.inject.Provider;
import dagger.multibindings.IntoMap;

@Module
abstract class SessionDetailModule {

    @FragmentScoped
    @ContributesAndroidInjector
    abstract SessionDetailFragment contributeSessionDetailFragment();

    @Binds
    @IntoMap
    @ViewModelKey(SessionDetailViewModel.class)
    abstract ViewModel bindSessionDetailFragmentViewModel(SessionDetailViewModel viewModel);
}
