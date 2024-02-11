

package com.google.samples.apps.iosched.shared.di;

import com.google.samples.apps.iosched.shared.data.map.MapMetadataDataSource;
import com.google.samples.apps.iosched.shared.data.map.RemoteMapMetadataDataSource;
import com.google.samples.apps.iosched.shared.data.session.RemoteSessionDataSource;
import com.google.samples.apps.iosched.shared.data.session.SessionDataSource;
import com.google.samples.apps.iosched.shared.data.tag.RemoteTagDataSource;
import com.google.samples.apps.iosched.shared.data.tag.TagDataSource;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

@Module
public class SharedModule {

    @Singleton
    @Provides
    public SessionDataSource provideSessionDataSource() {
        return new RemoteSessionDataSource();
    }

    @Singleton
    @Provides
    public TagDataSource provideTagDataSource() {
        return new RemoteTagDataSource();
    }

    @Singleton
    @Provides
    public MapMetadataDataSource provideMapMetadataDataSource() {
        return new RemoteMapMetadataDataSource();
    }
}