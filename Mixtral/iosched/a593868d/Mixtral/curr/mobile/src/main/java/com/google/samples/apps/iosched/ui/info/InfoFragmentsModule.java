package com.google.samples.apps.iosched.ui.info;

import dagger.Module;
import dagger.android.ContributesAndroidInjector;

@Module
public abstract class InfoFragmentsModule {

  @ChildFragmentScoped
  @ContributesAndroidInjector
  public abstract EventFragment contributeEventFragment();

  @ChildFragmentScoped
  @ContributesAndroidInjector
  public abstract TravelFragment contributeTravelFragment();

  @ChildFragmentScoped
  @ContributesAndroidInjector
  public abstract FaqFragment contributeFaqFragment();

  @ChildFragmentScoped
  @ContributesAndroidInjector
  public abstract SettingsFragment contributeSettingsFragment();
}
