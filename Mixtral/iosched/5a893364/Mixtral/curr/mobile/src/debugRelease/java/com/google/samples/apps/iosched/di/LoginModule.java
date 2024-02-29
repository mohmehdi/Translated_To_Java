

package com.google.samples.apps.iosched.di;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.samples.apps.iosched.shared.data.login.datasources.AuthStateUserDataSource;
import com.google.samples.apps.iosched.shared.data.login.datasources.FirebaseAuthStateUserDataSource;
import com.google.samples.apps.iosched.shared.data.login.datasources.FirestoreRegisteredUserDataSource;
import com.google.samples.apps.iosched.shared.data.login.datasources.RegisteredUserDataSource;
import com.google.samples.apps.iosched.util.login.DefaultLoginHandler;
import com.google.samples.apps.iosched.util.login.LoginHandler;
import javax.inject.Singleton;
import dagger.Module;
import dagger.Provides;

@Module
public class LoginModule {

    @Provides
    public LoginHandler provideLoginHandler() {
        return new DefaultLoginHandler();
    }

    @Singleton
    @Provides
    public RegisteredUserDataSource provideRegisteredUserDataSource(FirebaseFirestore firestore) {
        return new FirestoreRegisteredUserDataSource(firestore);
    }

    @Singleton
    @Provides
    public AuthStateUserDataSource provideAuthStateUserDataSource(FirebaseAuth firebase) {
        return new FirebaseAuthStateUserDataSource(firebase);
    }

    @Singleton
    @Provides
    public FirebaseAuth provideFirebaseAuth() {
        return FirebaseAuth.getInstance();
    }
}