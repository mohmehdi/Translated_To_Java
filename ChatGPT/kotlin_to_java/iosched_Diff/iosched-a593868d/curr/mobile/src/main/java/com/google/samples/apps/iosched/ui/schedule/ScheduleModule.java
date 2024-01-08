package com.google.samples.apps.iosched.ui.schedule;

import android.arch.lifecycle.ViewModel;
import com.google.samples.apps.iosched.shared.di.FragmentScoped;
import com.google.samples.apps.iosched.di.ViewModelKey;
import com.google.samples.apps.iosched.ui.schedule.filters.ScheduleFilterFragment;
import com.google.samples.apps.iosched.ui.sessioncommon.SessionViewPoolModule;
import dagger.Binds;
import dagger.Module;
import dagger.android.ContributesAndroidInjector;
import dagger.multibindings.IntoMap;

@Module
public abstract class ScheduleModule {

    @FragmentScoped
    @ContributesAndroidInjector(modules = {ScheduleChildFragmentsModule.class,
        SessionViewPoolModule.class})
    abstract ScheduleFragment contributeScheduleFragment();

    @FragmentScoped
    @ContributesAndroidInjector
    abstract ScheduleFilterFragment contributeScheduleFilterFragment();

    @Binds
    @IntoMap
    @ViewModelKey(ScheduleViewModel.class)
    abstract ViewModel bindScheduleFragmentViewModel(ScheduleViewModel viewModel);
}