package com.google.samples.apps.iosched.di;

import com.google.samples.apps.iosched.MainApplication;
import com.google.samples.apps.iosched.shared.di.SharedModule;
import com.google.samples.apps.iosched.ui.MainModule;
import com.google.samples.apps.iosched.ui.login.LoginViewModelPluginModule;
import com.google.samples.apps.iosched.ui.sessioncommon.ViewPoolModule;
import com.google.samples.apps.iosched.ui.sessiondetail.SessionDetailModule;
import com.google.samples.apps.iosched.util.login.LoginModule;
import dagger.Component;
import dagger.android.AndroidInjector;
import dagger.android.support.AndroidSupportInjectionModule;

import javax.inject.Singleton;

@Singleton
@Component(modules = {
        AndroidSupportInjectionModule.class,
        AppModule.class,
        ViewModelModule.class,
        SharedModule.class,
        MainModule.class,
        SessionDetailModule.class,
        LoginModule.class,
        LoginViewModelPluginModule.class,
        ViewPoolModule.class
})
public interface AppComponent extends AndroidInjector<MainApplication> {

    @Component.Builder
    abstract class Builder extends AndroidInjector.Builder<MainApplication> {
    }
}