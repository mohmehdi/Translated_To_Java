package com.google.samples.apps.iosched.ui.schedule;

import android.arch.lifecycle.ViewModel;
import com.google.samples.apps.iosched.di.ViewModelKey;
import com.google.samples.apps.iosched.ui.MainModule;
import com.google.samples.apps.iosched.ui.schedule.agenda.ScheduleAgendaFragment;
import com.google.samples.apps.iosched.ui.schedule.day.ScheduleDayFragment;
import com.google.samples.apps.iosched.ui.schedule.filters.ScheduleFilterFragment;
import dagger.Binds;
import dagger.Module;
import dagger.android.AndroidInjector;
import dagger.android.ContributesAndroidInjector;
import dagger.multibindings.IntoMap;

@Module
abstract class ScheduleModule {

    @ContributesAndroidInjector
    abstract ScheduleFragment contributeScheduleFragment();

    @ContributesAndroidInjector
    abstract ScheduleDayFragment contributeScheduleDayFragment();

    @ContributesAndroidInjector
    abstract ScheduleAgendaFragment contributeScheduleAgendaFragment();

    @ContributesAndroidInjector
    abstract ScheduleFilterFragment contributeScheduleFilterFragment();

    @Binds
    @IntoMap
    @ViewModelKey(ScheduleViewModel.class)
    abstract ViewModel bindScheduleFragmentViewModel(ScheduleViewModel viewModel);
}