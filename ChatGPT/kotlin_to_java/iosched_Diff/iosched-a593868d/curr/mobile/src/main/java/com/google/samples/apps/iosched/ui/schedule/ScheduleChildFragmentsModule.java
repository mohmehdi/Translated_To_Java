package com.google.samples.apps.iosched.ui.schedule;

import com.google.samples.apps.iosched.shared.di.ChildFragmentScoped;
import com.google.samples.apps.iosched.ui.schedule.agenda.ScheduleAgendaFragment;
import com.google.samples.apps.iosched.ui.schedule.day.ScheduleDayFragment;
import dagger.Module;
import dagger.android.AndroidInjector;
import dagger.android.ContributesAndroidInjector;

@Module
public abstract class ScheduleChildFragmentsModule {

    @ChildFragmentScoped
    @ContributesAndroidInjector
    public abstract ScheduleDayFragment contributeScheduleDayFragment();

    @ChildFragmentScoped
    @ContributesAndroidInjector
    public abstract ScheduleAgendaFragment contributeScheduleAgendaFragment();
}