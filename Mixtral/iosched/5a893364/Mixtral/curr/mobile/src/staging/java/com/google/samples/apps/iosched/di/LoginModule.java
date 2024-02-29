

package com.google.samples.apps.iosched.di;

import android.content.Context;

import com.google.samples.apps.iosched.shared.data.login.StagingAuthStateUserDataSource;
import com.google.samples.apps.iosched.shared.data.login.StagingAuthenticatedUser;
import com.google.samples.apps.iosched.shared.data.login.StagingLoginHandler;
import com.google.samples.apps.iosched.shared.data.login.StagingRegisteredUserDataSource;
import com.google.samples.apps.iosched.shared.data.login.AuthStateUserDataSource;
import com.google.samples.apps.iosched.shared.data.login.RegisteredUserDataSource;
import com.google.samples.apps.iosched.util.login.LoginHandler;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

@Module
public class LoginModule {

    @Provides
    public LoginHandler provideLoginHandler(Context context) {
        return new StagingLoginHandler(new StagingAuthenticatedUser(context));
    }

    @Singleton
    @Provides
    public RegisteredUserDataSource provideRegisteredUserDataSource(Context context) {
        return new StagingRegisteredUserDataSource(true);
    }

    @Singleton
    @Provides
    public AuthStateUserDataSource provideAuthStateUserDataSource(Context context) {
        return new StagingAuthStateUserDataSource(
                true,
                true,
                context,
                "StagingTest");
    }
}