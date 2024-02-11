

package com.google.samples.apps.iosched.shared.di;

import com.google.samples.apps.iosched.shared.data.map.FakeMapMetadataDataSource;
import com.google.samples.apps.iosched.shared.data.map.MapMetadataDataSource;
import com.google.samples.apps.iosched.shared.data.session.FakeSessionDataSource;
import com.google.samples.apps.iosched.shared.data.session.SessionDataSource;
import com.google.samples.apps.iosched.shared.data.session.agenda.AgendaDataSource;
import com.google.samples.apps.iosched.shared.data.session.agenda.FakeAgendaDataSource;
import com.google.samples.apps.iosched.shared.data.tag.FakeTagDataSource;
import com.google.samples.apps.iosched.shared.data.tag.TagDataSource;
import javax.inject.Singleton;
import dagger.Module;
import dagger.Provides;

@Module
public class SharedModule {

    @Singleton
    @Provides
    public SessionDataSource provideSessionDataSource() {
        return new FakeSessionDataSource();;
    }

    @Singleton
    @Provides
    public AgendaDataSource provideAgendaDataSource() {
        return new FakeAgendaDataSource();;
    }

    @Singleton
    @Provides
    public TagDataSource provideTagDataSource() {
        return new FakeTagDataSource();;
    }

    @Singleton
    @Provides
    public MapMetadataDataSource provideMapMetadataDataSource() {
        return new FakeMapMetadataDataSource();;
    }
}