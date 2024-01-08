package com.google.samples.apps.iosched.shared.di;

import com.google.samples.apps.iosched.shared.data.BootstrapConferenceDataSource;
import com.google.samples.apps.iosched.shared.data.ConferenceDataRepository;
import com.google.samples.apps.iosched.shared.data.ConferenceDataSource;
import com.google.samples.apps.iosched.shared.data.OfflineConferenceDataSource;
import com.google.samples.apps.iosched.shared.data.map.FakeMapMetadataDataSource;
import com.google.samples.apps.iosched.shared.data.map.MapMetadataDataSource;
import com.google.samples.apps.iosched.shared.data.session.DefaultSessionRepository;
import com.google.samples.apps.iosched.shared.data.session.SessionRepository;
import com.google.samples.apps.iosched.shared.data.userevent.DefaultSessionAndUserEventRepository;
import com.google.samples.apps.iosched.shared.data.userevent.FakeUserEventDataSource;
import com.google.samples.apps.iosched.shared.data.userevent.SessionAndUserEventRepository;
import com.google.samples.apps.iosched.shared.data.userevent.UserEventDataSource;

import javax.inject.Named;
import javax.inject.Singleton;

@Module
public class SharedModule {

    @Singleton
    @Provides
    @Named("remoteConfDatasource")
    public ConferenceDataSource provideConferenceDataSource() {
        return new OfflineConferenceDataSource();
    }

    @Singleton
    @Provides
    @Named("bootstrapConfDataSource")
    public ConferenceDataSource provideBootstrapRemoteSessionDataSource() {
        return BootstrapConferenceDataSource.INSTANCE;
    }

    @Singleton
    @Provides
    public ConferenceDataRepository provideConferenceDataRepository(
            @Named("remoteConfDatasource") ConferenceDataSource remoteDataSource,
            @Named("bootstrapConfDataSource") ConferenceDataSource boostrapDataSource
    ) {
        return new ConferenceDataRepository(remoteDataSource, boostrapDataSource);
    }

    @Singleton
    @Provides
    public SessionRepository provideSessionRepository(
            ConferenceDataRepository conferenceDataRepository
    ) {
        return new DefaultSessionRepository(conferenceDataRepository);
    }

    @Singleton
    @Provides
    public MapMetadataDataSource provideMapMetadataDataSource() {
        return FakeMapMetadataDataSource.INSTANCE;
    }

    @Singleton
    @Provides
    public UserEventDataSource provideUserEventDataSource() {
        return FakeUserEventDataSource.INSTANCE;
    }

    @Singleton
    @Provides
    public SessionAndUserEventRepository provideSessionAndUserEventRepository(
            UserEventDataSource userEventDataSource,
            SessionRepository sessionRepository
    ) {
        return new DefaultSessionAndUserEventRepository(userEventDataSource, sessionRepository);
    }
}