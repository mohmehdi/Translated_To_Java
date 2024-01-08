package com.google.samples.apps.iosched.ui.info;

import com.google.samples.apps.iosched.ui.MainModule;
import dagger.Module;
import dagger.android.AndroidInjector;
import dagger.android.ContributesAndroidInjector;

@Module
public abstract class InfoModule {

    @ContributesAndroidInjector
    public abstract InfoFragment contributeInfoFragment();

    @ContributesAndroidInjector
    public abstract EventFragment contributeEventFragment();

    @ContributesAndroidInjector
    public abstract TravelFragment contributeTravelFragment();

    @ContributesAndroidInjector
    public abstract FaqFragment contributeFaqFragment();

    @ContributesAndroidInjector
    public abstract SettingsFragment contributeSettingsFragment();
}