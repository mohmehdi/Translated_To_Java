package com.google.samples.apps.iosched.ui.sessioncommon;

import android.support.v7.widget.RecyclerView;
import dagger.Module;
import dagger.Provides;
import javax.inject.Named;

@Module
public class ViewPoolModule {

    @Provides
    @Named("sessionViewPool")
    public RecyclerView.RecycledViewPool providesSessionViewPool() {
        return new RecyclerView.RecycledViewPool();
    }

    @Provides
    @Named("tagViewPool")
    public RecyclerView.RecycledViewPool providesTagViewPool() {
        return new RecyclerView.RecycledViewPool();
    }
}