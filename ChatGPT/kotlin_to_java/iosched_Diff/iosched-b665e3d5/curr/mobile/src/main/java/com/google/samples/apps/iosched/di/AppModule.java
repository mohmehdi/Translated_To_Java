package com.google.samples.apps.iosched.di;

import android.content.ClipboardManager;
import android.content.Context;
import android.net.wifi.WifiManager;

import com.google.samples.apps.iosched.MainApplication;
import com.google.samples.apps.iosched.shared.analytics.AnalyticsHelper;
import com.google.samples.apps.iosched.shared.data.prefs.PreferenceStorage;
import com.google.samples.apps.iosched.shared.data.prefs.SharedPreferenceStorage;
import com.google.samples.apps.iosched.shared.data.agenda.AgendaRepository;
import com.google.samples.apps.iosched.shared.data.agenda.DefaultAgendaRepository;
import com.google.samples.apps.iosched.ui.signin.SignInViewModelDelegate;
import com.google.samples.apps.iosched.util.FirebaseAnalyticsHelper;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class AppModule {

    @Provides
    public Context provideContext(MainApplication application) {
        return application.getApplicationContext();
    }

    @Singleton
    @Provides
    public PreferenceStorage providesPreferenceStorage(Context context) {
        return new SharedPreferenceStorage(context);
    }

    @Provides
    public WifiManager providesWifiManager(Context context) {
        return (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    @Provides
    public ClipboardManager providesClipboardManager(Context context) {
        return (ClipboardManager) context.getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Singleton
    @Provides
    public AnalyticsHelper providesAnalyticsHelper(Context context, SignInViewModelDelegate signInDelegate, PreferenceStorage preferenceStorage) {
        return new FirebaseAnalyticsHelper(context, signInDelegate, preferenceStorage);
    }

    @Singleton
    @Provides
    public AgendaRepository provideAgendaRepository() {
        return new DefaultAgendaRepository();
    }
}