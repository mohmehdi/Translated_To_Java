

package com.google.samples.apps.iosched.ui.agenda;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import com.google.samples.apps.iosched.shared.di.ChildFragmentScoped;
import com.google.samples.apps.iosched.shared.di.ViewModelKey;
import dagger.Binds;
import dagger.MapKey;
import dagger.Module;
import dagger.android.ContributesAndroidInjector;
import dagger.multibindings.IntoMap;
import javax.inject.Provider;

@Module
public abstract class AgendaModule {

    @ChildFragmentScoped
    @ContributesAndroidInjector
    public static AgendaFragment contributeScheduleAgendaFragment() {
        return AgendaFragment::new;
    }

    @Binds
    @IntoMap
    @ViewModelKey(AgendaViewModel.class)
    public abstract ViewModel bindAgendaViewModel(AgendaViewModel viewModel);

    @MapKey
    @interface ViewModelKey {
        Class<? extends ViewModel> value();
    }
}