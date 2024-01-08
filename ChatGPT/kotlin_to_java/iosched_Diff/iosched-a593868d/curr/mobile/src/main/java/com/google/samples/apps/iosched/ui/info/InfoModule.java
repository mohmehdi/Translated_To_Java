package com.google.samples.apps.iosched.ui.info;

import com.google.samples.apps.iosched.shared.di.FragmentScoped;
import dagger.Module;
import dagger.android.AndroidInjector;
import dagger.android.ContributesAndroidInjector;

@Module
public abstract class InfoModule {

    @FragmentScoped
    @ContributesAndroidInjector(modules = {InfoFragmentsModule.class})
    public abstract InfoFragment contributeInfoFragment();
}