package com.google.samples.apps.iosched.di;

import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUser;
import com.google.samples.apps.iosched.shared.data.login.StagingAuthenticatedUser;
import com.google.samples.apps.iosched.shared.data.login.StagingLoginHandler;
import com.google.samples.apps.iosched.util.login.LoginHandler;
import dagger.Module;
import dagger.Provides;

@Module
public class LoginModule {
    @Provides
    public LoginHandler provideLoginHandler(AuthenticatedUser user) {
        return new StagingLoginHandler((StagingAuthenticatedUser) user);
    }
}