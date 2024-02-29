

package com.google.samples.apps.iosched.ui.login;

import com.google.samples.apps.iosched.shared.domain.auth.ObserveUserAuthStateUseCase;
import dagger.Module;
import dagger.Provides;

@Module
public class LoginViewModelPluginModule {

    @Provides
    public LoginViewModelPlugin provideLoginViewModelPlugin(ObserveUserAuthStateUseCase dataSource) {
        return new FirebaseLoginViewModelPlugin(dataSource);
    }
}