package com.google.samples.apps.iosched.ui.sessiondetail;

import android.arch.lifecycle.ViewModel;
import com.google.samples.apps.iosched.di.ViewModelKey;
import dagger.Binds;
import dagger.Module;
import dagger.android.ContributesAndroidInjector;
import dagger.multibindings.IntoMap;

@Module
public abstract class SessionDetailModule {

    @ContributesAndroidInjector
    abstract SessionDetailActivity sessionDetailActivity();

    @ContributesAndroidInjector
    abstract SessionDetailFragment contributeSessionDetailFragment();

    @Binds
    @IntoMap
    @ViewModelKey(SessionDetailViewModel.class)
    abstract ViewModel bindSessionDetailFragmentViewModel(SessionDetailViewModel viewModel);
}