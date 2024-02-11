

package com.google.samples.apps.iosched.shared.di;

import com.google.samples.apps.iosched.shared.data.map.FakeMapMetadataDataSource;
import com.google.samples.apps.iosched.shared.data.map.MapMetadataDataSource;
import com.google.samples.apps.iosched.shared.data.session.FakeSessionDataSource;
import com.google.samples.apps.iosched.shared.data.session.SessionDataSource;
import com.google.samples.apps.iosched.shared.data.tag.FakeTagDataSource;
import com.google.samples.apps.iosched.shared.data.tag.TagDataSource;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

@Module
public class SharedModule {

    @Singleton
    @Provides
    public SessionDataSource provideSessionDataSource() {
        return new FakeSessionDataSource();
    }

    @Singleton
    @Provides
    public TagDataSource provideTagDataSource() {
        return new FakeTagDataSource();
    }

    @Singleton
    @Provides
    public MapMetadataDataSource provideMapMetadataDataSource() {
        return new FakeMapMetadataDataSource();
    }
}