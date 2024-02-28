

package com.google.samples.apps.iosched.di;

import com.google.samples.apps.iosched.shared.di.ActivityScoped;
import com.google.samples.apps.iosched.ui.LaunchModule;
import com.google.samples.apps.iosched.ui.LauncherActivity;
import com.google.samples.apps.iosched.ui.MainActivity;
import com.google.samples.apps.iosched.ui.MainActivityModule;
import com.google.samples.apps.iosched.ui.MapActivity;
import com.google.samples.apps.iosched.ui.MapModule;
import com.google.samples.apps.iosched.ui.OnboardingActivity;
import com.google.samples.apps.iosched.ui.OnboardingModule;
import com.google.samples.apps.iosched.ui.SessionDetailActivity;
import com.google.samples.apps.iosched.ui.SessionDetailModule;
import com.google.samples.apps.iosched.ui.SettingsModule;
import com.google.samples.apps.iosched.ui.SignInDialogModule;
import com.google.samples.apps.iosched.ui.SpeakerActivity;
import com.google.samples.apps.iosched.ui.SpeakerModule;
import com.google.samples.apps.iosched.ui.info.InfoModule;
import com.google.samples.apps.iosched.ui.map.MapActivity;
import com.google.samples.apps.iosched.ui.onboarding.OnboardingModule;
import com.google.samples.apps.iosched.ui.prefs.PreferenceModule;
import com.google.samples.apps.iosched.ui.reservation.ReservationModule;
import com.google.samples.apps.iosched.ui.schedule.ScheduleModule;
import com.google.samples.apps.iosched.ui.sessioncommon.EventActionsViewModelDelegateModule;
import dagger.Module;
import dagger.android.ContributesAndroidInjector;

@Module
public abstract class ActivityBindingModule {

    @ActivityScoped
    @ContributesAndroidInjector(modules = LaunchModule.class)
    public abstract LauncherActivity launcherActivity();

    @ActivityScoped
    @ContributesAndroidInjector(modules = OnboardingModule.class)
    public abstract OnboardingActivity onboardingActivity();

    @ActivityScoped
    @ContributesAndroidInjector(
            modules = {
                    MainActivityModule.class,
                    ScheduleModule.class,
                    MapModule.class,
                    InfoModule.class,
                    SettingsModule.class,
                    SignInDialogModule.class,
                    ReservationModule.class,
                    PreferenceModule.class
            }
    )
    public abstract MainActivity mainActivity();

    @ActivityScoped
    @ContributesAndroidInjector(
            modules = {
                    SessionDetailModule.class,
                    SignInDialogModule.class,
                    ReservationModule.class,
                    PreferenceModule.class
            }
    )
    public abstract SessionDetailActivity sessionDetailActivity();

    @ActivityScoped
    @ContributesAndroidInjector(
            modules = {
                    SpeakerModule.class,
                    SignInDialogModule.class,
                    EventActionsViewModelDelegateModule.class,
                    PreferenceModule.class
            }
    )
    public abstract SpeakerActivity speakerActivity();

    @ActivityScoped
    @ContributesAndroidInjector(
            modules = {
                    MapModule.class,
                    PreferenceModule.class
            }
    )
    public abstract MapActivity mapActivity();
}