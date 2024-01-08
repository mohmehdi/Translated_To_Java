package com.google.samples.apps.iosched.ui;

import com.google.samples.apps.iosched.ui.info.InfoModule;
import com.google.samples.apps.iosched.ui.map.MapModule;
import com.google.samples.apps.iosched.ui.schedule.ScheduleModule;
import dagger.Module;
import dagger.android.ContributesAndroidInjector;

@Module
public abstract class MainModule {

    @ContributesAndroidInjector(modules = {ScheduleModule.class, MapModule.class, InfoModule.class})
    public abstract MainActivity mainActivity();
}