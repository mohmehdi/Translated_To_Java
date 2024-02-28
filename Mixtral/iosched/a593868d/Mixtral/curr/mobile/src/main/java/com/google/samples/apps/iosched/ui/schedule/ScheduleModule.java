package com.google.samples.apps.iosched.ui.schedule;

import android.arch.lifecycle.ViewModel;
import com.google.samples.apps.iosched.shared.di.FragmentScoped;
import com.google.samples.apps.iosched.di.ViewModelKey;
import com.google.samples.apps.iosched.ui.schedule.filters.ScheduleFilterFragment;
import com.google.samples.apps.iosched.ui.sessioncommon.SessionViewPoolModule;
import dagger.Binds;
import dagger.MapKey;
import dagger.Module;
import dagger.android.ContributesAndroidInjector;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.multibindings.IntoMap;

import static dagger.android.AndroidInjection.inject;

@Module
public abstract class ScheduleModule {

    @FragmentScoped
    @ContributesAndroidInjector(modules = {ScheduleChildFragmentsModule.class,
            SessionViewPoolModule.class})
    public abstract ScheduleFragment contributeScheduleFragment();

    @FragmentScoped
    @ContributesAndroidInjector
    public abstract ScheduleFilterFragment contributeScheduleFilterFragment();

    @Binds
    @IntoMap
    @ViewModelKey(ScheduleViewModel.class)
    public abstract ViewModel bindScheduleFragmentViewModel(ScheduleViewModel viewModel);
}