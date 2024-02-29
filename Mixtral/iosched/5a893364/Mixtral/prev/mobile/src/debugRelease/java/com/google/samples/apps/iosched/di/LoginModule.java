

package com.google.samples.apps.iosched.di;

import com.google.samples.apps.iosched.util.login.DefaultLoginHandler;
import com.google.samples.apps.iosched.util.login.LoginHandler;
import dagger.Module;
import dagger.Provides;

@Module
public class LoginModule {
    @Provides
    public LoginHandler provideLoginHandler() {
        return new DefaultLoginHandler();
    }
}