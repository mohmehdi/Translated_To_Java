package com.google.samples.apps.iosched.ui.schedule;

import com.google.samples.apps.iosched.shared.di.ChildFragmentScoped;
import com.google.samples.apps.iosched.ui.schedule.day.ScheduleDayFragment;
import dagger.Module;
import dagger.android.AndroidInjector;
import dagger.android.ContributesAndroidInjector;

@Module
internal abstract class ScheduleChildFragmentsModule {

    @ChildFragmentScoped
    @ContributesAndroidInjector
    internal abstract AndroidInjector<ScheduleDayFragment> contributeScheduleDayFragment();
}