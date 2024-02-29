

package com.google.samples.apps.iosched.ui.login;

import com.google.samples.apps.iosched.shared.data.login.AuthenticatedUser;
import dagger.Module;
import dagger.Provides;

@Module
public class LoginViewModelPluginModule {

    @Provides
    public LoginViewModelPlugin provideLoginViewModelPlugin(AuthenticatedUser dataSource) {
        return new DefaultLoginViewModelPlugin(dataSource);
    }
}