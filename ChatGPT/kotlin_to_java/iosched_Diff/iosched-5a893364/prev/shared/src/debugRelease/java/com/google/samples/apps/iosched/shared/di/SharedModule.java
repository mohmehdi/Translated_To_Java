package com.google.samples.apps.iosched.shared.di;

import android.content.Context;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.samples.apps.iosched.shared.data.BootstrapConferenceDataSource;
import com.google.samples.apps.iosched.shared.data.ConferenceDataRepository;
import com.google.samples.apps.iosched.shared.data.ConferenceDataSource;
import com.google.samples.apps.iosched.shared.data.NetworkConferenceDataSource;
import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUser;
import com.google.samples.apps.iosched.shared.data.login.FirebaseAuthenticatedUser;
import com.google.samples.apps.iosched.shared.data.login.LoginDataSource;
import com.google.samples.apps.iosched.shared.data.login.LoginRemoteDataSource;
import com.google.samples.apps.iosched.shared.data.map.MapMetadataDataSource;
import com.google.samples.apps.iosched.shared.data.map.RemoteMapMetadataDataSource;
import com.google.samples.apps.iosched.shared.data.session.DefaultSessionRepository;
import com.google.samples.apps.iosched.shared.data.session.SessionRepository;
import com.google.samples.apps.iosched.shared.data.userevent.DefaultSessionAndUserEventRepository;
import com.google.samples.apps.iosched.shared.data.userevent.FirestoreUserEventDataSource;
import com.google.samples.apps.iosched.shared.data.userevent.SessionAndUserEventRepository;
import com.google.samples.apps.iosched.shared.data.userevent.UserEventDataSource;
import dagger.Module;
import dagger.Provides;
import javax.inject.Named;
import javax.inject.Singleton;

@Module
public class SharedModule {

    @Singleton
    @Provides
    @Named("remoteConfDatasource")
    public ConferenceDataSource provideConferenceDataSource(Context context) {
        return new NetworkConferenceDataSource(context);
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
        return new RemoteMapMetadataDataSource();
    }

    @Singleton
    @Provides
    public UserEventDataSource provideUserEventDataSource(FirebaseFirestore firestore) {
        return new FirestoreUserEventDataSource(firestore);
    }

    @Singleton
    @Provides
    public SessionAndUserEventRepository provideSessionAndUserEventRepository(
            UserEventDataSource userEventDataSource,
            SessionRepository sessionRepository
    ) {
        return new DefaultSessionAndUserEventRepository(userEventDataSource, sessionRepository);
    }

    @Singleton
    @Provides
    public LoginDataSource provideLoginDataSource() {
        return new LoginRemoteDataSource();
    }

    @Singleton
    @Provides
    public AuthenticatedUser provideAuthenticatedUser() {
        return new FirebaseAuthenticatedUser(FirebaseAuth.getInstance());
    }

    @Singleton
    @Provides
    public FirebaseFirestore provideFirebaseFireStore() {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        firestore.setFirestoreSettings(new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build());
        return firestore;
    }
}