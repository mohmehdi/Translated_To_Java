package com.google.samples.apps.iosched.di;

import com.google.samples.apps.iosched.shared.di.ActivityScoped;
import com.google.samples.apps.iosched.ui.MainActivity;
import com.google.samples.apps.iosched.ui.info.InfoModule;
import com.google.samples.apps.iosched.ui.map.MapModule;
import com.google.samples.apps.iosched.ui.schedule.ScheduleModule;
import com.google.samples.apps.iosched.ui.sessiondetail.SessionDetailActivity;
import com.google.samples.apps.iosched.ui.sessiondetail.SessionDetailModule;
import dagger.Module;
import dagger.android.ContributesAndroidInjector;

@Module
public abstract class ActivityBindingModule {

    @ActivityScoped
    @ContributesAndroidInjector(modules = {ScheduleModule.class, MapModule.class, InfoModule.class})
    public abstract MainActivity mainActivity();

    @ActivityScoped
    @ContributesAndroidInjector(modules = {SessionDetailModule.class})
    public abstract SessionDetailActivity sessionDetailActivity();

}