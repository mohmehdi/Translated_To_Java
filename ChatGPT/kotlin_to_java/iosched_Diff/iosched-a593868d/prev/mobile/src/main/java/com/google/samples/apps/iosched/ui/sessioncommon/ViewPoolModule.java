package com.google.samples.apps.iosched.ui.sessioncommon;

import android.support.v7.widget.RecyclerView.RecycledViewPool;
import dagger.Module;
import dagger.Provides;
import javax.inject.Named;

@Module
public class ViewPoolModule {

    @Provides
    @Named("sessionViewPool")
    public RecycledViewPool providesSessionViewPool() {
        return new RecycledViewPool();
    }

    @Provides
    @Named("tagViewPool")
    public RecycledViewPool providesTagViewPool() {
        return new RecycledViewPool();
    }
}