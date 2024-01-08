package com.google.samples.apps.iosched.ui.sessioncommon;

import android.support.v7.widget.RecyclerView;
import com.google.samples.apps.iosched.shared.di.FragmentScoped;
import dagger.Module;
import dagger.Provides;
import javax.inject.Named;

@Module
public class SessionViewPoolModule {

    @FragmentScoped
    @Provides
    @Named("sessionViewPool")
    public RecyclerView.RecycledViewPool providesSessionViewPool() {
        return new RecyclerView.RecycledViewPool();
    }

    @FragmentScoped
    @Provides
    @Named("tagViewPool")
    public RecyclerView.RecycledViewPool providesTagViewPool() {
        return new RecyclerView.RecycledViewPool();
    }
}