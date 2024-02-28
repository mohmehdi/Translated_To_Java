package com.google.samples.apps.iosched.ui.info;

import android.app.Activity;
import dagger.Module;
import dagger.android.AndroidInjector;
import dagger.android.ContributesAndroidInjector;
import dagger.bindings.Internal;

@Module
abstract class InfoModule {
    
    @ContributesAndroidInjector
    abstract InfoFragment contributeInfoFragment();

    
    @ContributesAndroidInjector
    abstract EventFragment contributeEventFragment();

    
    @ContributesAndroidInjector
    abstract TravelFragment contributeTravelFragment();

    
    @ContributesAndroidInjector
    abstract FaqFragment contributeFaqFragment();

    
    @ContributesAndroidInjector
    abstract SettingsFragment contributeSettingsFragment();
}