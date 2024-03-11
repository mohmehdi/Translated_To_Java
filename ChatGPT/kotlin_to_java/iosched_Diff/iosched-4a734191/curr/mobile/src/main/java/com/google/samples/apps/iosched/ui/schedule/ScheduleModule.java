package com.google.samples.apps.iosched.ui.schedule;

import android.arch.lifecycle.ViewModel;
import com.google.samples.apps.iosched.di.ViewModelKey;
import com.google.samples.apps.iosched.ui.schedule.agenda.ScheduleAgendaFragment;
import dagger.Binds;
import dagger.Module;
import dagger.android.ContributesAndroidInjector;
import dagger.multibindings.IntoMap;

@Module
public abstract class ScheduleModule {

    @ContributesAndroidInjector
    public abstract ScheduleFragment contributeScheduleFragment();

    @ContributesAndroidInjector
    public abstract ScheduleDayFragment contributeScheduleDayFragment();

    @ContributesAndroidInjector
    public abstract ScheduleAgendaFragment contributeScheduleAgendaFragment();

    @ContributesAndroidInjector
    public abstract ScheduleFilterFragment contributeScheduleFilterFragment();

    @Binds
    @IntoMap
    @ViewModelKey(ScheduleViewModel.class)
    public abstract ViewModel bindScheduleFragmentViewModel(ScheduleViewModel viewModel);
}