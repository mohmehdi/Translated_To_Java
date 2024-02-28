

package com.google.samples.apps.iosched.di;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.samples.apps.iosched.shared.data.signin.datasources.AuthStateUserDataSource;
import com.google.samples.apps.iosched.shared.data.signin.datasources.FirebaseAuthStateUserDataSource;
import com.google.samples.apps.iosched.shared.data.signin.datasources.FirestoreRegisteredUserDataSource;
import com.google.samples.apps.iosched.shared.data.signin.datasources.RegisteredUserDataSource;
import com.google.samples.apps.iosched.shared.fcm.FcmTokenUpdater;
import com.google.samples.apps.iosched.util.signin.FirebaseAuthSignInHandler;
import com.google.samples.apps.iosched.util.signin.SignInHandler;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class SignInModule {

    @Provides
    public SignInHandler provideSignInHandler() {
        return new FirebaseAuthSignInHandler();
    }

    @Singleton
    @Provides
    public RegisteredUserDataSource provideRegisteredUserDataSource(FirebaseFirestore firestore) {
        return new FirestoreRegisteredUserDataSource(firestore);
    }

    @Singleton
    @Provides
    public AuthStateUserDataSource provideAuthStateUserDataSource(
            FirebaseAuth firebase, FirebaseFirestore firestore) {
        return new FirebaseAuthStateUserDataSource(
                firebase,
                new FcmTokenUpdater(firestore)
        );
    }

    @Singleton
    @Provides
    public FirebaseAuth provideFirebaseAuth() {
        return FirebaseAuth.getInstance();
    }
}