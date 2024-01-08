package com.google.samples.apps.iosched.ui.agenda;

import androidx.lifecycle.ViewModel;
import com.google.samples.apps.iosched.shared.di.ChildFragmentScoped;
import com.google.samples.apps.iosched.shared.di.ViewModelKey;
import dagger.Binds;
import dagger.Module;
import dagger.android.AndroidInjector;
import dagger.android.ContributesAndroidInjector;
import dagger.multibindings.IntoMap;

@Module
public abstract class AgendaModule {

    @ChildFragmentScoped
    @ContributesAndroidInjector
    public abstract AgendaFragment contributeScheduleAgendaFragment();

    @Binds
    @IntoMap
    @ViewModelKey(AgendaViewModel.class)
    public abstract ViewModel bindAgendaViewModel(AgendaViewModel viewModel);
}