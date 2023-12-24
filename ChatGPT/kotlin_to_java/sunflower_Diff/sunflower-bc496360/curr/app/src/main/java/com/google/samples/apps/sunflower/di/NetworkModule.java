
package com.google.samples.apps.sunflower.di;

import com.google.samples.apps.sunflower.api.UnsplashService;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

import javax.inject.Singleton;

@InstallIn(SingletonComponent.class)
@Module
public class NetworkModule {

    @Singleton
    @Provides
    public UnsplashService provideUnsplashService() {
        return UnsplashService.create();
    }
}
